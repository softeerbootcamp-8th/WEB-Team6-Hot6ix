#!/usr/bin/env bash
# Finder 에서 더블클릭하면 개발에 필요한 걸 한꺼번에 띄운다.
#
#   1. 개발용 MySQL (도커)
#   2. 그래프 (Prometheus + Grafana)
#   3. 부하 테스트 콘솔 서버
#   4. 프론트 (pnpm 이 있으면)
#   5. 백엔드 앱
#
# 터미널에 명령을 칠 필요가 없게 하려고 둔 파일이다. 창은 뜨지만 아무것도 안 쳐도 된다.
# 이 창을 닫거나 Ctrl+C 를 누르면 위에서 띄운 것들이 같이 정리된다.
#
# 처음 한 번은 Finder 에서 우클릭 > 열기 로 실행해야 할 수 있다 (macOS Gatekeeper).

cd "$(dirname "$0")" || exit 1

BACKEND_DIR="$(pwd)"
FRONTEND_DIR="$(cd .. && pwd)/frontend"

APP_PORT="${SERVER_PORT:-18000}"
GRAFANA_PORT="${PERF_GRAFANA_PORT:-13000}"

CONSOLE_PID=""
FRONT_PID=""
APP_PID=""

cleanup() {
  echo
  echo "정리하는 중..."
  [ -n "$CONSOLE_PID" ] && kill "$CONSOLE_PID" 2>/dev/null
  [ -n "$FRONT_PID" ] && kill "$FRONT_PID" 2>/dev/null
  [ -n "$APP_PID" ] && kill "$APP_PID" 2>/dev/null
  echo "끝났습니다."
  echo "(개발용 MySQL 과 그래프는 계속 떠 있습니다. 내리려면 Docker Desktop 에서 멈추세요.)"
}
trap cleanup EXIT INT TERM

echo "════════════════════════════════════════════════════════"
echo "  UpBid 개발환경"
echo "════════════════════════════════════════════════════════"
echo

# ── 준비물 확인 ────────────────────────────────────────────────────
missing=""
command -v docker  >/dev/null 2>&1 || missing="$missing Docker"
command -v java    >/dev/null 2>&1 || missing="$missing Java"
command -v python3 >/dev/null 2>&1 || missing="$missing Python3"

if [ -n "$missing" ]; then
  echo "  ✗ 설치가 안 된 게 있습니다:$missing"
  echo
  echo "    Docker   https://www.docker.com/products/docker-desktop"
  echo "    Java 21  brew install --cask temurin@21"
  echo "    Python3  xcode-select --install"
  echo
  read -r -p "  엔터를 누르면 창이 닫힙니다. " _
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "  ✗ Docker Desktop 이 안 켜져 있습니다. 실행한 뒤 이 파일을 다시 더블클릭하세요."
  echo
  read -r -p "  엔터를 누르면 창이 닫힙니다. " _
  exit 1
fi

# ── 1. 개발용 MySQL ────────────────────────────────────────────────
echo "  [1/5] 개발용 MySQL"
docker compose up -d >/dev/null 2>&1
for _ in $(seq 1 30); do
  docker compose exec -T mysql mysqladmin ping -h localhost -uroot -p1234 >/dev/null 2>&1 && break
  sleep 2
done
echo "        준비됨"

# ── 2. 그래프 ──────────────────────────────────────────────────────
# Prometheus 와 Grafana 만 미리 띄운다. 측정용 앱과 MySQL, nginx 는 run.sh 가 띄운다.
#
# 왜 여기서 띄우나: 예전에는 측정을 한 번 돌려야만 Grafana 가 떠서, 개발환경만 켜고
# 13000 을 연 사람은 연결 거부를 보고 "고장났나" 했다. 둘은 가볍고, run.sh 의 포트
# 검사도 upbid-perf 프로젝트 컨테이너는 예외로 두기 때문에 미리 떠 있어도 안 부딪힌다.
#
# 측정 전에는 그래프가 비어 있는 게 정상이다. 그릴 숫자가 아직 없기 때문이다.
echo "  [2/5] 그래프 (Prometheus + Grafana)"
if docker compose -f perf/docker-compose.perf.yml up -d prometheus grafana >/dev/null 2>&1; then
  echo "        http://localhost:$GRAFANA_PORT  (측정을 돌리기 전에는 비어 있습니다)"
