#!/usr/bin/env bash
# Finder 에서 더블클릭하면 개발에 필요한 걸 한꺼번에 띄운다.
#
#   1. 개발용 MySQL (도커)
#   2. 부하 테스트 콘솔 서버
#   3. 프론트 (pnpm 이 있으면)
#   4. 백엔드 앱
#
# 터미널에 명령을 칠 필요가 없게 하려고 둔 파일이다. 창은 뜨지만 아무것도 안 쳐도 된다.
# 이 창을 닫거나 Ctrl+C 를 누르면 위에서 띄운 것들이 같이 정리된다.
#
# 처음 한 번은 Finder 에서 우클릭 > 열기 로 실행해야 할 수 있다 (macOS Gatekeeper).

cd "$(dirname "$0")" || exit 1

BACKEND_DIR="$(pwd)"
FRONTEND_DIR="$(cd .. && pwd)/frontend"

CONSOLE_PID=""
FRONT_PID=""

cleanup() {
  echo
  echo "정리하는 중..."
  [ -n "$CONSOLE_PID" ] && kill "$CONSOLE_PID" 2>/dev/null
  [ -n "$FRONT_PID" ] && kill "$FRONT_PID" 2>/dev/null
  echo "끝났습니다. (개발용 MySQL 은 계속 떠 있습니다. 내리려면 docker compose stop)"
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
echo "  [1/4] 개발용 MySQL"
docker compose up -d >/dev/null 2>&1
for _ in $(seq 1 30); do
  docker compose exec -T mysql mysqladmin ping -h localhost -uroot -p1234 >/dev/null 2>&1 && break
  sleep 2
done
echo "        준비됨"

# ── 2. 부하 테스트 콘솔 서버 ────────────────────────────────────────
echo "  [2/4] 부하 테스트 콘솔 서버"
python3 perf/console-server.py >/tmp/upbid-console-server.log 2>&1 &
CONSOLE_PID=$!
sleep 1
echo "        http://localhost:18099 (로그: /tmp/upbid-console-server.log)"

# ── 3. 프론트 ──────────────────────────────────────────────────────
echo "  [3/4] 프론트"
if command -v pnpm >/dev/null 2>&1 && [ -d "$FRONTEND_DIR/node_modules" ]; then
  (cd "$FRONTEND_DIR" && pnpm dev >/tmp/upbid-frontend.log 2>&1) &
  FRONT_PID=$!
  echo "        http://localhost:15173 (로그: /tmp/upbid-frontend.log)"
else
  echo "        건너뜁니다 (pnpm 이 없거나 pnpm install 을 아직 안 했습니다)"
  echo "        경매방 화면을 보려면 frontend/ 에서 pnpm install 을 한 번 하세요"
fi

# ── 4. 백엔드 ──────────────────────────────────────────────────────
echo "  [4/4] 백엔드 앱 — 뜨는 데 20초쯤 걸립니다"
echo
echo "════════════════════════════════════════════════════════"
echo "  뜨고 나면 여기를 엽니다"
echo
echo "     http://localhost:18000/dev-console.html"
echo
echo "  이 창은 켜 둡니다. 닫으면 전부 멈춥니다."
echo "════════════════════════════════════════════════════════"
echo

exec ./gradlew bootRun