else
  echo "        건너뜁니다 (안 떴습니다. 측정을 돌리면 그때 다시 띄웁니다)"
fi

# ── 3. 부하 테스트 콘솔 서버 ────────────────────────────────────────
echo "  [3/5] 부하 테스트 콘솔 서버"
python3 perf/console-server.py >/tmp/upbid-console-server.log 2>&1 &
CONSOLE_PID=$!
sleep 1
echo "        http://localhost:18099 (로그: /tmp/upbid-console-server.log)"

# ── 4. 프론트 ──────────────────────────────────────────────────────
echo "  [4/5] 프론트"
if command -v pnpm >/dev/null 2>&1 && [ -d "$FRONTEND_DIR/node_modules" ]; then
  (cd "$FRONTEND_DIR" && pnpm dev >/tmp/upbid-frontend.log 2>&1) &
  FRONT_PID=$!
  echo "        http://localhost:15173 (로그: /tmp/upbid-frontend.log)"
else
  echo "        건너뜁니다 (pnpm 이 없거나 pnpm install 을 아직 안 했습니다)"
  echo "        경매방 화면을 보려면 frontend/ 에서 pnpm install 을 한 번 하세요"
fi

# ── 5. 백엔드 ──────────────────────────────────────────────────────
# exec 로 넘기지 않는다. 그러면 이 스크립트가 bootRun 으로 바뀌어서 "다 떴습니다" 를
# 못 찍는다. gradle 은 앱이 도는 내내 `80% EXECUTING` 에 머무는데, 그게 마지막 줄로
# 남으면 멈춘 것처럼 보인다(실제로 팀원이 그렇게 읽었다). 그래서 배경으로 돌리고,
# 앱이 응답할 때까지 기다렸다가 주소를 찍는다.
#
# --console=plain 은 그 가짜 진행률 막대를 아예 없앤다. 로그는 그대로 보인다.
echo "  [5/5] 백엔드 앱 — 뜨는 데 20초쯤 걸립니다"
echo
./gradlew bootRun --console=plain &
APP_PID=$!

READY=""
for _ in $(seq 1 90); do
  if curl -fsS -o /dev/null --max-time 2 "http://localhost:$APP_PORT/actuator/health" 2>/dev/null; then
    READY="yes"
    break
  fi
  # 앱이 뜨다가 죽었으면 더 기다릴 이유가 없다. 위 로그에 원인이 찍혀 있다.
  kill -0 "$APP_PID" 2>/dev/null || break
  sleep 1
done

echo
if [ -n "$READY" ]; then
  echo "════════════════════════════════════════════════════════"
  echo "  준비 완료. 아래를 브라우저에서 여세요."
  echo
  echo "     개발 콘솔   http://localhost:$APP_PORT/dev-console.html"
  echo "     그래프      http://localhost:$GRAFANA_PORT"
  echo
  echo "  이 창은 켜 둡니다. 닫으면 앱이 멈춥니다."
  echo "  아래로 계속 흐르는 건 앱 로그입니다. 진행률 막대가 멈춰 보여도 정상입니다."
  echo "════════════════════════════════════════════════════════"
else
  echo "════════════════════════════════════════════════════════"
  echo "  ✗ 백엔드가 안 떴습니다. 위 로그에 원인이 찍혀 있습니다."
  echo "    자주 걸리는 것: $APP_PORT 를 다른 프로그램이 쓰고 있거나,"
  echo "    backend/.env 의 SERVER_PORT 가 다른 값으로 적혀 있는 경우입니다."
  echo "════════════════════════════════════════════════════════"
fi
echo

wait "$APP_PID"
