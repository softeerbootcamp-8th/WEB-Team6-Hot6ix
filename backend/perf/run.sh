#!/usr/bin/env bash
# 측정 한 줄을 처음부터 끝까지 돌린다.
#
#   ./perf/run.sh --scenario 1 --vus 40 --pool 10 --items 1 --who 승민
#
# 하는 일
#   jar 빌드 → 컨테이너 재기동(DB 초기화) → health 대기 → 시딩 → 워밍업 30초
#   → k6 → Prometheus 에서 값 뽑기 → 결과 폴더 저장 → index.csv 에 한 줄 append
#
# 손으로 하면 반드시 하나를 빠뜨린다. 그 한 번 때문에 개선 전후 비교가 깨진다.
#
# 사람이 쓰는 건 note.md 의 판정 칸 두 개뿐이다. 숫자는 전부 이 스크립트가 채운다.
# 손으로 옮기게 하면 단위도 집계도 사람마다 달라진다.

set -euo pipefail

PERF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "$PERF_DIR/.." && pwd)"
COMPOSE=(docker compose -f "$PERF_DIR/docker-compose.perf.yml")

SCENARIO=1
VUS=40
POOL=10
ITEMS=1
SSE=0
USERS=200
HEARTBEAT_MS=30000
SCHEDULER_POOL=4
START_PRICE=10000
BID_UNIT=1000
DURATION="3m"
WARMUP=30
WHO="${PERF_WHO:-$(whoami)}"
JAVA_OPTS=""
SKIP_BUILD=0
VIRTUAL_THREADS=false
BID_ITEMS=0
ISOLATION=RR
CPUS=2.0
BULK_ITEMS=0
SWEEP_INDEX=off

# perf 는 측정용 포트를 따로 쓴다. 개발 백엔드(8080)나 프론트(5173)와 안 겹치게 한다.
# 겹치면 측정을 시작하는 순간 개발 환경이 죽고, 프론트가 조용히 perf 앱에 붙는다.
PERF_HTTP_PORT="${PERF_HTTP_PORT:-18080}"
PERF_PROM_PORT="${PERF_PROM_PORT:-19090}"
PERF_GRAFANA_PORT="${PERF_GRAFANA_PORT:-13000}"
# 주소는 --port 까지 읽은 뒤에 만든다 (인자 파싱 아래에서).

usage() {
  cat <<'USAGE'
사용법: run.sh [옵션]

  --scenario N       0=부하 발생기 한계, 1~5=시나리오 (기본 1)
                     5 는 마감과 입찰을 겹친다. --items 로 마감할 물품 수를 준다
  --vus N            가상 사용자 수. 계단은 10/20/40/80/160 (기본 40)
  --items N          물품 수. 시나리오 1은 1, 2는 20 (기본 1)
  --sse N            같이 붙여 둘 SSE 접속 수. 5번(겹쳐 재기)에서 쓴다 (기본 0)
  --users N          시딩할 구매자 수. --vus 보다 커야 한다 (기본 200)

  대조 실험용 (한 번에 하나만 바꾼다)
  --pool N           DB_POOL_SIZE (기본 10)
  --scheduler-pool N spring.task.scheduling.pool.size (기본 4)
  --heartbeat-ms N   SSE heartbeat 주기 (기본 30000)
  --xmx SIZE         앱 힙 상한. 예: 512m
  --virtual-threads  가상 스레드를 켠다. **톰캣도 함께 바뀐다**(전역 스위치)
  --bid-items N      시나리오 5 에서 입찰을 넣을 물품 수. 나머지는 마감 대상이 된다
                     (기본: 절반. 전부에 입찰하면 Soft Close 로 안 닫힌다)
  --cpus N           앱과 MySQL 컨테이너에 줄 코어 수 (기본 2.0)
                     둘을 함께 올린다. 한쪽만 올리면 어느 쪽이 CPU 에 묶였는지 못 가른다.
                     **기본값으로 잰 결과와는 비교하지 않는다**
  --isolation RC|RR  MySQL 트랜잭션 격리 수준 (기본 RR = MySQL 기본값)
                     RR 은 갭 락을 걸고 RC 는 안 건다. 경합이 행 락 때문인지 갭 때문인지 가른다
  --bulk-items N     닫힌 물품 N 개를 SQL 로 밀어 넣어 표를 불린다 (기본 0)
                     마감 대상을 찾는 조회가 표 전체를 훑는지 보려면 표가 커야 한다
  --sweep-index on|off  auction_items(status, end_at) 인덱스를 만들지 (기본 off)
                     --bulk-items 와 짝으로 켜고 끄면서 조회 시간이 어떻게 달라지는지 본다

  --duration D       측정 길이 (기본 3m)
  --warmup N         워밍업 초 (기본 30)
  --who NAME         결과에 남길 이름 (기본 whoami)
  --skip-build       jar 를 다시 안 만든다. 같은 커밋으로 계단만 올릴 때
  --port N           perf nginx 를 띄울 호스트 포트 (기본 18080)

측정용 포트는 개발 백엔드(8080)나 프론트(5173)와 겹치지 않는다.
  nginx 18080   Prometheus 19090   Grafana 13000
환경변수 PERF_HTTP_PORT / PERF_PROM_PORT / PERF_GRAFANA_PORT 로도 바꿀 수 있다.
USAGE
}

while [ $# -gt 0 ]; do
  case "$1" in
    --scenario) SCENARIO="$2"; shift 2 ;;
    --vus) VUS="$2"; shift 2 ;;
    --items) ITEMS="$2"; shift 2 ;;
    --sse) SSE="$2"; shift 2 ;;
    --users) USERS="$2"; shift 2 ;;
    --pool) POOL="$2"; shift 2 ;;
    --scheduler-pool) SCHEDULER_POOL="$2"; shift 2 ;;
    --heartbeat-ms) HEARTBEAT_MS="$2"; shift 2 ;;
    --xmx) JAVA_OPTS="-Xmx$2"; shift 2 ;;
    --virtual-threads) VIRTUAL_THREADS=true; shift ;;
    --bid-items) BID_ITEMS="$2"; shift 2 ;;
    --cpus) CPUS="$2"; shift 2 ;;
    --isolation) ISOLATION="$2"; shift 2 ;;
    --bulk-items) BULK_ITEMS="$2"; shift 2 ;;
    --sweep-index) SWEEP_INDEX="$2"; shift 2 ;;
    --duration) DURATION="$2"; shift 2 ;;
    --warmup) WARMUP="$2"; shift 2 ;;
    --who) WHO="$2"; shift 2 ;;
    --skip-build) SKIP_BUILD=1; shift ;;
    --port) PERF_HTTP_PORT="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "모르는 옵션: $1" >&2; usage; exit 1 ;;
  esac
done

case "$ISOLATION" in
  RC) TX_ISOLATION="READ-COMMITTED" ;;
  RR) TX_ISOLATION="REPEATABLE-READ" ;;
  *) echo "--isolation 은 RC 또는 RR 이다 (받은 값: $ISOLATION)" >&2; exit 1 ;;
esac
export TX_ISOLATION

case "$SWEEP_INDEX" in
  on|off) ;;
  *) echo "--sweep-index 는 on 또는 off 다 (받은 값: $SWEEP_INDEX)" >&2; exit 1 ;;
esac

command -v jq >/dev/null || { echo "jq 가 필요하다: brew install jq" >&2; exit 1; }
command -v docker >/dev/null || { echo "docker 가 필요하다" >&2; exit 1; }

export PERF_HTTP_PORT PERF_PROM_PORT PERF_GRAFANA_PORT

APP_URL="http://localhost:$PERF_HTTP_PORT"
PROM_URL="http://localhost:$PERF_PROM_PORT"
GRAFANA_URL="http://localhost:$PERF_GRAFANA_PORT"

# ── 사전 점검 ──────────────────────────────────────────────────────
# 노트북마다 다른 것 중에 결과를 통째로 망치는 게 둘 있다. 기억에 맡기면 반드시 빠뜨린다.
# 두 번째 실행이 시작하면 첫 번째가 쓰던 컨테이너를 down 으로 내려 버린다. 그러면 앞 실행은
# 앱이 사라진 채로 남은 시간을 재고, 그 숫자가 조용히 이상해진다. 실제로 한 번 겪었다.
# mkdir 은 원자적이라 잠금으로 쓸 수 있다.
LOCK_DIR="$PERF_DIR/.run.lock"

acquire_lock() {
  if ! mkdir "$LOCK_DIR" 2>/dev/null; then
    local owner
    owner="$(cat "$LOCK_DIR/pid" 2>/dev/null || echo '?')"
    if [ "$owner" != "?" ] && kill -0 "$owner" 2>/dev/null; then
      echo "※ 이미 측정이 돌고 있다 (pid $owner). 한 대에서 둘을 같이 못 돌린다." >&2
      echo "  먼저 끝나기를 기다리거나, 그 프로세스를 끝내고 다시 실행한다." >&2
      exit 1
    fi
    # 남은 잠금인데 주인이 없다 — 앞 실행이 강제 종료된 경우다.
    echo "※ 주인 없는 잠금을 걷어낸다 (앞 실행이 비정상 종료된 듯하다)" >&2
    rm -rf "$LOCK_DIR"
    mkdir "$LOCK_DIR" 2>/dev/null || { echo "잠금을 잡지 못했다" >&2; exit 1; }
  fi
  echo "$$" >"$LOCK_DIR/pid"
  trap cleanup EXIT
}

# trap 을 두 군데서 걸면 뒤엣것이 앞엣것을 덮어써서 잠금이 안 풀린다. 한 곳에 모은다.
# 샘플러 둘 다 빈 값으로 시작해야 한다. set -u 라 아직 안 뜬 샘플러를 트랩이 참조하면 그
# 자리에서 터지고, 그러면 아래 rm 까지 못 가서 잠금이 남는다.
CPU_SAMPLER=""
LOCK_SAMPLER=""
cleanup() {
  # || true 가 없으면 이미 죽은 샘플러에 kill 이 실패하고, set -e 가 그걸 잡아서
  # 정상 종료인데도 종료 코드가 1 로 나간다 (실측으로 확인).
  if [ -n "$CPU_SAMPLER" ]; then
    kill "$CPU_SAMPLER" 2>/dev/null || true
  fi

  # CPU 샘플러와 따로 본다. 락 샘플러가 뒤에 뜨므로 둘을 한 조건에 묶으면 안 죽는 경우가 생긴다.
  if [ -n "$LOCK_SAMPLER" ]; then
    kill "$LOCK_SAMPLER" 2>/dev/null || true
  fi

  rm -rf "$LOCK_DIR"
  return 0
}

# 포트를 누가 잡고 있으면 컨테이너가 조용히 안 뜨거나, 더 나쁘게는 남의 포트를 뺏는다.
assert_ports_free() {
  local blocked=""
  for entry in "$PERF_HTTP_PORT:nginx" "$PERF_PROM_PORT:prometheus" "$PERF_GRAFANA_PORT:grafana"; do
    local port="${entry%%:*}" name="${entry##*:}"

    # 우리 perf 컨테이너가 잡고 있는 건 정상이다(재실행). 그 외가 잡고 있으면 막는다.
    if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
      if ! docker ps --filter "label=com.docker.compose.project=upbid-perf" --format '{{.Ports}}' \
           2>/dev/null | grep -q ":$port->"; then
        blocked="$blocked\n  $port ($name) — $(lsof -nP -iTCP:"$port" -sTCP:LISTEN -Fc 2>/dev/null | grep '^c' | head -1 | cut -c2-)"
      fi
    fi
  done

  if [ -n "$blocked" ]; then
    printf '※ 다른 프로세스가 perf 포트를 쓰고 있다:%b\n' "$blocked" >&2
    echo "  PERF_HTTP_PORT / PERF_PROM_PORT / PERF_GRAFANA_PORT 로 바꾸거나 그 프로세스를 끝낸다." >&2
    exit 1
  fi
}

preflight() {
  local cores
  cores="$(docker info --format '{{.NCPU}}' 2>/dev/null || echo 0)"

  # 앱 2 + MySQL 2 를 주고 나면 나머지가 k6 와 nginx, Prometheus 몫이다.
  # 4코어짜리에 돌리면 부하 발생기가 서버보다 먼저 막혀서, 그 숫자는 서버 한계가 아니다.
  if [ "$cores" -lt 6 ] 2>/dev/null; then
    echo "※ Docker Desktop 에 할당된 코어가 ${cores}개다. 최소 6개는 있어야 한다." >&2
    echo "  (앱 2 + MySQL 2 + k6 몫). Settings > Resources 에서 올린다." >&2
    echo "  이대로 재면 부하 발생기가 먼저 막혀서 서버 한계를 못 본다." >&2
  fi

  # SSE 수백 개를 붙일 때 macOS 기본값(보통 256)에서 먼저 터진다.
  # 서버가 못 버틴 것으로 착각하기 딱 좋다. k6 는 컨테이너 안이라 직접 영향은 없지만,
  # 이 셸에서 curl 로 시딩할 때와 사람이 손으로 확인할 때 걸린다.
  local nofile
  nofile="$(ulimit -n 2>/dev/null || echo 0)"
  if [ "$nofile" -lt 4096 ] 2>/dev/null; then
    echo "※ 이 셸의 ulimit -n 이 ${nofile}이다. 측정 전에 \`ulimit -n 65535\` 를 한 번 친다." >&2
  fi

  # 입찰하는 시나리오만 회원이 필요하다.
  #   0 (capacity)  /actuator/health 만 때린다
  #   3 (SSE)       subscribe 가 @GuestAllowed 라 로그인도 약관 동의도 필요 없다
  #   4 (동시 마감)  판매자 한 명만 쓴다
  # 시나리오 5 는 물품을 입찰용과 마감용으로 나눈다. 하나뿐이면 나눌 수가 없다.
  if [ "$SCENARIO" = "5" ] && [ "$ITEMS" -lt 2 ] 2>/dev/null; then
    echo "※ 시나리오 5 는 --items 가 2 이상이어야 한다. 입찰용과 마감용으로 나누기 때문이다." >&2
    exit 1
  fi

  case "$SCENARIO" in
    1|2|5)
      if [ "$USERS" -lt "$VUS" ] 2>/dev/null; then
        echo "※ 시딩할 회원(${USERS})이 VU(${VUS})보다 적다. 남는 VU 는 약관 동의가 없어" >&2
        echo "  전부 TERMS_NOT_AGREED 로 거절된다. --users 를 올린다." >&2
        exit 1
      fi
      ;;
  esac
}

acquire_lock
assert_ports_free
preflight

# ── 실행 이름 ──────────────────────────────────────────────────────
# 파라미터를 이름에 박아 두면 폴더 목록만 봐도 뭘 잰 건지 안다.
# 초까지 넣는다. 분까지만 찍으면 앞 실행이 빨리 실패했을 때 다음 실행이 같은 분에 들어와
# 실행 이름이 겹친다. 그러면 결과 폴더가 덮어써지고 Prometheus 의 run 라벨까지 같아져서,
# 라벨을 도입해 막았던 "직전 실행 값을 집어오는" 문제가 그대로 다시 열린다.
# 실측으로 겪었다 — 3연속 실행에서 2회차가 2/8 단계에 죽고 3회차가 같은 이름을 가져갔다.
STAMP="$(date +%Y-%m-%dT%H-%M-%S)"
RUN_ID="${STAMP}_s${SCENARIO}_vus${VUS}_pool${POOL}_items${ITEMS}_sse${SSE}"
RESULT_DIR="$PERF_DIR/results/$RUN_ID"
mkdir -p "$RESULT_DIR"

COMMIT="$(git -C "$BACKEND_DIR" rev-parse --short HEAD)"
DIRTY="$(git -C "$BACKEND_DIR" status --porcelain -- "$BACKEND_DIR" | head -1)"

echo "═══ $RUN_ID ═══"
echo "커밋 $COMMIT${DIRTY:+  (커밋 안 된 변경 있음 — 남이 재현 못 한다)}"

# ── 1. jar ────────────────────────────────────────────────────────
# Dockerfile 은 미리 만들어 둔 app.jar 를 복사만 한다(CI 와 같은 방식).
# 그래서 도커에 넘기기 전에 여기서 만든다.
if [ "$SKIP_BUILD" -eq 0 ]; then
  echo "[1/8] jar 빌드"
  (cd "$BACKEND_DIR" && ./gradlew --no-daemon -q bootJar)
  find "$BACKEND_DIR/build/libs" -name '*.jar' ! -name '*-plain.jar' -exec cp {} "$BACKEND_DIR/app.jar" \;
fi

[ -f "$BACKEND_DIR/app.jar" ] || { echo "app.jar 가 없다. --skip-build 를 빼고 다시 돌린다." >&2; exit 1; }

# ── 2. 컨테이너 ────────────────────────────────────────────────────
# down 만으로 DB 가 비워진다. mysql 에 볼륨을 안 붙여 뒀기 때문이다
# (`down -v` 는 팀 금지 명령이라 쓸 수 없다).
echo "[2/8] 컨테이너 재기동 (DB 초기화)"
export PERF_RUN_ID="$RUN_ID"
export DB_POOL_SIZE="$POOL"
export SCHEDULER_POOL_SIZE="$SCHEDULER_POOL"
export SSE_HEARTBEAT_MS="$HEARTBEAT_MS"
export APP_JAVA_OPTS="$JAVA_OPTS"
export VIRTUAL_THREADS
export PERF_CPUS="$CPUS"

# 일회성 컨테이너(docker compose run 으로 띄운 k6)는 down 이 안 지운다. 설계가 그렇다.
# 안 지우면 직전 실행의 배경 SSE 가 새 앱에 그대로 붙어서, 이번 줄의 접속 수와 스레드가
# 조용히 부풀려진다 (실측: 다음 실행 시작 시점에 톰캣 스레드가 이미 87이었다).
cleanup_oneoff() {
  docker ps -aq \
    --filter "label=com.docker.compose.project=upbid-perf" \
    --filter "label=com.docker.compose.oneoff=True" 2>/dev/null \
    | while read -r cid; do docker rm -f "$cid" >/dev/null 2>&1 || true; done
}

cleanup_oneoff

# down 이 아니라 이 셋만 지운다.
#
# down 은 프로젝트를 통째로 내리므로 Prometheus 와 Grafana 까지 걷어간다. 그 둘은 런처가
# 미리 띄워 두는 것이라(측정 전에 :13000 을 열어도 연결 거부가 안 나게 하려고), 여기서
# 내렸다가 아래 up 전에 실행이 죽으면 **그래프가 사라진 채로 안 돌아온다.** 런처는 이미
# 지나갔으니 다시 안 띄운다. 실제로 겪었다 — 2/8 단계에서 정지시킨 실행 하나 때문에
# 그 뒤로 계속 :13000 이 연결 거부였다.
#
# -v 는 **익명 볼륨만** 지운다. mysql 이미지가 /var/lib/mysql 을 볼륨으로 선언해서 컨테이너
# 마다 익명 볼륨이 하나씩 생기는데, -v 가 없으면 실행할 때마다 그게 하나씩 떠돌며 쌓인다
# (실측: rm 뒤 dangling 볼륨이 1 늘었고, -v 를 붙이니 안 늘었다).
#
# 팀 금지 명령인 `down -v` 와는 다르다. 저건 프로젝트의 named 볼륨까지 지워서 로컬 DB 가
# 날아가는 명령이고, 이건 여기 적은 세 서비스의 익명 볼륨만 건드린다. 측정용 DB 는 매 실행
# 비우는 게 목적이라 지워도 되는 것이고, named 볼륨인 prometheus-data 와 다른 프로젝트의
# 개발용 DB 볼륨은 그대로 남는 것을 확인했다.
"${COMPOSE[@]}" rm -sfv nginx app mysql >/dev/null 2>&1 || true
UP_AT="$(date +%s)"
"${COMPOSE[@]}" up -d --build nginx app mysql prometheus grafana

# Prometheus 가 실행 사이에 살아남게 됐으므로 설정 파일을 다시 읽게 한다.
#
# 예전에는 down 이 매번 컨테이너를 새로 만들어서 prometheus.yml 수정이 저절로 반영됐다.
# 이제는 안 그러므로, 파일만 고치고 "왜 안 먹지" 하는 새 함정이 생긴다. Prometheus 는
# SIGHUP 으로 설정을 다시 읽는다(--web.enable-lifecycle 을 안 켰으므로 /-/reload 는 없다).
# 방금 새로 만들어졌으면 그냥 한 번 더 읽는 것이라 아무 일도 안 일어난다.
"${COMPOSE[@]}" kill -s SIGHUP prometheus >/dev/null 2>&1 || true

echo "[3/8] 기동 대기"
for _ in $(seq 1 120); do
  if curl -sf "$APP_URL/actuator/health" >/dev/null 2>&1; then break; fi
  sleep 2
done
curl -sf "$APP_URL/actuator/health" >/dev/null || {
  echo "앱이 안 떴다. 로그:" >&2
  "${COMPOSE[@]}" logs --tail 60 app >&2
  exit 1
}

# Prometheus 가 이 컨테이너를 실제로 긁기 시작할 때까지 기다린다.
#
# 앱이 떴다고 바로 재면 안 된다. 서비스 디스커버리가 새 컨테이너를 찾는 데 시간이 걸리고,
# 그 사이 구간이 시작되면 앞부분이 비는 것으로 끝나지 않는다 — 도커가 IP 를 재사용하면
# 시계열 이름이 직전 실행과 같아서, 구간 시작값으로 **직전 실행의 마지막 값**을 집어온다.
# 그러면 구간 증가분이 음수로 나온다 (실측으로 확인).
#
# process_start_time_seconds 는 JVM 이 뜬 시각이라 컨테이너마다 다르다. 이 값이 우리가
# up 을 부른 시각보다 뒤면, 지금 보고 있는 게 새 컨테이너라는 뜻이다.
echo "      Prometheus 가 새 컨테이너를 잡을 때까지 대기"
RUN_LABEL="run=\"$RUN_ID\""
PROM_READY=0
for _ in $(seq 1 60); do
  STARTED="$(curl -sG "$PROM_URL/api/v1/query" \
    --data-urlencode "query=max(process_start_time_seconds{$RUN_LABEL})" \
    | jq -r '.data.result[0].value[1] // "0"')"

  if awk -v a="$STARTED" -v b="$UP_AT" 'BEGIN { exit !(a + 0 >= b + 0) }'; then
    PROM_READY=1
    break
  fi
  sleep 2
done
[ "$PROM_READY" -eq 1 ] || {
  echo "Prometheus 가 앱을 못 찾았다. prometheus.yml 의 dns_sd 설정을 본다." >&2
  exit 1
}

# 계측이 실제로 나오는지 여기서 확인한다. 3분을 다 재고 나서 그래프가 비어 있는 걸
# 발견하면 그 한 줄을 통째로 다시 돌려야 한다.
# ── 3. 시딩 ────────────────────────────────────────────────────────
echo "[4/8] 시딩"
SEED_ENV="$RESULT_DIR/seed.env"
SEED_START="all"
case "$SCENARIO" in 4|5) SEED_START="none" ;; esac

BASE_URL="$APP_URL/api/v1" "$PERF_DIR/seed.sh" \
  --users "$USERS" --items "$ITEMS" \
  --start-price "$START_PRICE" --unit "$BID_UNIT" \
  --start "$SEED_START" --out "$SEED_ENV"

# shellcheck source=/dev/null
. "$SEED_ENV"

# DB 를 직접 볼 때 쓴다. seed.sh 가 아니라 여기 두는 이유는 seed.sh 는 API 만 알고 있어서
# --base 로 개발 앱에도 쓸 수 있는데, 컨테이너 이름을 아는 순간 그게 깨지기 때문이다.
mysql_query() {
  "${COMPOSE[@]}" exec -T mysql \
    mysql -uroot -p1234 -N -B upbid -e "$1" 2>/dev/null || true
}

# 마감 대상을 찾는 조회는 status 와 end_at 으로 거른다. 물품이 스무 개뿐이면 표 전체를 훑어도
# 순식간이라 인덱스가 있으나 없으나 같은 숫자가 나오고, 그러면 인덱스를 넣을 근거를 못 만든다.
# 닫힌 물품을 채워 표를 불려야 훑는 비용이 드러난다.
#
# 채우는 행은 이미 있는 행을 그대로 복사한다. 컬럼 목록을 손으로 적으면 엔티티에 컬럼이
# 하나 붙을 때마다 여기가 조용히 깨지므로 information_schema 에서 읽어 온다.
# 복사한 뒤 상태를 CLOSED 로 바꾸고 end_at 을 흩어 놓는다. 전부 같은 값이면 인덱스를 타도
# 한 덩어리라 범위 조회가 좁혀지지 않는다.
if [ "$BULK_ITEMS" -gt 0 ]; then
  echo "[4/8] 닫힌 물품 $BULK_ITEMS 개 채우기"

  LAST_REAL_ID="$(mysql_query "SELECT COALESCE(MAX(auction_item_id), 0) FROM auction_items")"
  TOTAL="$(mysql_query "SELECT COUNT(*) FROM auction_items")"
  REMAINING="$BULK_ITEMS"

  while [ "$REMAINING" -gt 0 ]; do
    # 한 번에 지금 있는 만큼까지만 복사할 수 있다. 표가 두 배씩 커지므로 반복은 금방 끝난다.
    BATCH="$REMAINING"
    [ "$BATCH" -gt "$TOTAL" ] && BATCH="$TOTAL"

    mysql_query "
      SET @cols := (SELECT GROUP_CONCAT(COLUMN_NAME) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'auction_items'
                      AND COLUMN_NAME <> 'auction_item_id');
      SET @sql := CONCAT('INSERT INTO auction_items (', @cols, ') SELECT ', @cols,
                         ' FROM auction_items LIMIT $BATCH');
      PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;" >/dev/null

    REMAINING=$((REMAINING - BATCH))
    TOTAL=$((TOTAL + BATCH))
  done

  mysql_query "
    UPDATE auction_items
       SET status = 'CLOSED',
           end_at = DATE_SUB(NOW(), INTERVAL (auction_item_id % 86400) SECOND)
     WHERE auction_item_id > $LAST_REAL_ID" >/dev/null

  echo "[4/8]   물품 표: $(mysql_query "SELECT COUNT(*) FROM auction_items") 행"
fi

if [ "$SWEEP_INDEX" = "on" ]; then
  echo "[4/8] idx_auction_items_status_end_at 만들기"
  mysql_query "CREATE INDEX idx_auction_items_status_end_at
               ON auction_items (status, end_at)" >/dev/null

  # mysql_query 는 stderr 를 버리고 || true 로 끝나서, CREATE 가 실패해도 그냥 지나간다.
  # 그러면 on 과 off 가 사실상 같은 실행이 되고 "인덱스를 넣어도 효과가 없다"는 틀린 결론이
  # 나온다. 대조 실험은 한쪽이 조용히 안 걸리는 게 제일 나쁘므로 여기서 멈춘다.
  if [ -z "$(mysql_query "SHOW INDEX FROM auction_items
                          WHERE Key_name = 'idx_auction_items_status_end_at'")" ]; then
    echo "인덱스가 안 만들어졌다. --sweep-index on 인데 off 와 같은 실행이 되므로 멈춘다." >&2
    exit 1
  fi
fi

# 마감 대상 조회가 실제로 어떻게 도는지 남긴다. 인덱스를 켠 실행과 끈 실행의 이 파일을
# 나란히 놓으면 type 이 ALL 에서 range 로 바뀌었는지, filesort 가 사라졌는지가 보인다.
SWEEP_SQL="SELECT auction_item_id, end_at FROM auction_items
           WHERE status = 'IN_PROGRESS' AND end_at IS NOT NULL ORDER BY end_at ASC"
mysql_query "EXPLAIN $SWEEP_SQL" >"$RESULT_DIR/sweep_explain.txt"

# 계측이 실제로 붙었는지 여기서 확인한다. 시딩이 수백 건을 보낸 뒤라 커넥션 풀도 쓰였고
# 요청 지표도 생겼다 — 기동 직후에 보면 아직 없는 게 정상이라 잡아낼 수가 없다.
# 3분을 다 재고 나서 그래프가 비어 있는 걸 발견하면 그 한 줄을 통째로 다시 돌려야 한다.
#
#   tomcat_threads_busy_threads   ← server.tomcat.mbeanregistry.enabled 를 빠뜨리면 통째로 없다
#   *_seconds_bucket              ← percentiles-histogram 을 안 켜면 없고, 그러면 p95 를 못 뽑는다
#   upbid_*                       ← 계측 코드 자체
#   hikaricp_connections_pending  ← 없으면 "줄 서 있는 중"을 못 본다
MISSING=""
for _ in $(seq 1 10); do
  METRICS="$(curl -s "$APP_URL/actuator/prometheus")"
  MISSING=""
  for metric in tomcat_threads_busy_threads \
                hikaricp_connections_active hikaricp_connections_pending \
                http_server_requests_seconds_bucket \
                upbid_sse_connections upbid_sse_rooms \
                upbid_bid_lock_wait_seconds_bucket \
                upbid_bid_lock_hold_seconds_bucket \
                upbid_auction_close_delay_seconds_bucket \
                upbid_auction_close_duration_seconds_bucket \
                executor_queued_tasks; do
    # 파이프를 쓰면 안 된다. grep -q 는 일치를 찾자마자 끝나는데, 그러면 앞 명령이
    # SIGPIPE 로 죽고 set -o pipefail 이 그걸 파이프라인 실패로 본다. 그래서 출력 앞쪽에
    # 있는 지표만 "없다"고 나온다 (실측으로 한참 헤맸다). here-string 은 파이프가 아니다.
    grep -q "^$metric" <<<"$METRICS" || MISSING="$MISSING $metric"
  done
  [ -z "$MISSING" ] && break
  sleep 3
done

if [ -n "$MISSING" ]; then
  echo "계측이 안 붙었다:$MISSING" >&2
  echo "  application-perf.yaml 의 management.* 와 server.tomcat.mbeanregistry 를 본다." >&2
  exit 1
fi

# ── 4. 배경 SSE ────────────────────────────────────────────────────
# 5번(겹쳐 재기)용. 입찰 부하와 별개로 접속을 미리 붙여 둔다.
SSE_CONTAINER=""
if [ "$SSE" -gt 0 ]; then
  echo "[5/8] 배경 SSE 접속 $SSE 개"
  # -e 를 하나씩 적어야 한다. docker compose run 은 셸 환경변수를 그냥 물려주지 않는다.
  # (RUN_ID 를 비워 두면 scenario3.js 가 요약 파일을 안 쓴다 — 본 측정 결과를 덮으면 안 된다.)
  # 컨테이너 이름을 받아 둔다. 안 그러면 뒤의 CPU 샘플러가 이 컨테이너와 본 부하 컨테이너를
  # 구분 못 해서, k6_cpu_max 가 둘 중 어느 쪽인지 모르는 값이 된다.
  SSE_CID="$("${COMPOSE[@]}" run --rm -d --no-deps \
    -e "VUS=$SSE" -e "SHARE_CODE=$SHARE_CODE" -e "RUN_ID=" -e "DURATION=30m" \
    k6 run /scripts/scenario3.js)"
  SSE_CONTAINER="$(docker inspect --format '{{.Name}}' "$SSE_CID" 2>/dev/null | sed 's#^/##')"

  # 접속이 실제로 붙었는지 확인하고 넘어간다. 안 붙은 채로 재면 "SSE 를 붙였는데 영향이
  # 없었다"는 잘못된 결론이 나온다.
  sleep 15
  ATTACHED="$(curl -s "$APP_URL/actuator/metrics/upbid.sse.connections" \
    | jq -r '.measurements[0].value // 0')"
  echo "[5/8]   붙은 접속 $ATTACHED / $SSE"
  if [ "${ATTACHED%%.*}" -lt "$((SSE / 2))" ] 2>/dev/null; then
    echo "※ SSE 접속이 목표의 절반도 안 붙었다. ulimit -n 이나 nginx worker_connections 를 본다." >&2
  fi
else
  echo "[5/8] 배경 SSE 없음"
fi

# ── 5. 워밍업 ──────────────────────────────────────────────────────
# JIT 이 덥혀지고 커넥션 풀이 채워지기 전 구간을 재면 첫 줄만 유독 느리게 나온다.
echo "[6/8] 워밍업 ${WARMUP}초"
sleep "$WARMUP"

row_lock_status() {
  mysql_query "SHOW GLOBAL STATUS LIKE 'Innodb_row_lock%'"
}
row_lock_status >"$RESULT_DIR/innodb_row_lock_before.txt"

# 커밋이 느려진 이유를 MySQL 쪽에서 본다.
#
# 앱이 잰 upbid.bid.commit 은 "커밋이 몇 ms 걸렸나"까지만 알려주고 왜 그런지는 모른다.
# innodb_flush_log_at_trx_commit=1 과 sync_binlog=1 이라 커밋 한 번에 디스크 동기화가 두 번
# 일어나므로, 그 둘의 횟수와 시간을 따로 보면 커밋 지연이 fsync 에서 온 것인지 갈린다.
#
# performance_schema 의 타이머 단위는 피코초다. 1e9 로 나누면 ms 가 된다.
#
# COUNT_STAR 와 SUM_TIMER_WAIT 는 그 파일의 **모든** I/O 다 — 읽기와 쓰기와 동기화가 섞여
# 있다. fsync 만 보려면 MISC 쪽을 봐야 한다. 둘 다 남기는 이유는, 총 I/O 는 이 칸이 생기기
# 전에 잰 실행과도 나란히 놓을 수 있어서다.
#
# 실측으로 로그 파일 I/O 시간의 97 % 가 MISC(동기화)였다. 버퍼된 쓰기는 한 번에 0.005ms 인데
# 동기화는 0.232ms 다.
io_status() {
  mysql_query "SELECT EVENT_NAME, COUNT_STAR, SUM_TIMER_WAIT, COUNT_MISC, SUM_TIMER_MISC
               FROM performance_schema.file_summary_by_event_name
               WHERE EVENT_NAME IN ('wait/io/file/innodb/innodb_log_file',
                                    'wait/io/file/sql/binlog')"
}
io_status >"$RESULT_DIR/mysql_io_before.txt"

# 입찰 한 건이 SELECT 를 몇 번 하는지 본다.
#
# 입찰 경로는 물품·경매방·참여자·최고가를 각각 읽는데, 그중 하나가 락 안에 들어가 있으면
# 왕복이 늘어난 만큼 락을 오래 잡는다. 응답 시간만 보면 그게 왕복 수 때문인지 락 대기
# 때문인지 안 갈린다. Com_select 는 서버 전체 누적이라 구간 증가분을 입찰을 시도한 건수로
# 나눠서 "입찰 한 건당 몇 번"으로 만든다.
com_select() {
  mysql_query "SHOW GLOBAL STATUS LIKE 'Com_select'" | awk '{print $2}'
}
COM_SELECT_BEFORE="$(com_select)"

# ── 6. k6 ─────────────────────────────────────────────────────────
case "$SCENARIO" in
  0) SCRIPT="capacity.js" ;;
  1) SCRIPT="scenario1.js" ;;
  2) SCRIPT="scenario2.js" ;;
  3) SCRIPT="scenario3.js" ;;
  4) SCRIPT="scenario4.js" ;;
  5) SCRIPT="scenario5.js" ;;
  *) echo "모르는 시나리오: $SCENARIO (0~5)" >&2; exit 1 ;;
esac

WINDOW_START="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
WINDOW_START_EPOCH="$(date +%s)"

echo "[7/8] k6 $SCRIPT (vus=$VUS, $DURATION)"

# 컨테이너 CPU 를 밖에서 샘플링한다. 세 가지를 각각 봐야 한다.
#
#   k6    "여기부턴 부하 발생기가 병목" 판정. k6 는 자기 CPU 를 지표로 안 내보낸다.
#   app   `process_cpu_usage` 로도 나오지만 index.csv 에 없으면 아무도 안 본다.
#         실제로 pool 곡선을 20회 넘게 재는 동안 앱 CPU 를 한 번도 표에 안 올렸고,
#         나중에 보니 pool 4 에서도 시간의 15 % 를 100 % 에 붙어 있었다.
#   mysql **이 값만 다른 데서 못 구한다.** Prometheus 가 MySQL 을 안 긁어서,
#         커밋이 느린 게 디스크 때문인지 CPU 때문인지 가를 근거가 여기밖에 없다.
#
# `docker stats` 의 `CPUPerc` 는 호스트 코어 기준이라 cpus=2 면 200 % 까지 올라간다.
# 아래에서 --cpus 로 나눠 "준 코어를 몇 % 썼나" 로 바꾼다. 그래야 --cpus 를 바꾼 실행끼리
# 비교가 된다.
CPU_LOG="$RESULT_DIR/container_cpu.txt"
: >"$CPU_LOG"
(
  while true; do
    docker stats --no-stream --format '{{.Name}} {{.CPUPerc}}' 2>/dev/null \
      | awk -v skip="$SSE_CONTAINER" '
          { gsub(/%/, "", $2) }
          # 배경 SSE 컨테이너는 뺀다. 안 그러면 k6 값이 둘 중 어느 쪽인지 모르는 값이 된다.
          tolower($1) ~ /k6/ && (skip == "" || $1 != skip) { print "k6", $2; next }
          $1 ~ /^upbid-perf-app-/   { print "app", $2; next }
          $1 ~ /^upbid-perf-mysql-/ { print "mysql", $2; next }' \
      >>"$CPU_LOG" || true
    sleep 5
  done
) &
CPU_SAMPLER=$!
# 잡 제어에서 떼어낸다. 안 그러면 끝낼 때 셸이 "Terminated" 를 뱉어서 결과 출력이 지저분해진다.
disown "$CPU_SAMPLER" 2>/dev/null || true

# 지금 잡혀 있는 락이 어떤 종류인지 부하 도중에 훔쳐본다.
#
# data_locks 는 그 순간에 살아 있는 락만 담고 있어서 끝나고 보면 표가 비어 있다. 그래서
# 부하가 도는 동안 표본을 떠야 한다. RR 은 갭 락(GAP, NEXT-KEY)을 걸고 RC 는 안 걸어서,
# --isolation 을 바꿔 가며 이 파일을 보면 갭이 실제로 사라졌는지 확인할 수 있다.
LOCK_MODES_LOG="$RESULT_DIR/data_locks.txt"
: >"$LOCK_MODES_LOG"

# MySQL 안에서 실제로 몇 개가 동시에 일하고 있는지.
#
# 커넥션 풀 크기는 "몇 개까지 들여보낼 수 있나"이고 Threads_running 은 "지금 몇 개가 돌고
# 있나"다. 둘이 다르다 — 커넥션을 잡고도 락을 기다리는 스레드는 running 이 아니다.
# 이 값이 pool 을 따라 오르면 "풀을 키운 만큼 MySQL 이 더 바빠졌다"가 숫자로 나온다.
#
# Innodb_os_log_pending_fsyncs 는 디스크 동기화를 기다리며 쌓인 수다. 동시 작업량과 커밋
# 지연을 잇는 자리라 같이 찍는다. 셋 다 게이지라 누적이 아니고 그 순간 값이다.
#
# Innodb_row_lock_current_waits 는 그 순간 행 락에 막혀 있는 수다. Threads_running 이
# "자고 있지 않은 스레드"라 락을 기다리는 것도 세기 때문에, 이 값을 빼야 "실제로 일하는 수"가
# 나온다. 실측으로 같은 pool 10 에서 Threads_running 이 9.0 과 8.6 으로 거의 같은데 로그 I/O 가
# 1.57 배 차이 난 적이 있다 — 그때 갈라야 했던 것이 이 값이다.
CONCURRENCY_LOG="$RESULT_DIR/mysql_concurrency.txt"
: >"$CONCURRENCY_LOG"
(
  while true; do
    {
      echo "── $(date +%H:%M:%S)"
      mysql_query "SELECT LOCK_TYPE, LOCK_MODE, COUNT(*) FROM performance_schema.data_locks
                   GROUP BY LOCK_TYPE, LOCK_MODE ORDER BY LOCK_TYPE, LOCK_MODE"
    } >>"$LOCK_MODES_LOG"

    mysql_query "SHOW GLOBAL STATUS WHERE Variable_name IN
                 ('Threads_running','Innodb_os_log_pending_fsyncs',
                  'Innodb_row_lock_current_waits')" >>"$CONCURRENCY_LOG"

    sleep 5
  done
) &
LOCK_SAMPLER=$!
disown "$LOCK_SAMPLER" 2>/dev/null || true

set +e
VUS="$VUS" DURATION="$DURATION" RUN_ID="$RUN_ID" \
SHARE_CODE="$SHARE_CODE" ITEM_IDS="$ITEM_IDS" CLOSE_ITEM_IDS="$CLOSE_ITEM_IDS" \
BID_ITEMS="$BID_ITEMS" \
START_PRICE="$START_PRICE" BID_UNIT="$BID_UNIT" \
  "${COMPOSE[@]}" run --rm \
    -e VUS -e DURATION -e RUN_ID -e SHARE_CODE -e ITEM_IDS -e CLOSE_ITEM_IDS -e BID_ITEMS \
    -e START_PRICE -e BID_UNIT \
    k6 run -o experimental-prometheus-rw "/scripts/$SCRIPT" \
    2>&1 | tee "$RESULT_DIR/k6.log"
K6_EXIT="${PIPESTATUS[0]}"
set -e

kill "$CPU_SAMPLER" 2>/dev/null || true
kill "$LOCK_SAMPLER" 2>/dev/null || true

WINDOW_END="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
WINDOW_END_EPOCH="$(date +%s)"
WINDOW_SECONDS=$((WINDOW_END_EPOCH - WINDOW_START_EPOCH))

row_lock_status >"$RESULT_DIR/innodb_row_lock_after.txt"
io_status >"$RESULT_DIR/mysql_io_after.txt"
COM_SELECT_AFTER="$(com_select)"

# 표본에서 갭까지 잠근 락이 몇 번 보였는지 센다.
#
# MySQL 은 넥스트키 락을 LOCK_MODE 에 그냥 X 로 적는다. 갭만 잠근 것이 X,GAP 이고 행 하나만
# 잠근 것이 X,REC_NOT_GAP 이다. 그래서 REC_NOT_GAP 이 아닌 행 락이 곧 갭을 문 락이다.
# REC_NOT_GAP 안에 GAP 이라는 글자가 들어 있어서 /GAP/ 로 세면 정반대를 센다.
#
# 5초마다 그 순간을 본 것이라 "몇 건이 걸렸다"가 아니라 "볼 때마다 몇 개가 있더라"다.
# 절대값을 읽을 숫자가 아니고, --isolation RC 로 바꿨을 때 0 으로 떨어지는지를 보는 값이다.
GAP_LOCKS="$(awk -F'\t' '$1 == "RECORD" && $2 !~ /REC_NOT_GAP|INSERT_INTENTION/ { n += $3 }
                          END { print n + 0 }' "$LOCK_MODES_LOG")"

# ── 7. 값 뽑기 ─────────────────────────────────────────────────────
echo "[8/8] Prometheus 에서 값 뽑기 (구간 ${WINDOW_SECONDS}초)"

# 구간 전체에 한 번 계산한다.
#
# 5초 간격으로 찍힌 p95 점들을 평균 내는 것은 수학적으로 틀리다. 그래서 Grafana 그래프를
# 눈으로 읽어 적지 않고, increase() 로 구간 전체의 버킷 증가분을 모아 거기에
# histogram_quantile 을 한 번 씌운다.
promq() {
  local query="$1"
  curl -sG "$PROM_URL/api/v1/query" \
    --data-urlencode "query=$query" \
    --data-urlencode "time=$WINDOW_END_EPOCH" \
    | jq -r '.data.result[0].value[1] // "NaN"'
}

W="${WINDOW_SECONDS}s"
# SSE 구독은 끝나지 않는 스트림이라 "요청 하나"로 셀 수 없다. 연결이 끊길 때 한 번에
# 수십 초짜리 응답으로 기록돼서, 섞으면 응답 p95 가 통째로 그쪽으로 끌려간다
# (실측: SSE 100개를 붙였더니 전체 p95 가 Micrometer 최대 버킷인 30초로 찍혔다).
# SSE 는 upbid_sse_connections 와 upbid_sse_heartbeat 로 따로 본다.
# 이번 실행의 시계열만 본다. run 라벨이 실행마다 달라서 직전 실행 값을 집어올 수 없다.
RUN="run=\"$RUN_ID\""
NOT_ACTUATOR="$RUN, uri!=\"/actuator/prometheus\", uri!~\".*subscribe\""

# 처리량과 p95 는 그 시나리오가 재려는 요청만 본다.
#
# 예전에는 액추에이터와 SSE 구독만 뺐다. 그러면 dev-login 과 약관 동의, 물품 시작이 전부
# 섞인다. 시나리오 1 처럼 입찰이 40만 건인 실행에서는 0.2% 라 티가 안 나지만(실측: p95
# 46.70 -> 46.56ms), 시나리오 4 는 전체 요청이 수십 건뿐이라 물품 시작이 처리량 그 자체가 된다.
case "$SCENARIO" in
  0)     MAIN_URI=', uri="/actuator/health"' ;;
  1|2|5) MAIN_URI=', uri="/api/v1/auction-items/{auctionItemId}/bids"' ;;
  4)     MAIN_URI=', uri="/api/v1/auction-items/{auctionItemId}/start"' ;;
  # 3 은 SSE 구독이 주인공인데 끝나지 않는 스트림이라 응답 시간에 안 잡힌다. 그래서 안 좁힌다.
  *)     MAIN_URI='' ;;
esac

MAIN="$NOT_ACTUATOR$MAIN_URI"

# ── increase() 를 안 쓰는 이유 ──────────────────────────────────────
# 마감 지연처럼 짧은 순간에 몰아서 찍히는 지표는 스크랩 한 번(5초) 사이에 값이 다 올라간다.
# 그러면 구간 안의 첫 표본과 마지막 표본이 똑같아서 increase() 가 0을 준다.
# 실측: 물품 20개가 0.25초 만에 다 닫혔더니 count 는 20인데 increase 는 0이었다.
#
# 그래서 끝 시점 값에서 시작 시점 값을 뺀다. 시작 시점에 없던 계열은 0으로 본다
# (`or ... * 0` 이 그 역할이다 — 라벨은 그대로 두고 값만 0으로 만든 계열을 채워 넣는다).
# run.sh 는 실행마다 컨테이너를 새로 띄우므로 카운터가 0에서 시작하는 것도 이 계산을 돕는다.
delta() {
  printf '((%s @ %s) - ((%s @ %s) or (%s @ %s) * 0))' \
    "$1" "$WINDOW_END_EPOCH" "$1" "$WINDOW_START_EPOCH" "$1" "$WINDOW_END_EPOCH"
}

# 구간 전체를 하나의 histogram_quantile 로 계산한다.
# 5초마다 찍힌 p95 점들을 평균 내는 것은 수학적으로 틀리므로 그래프를 눈으로 읽어 적지 않는다.
quantile_of() {
  promq "histogram_quantile($2, sum by (le) ($(delta "$1"))) * 1000"
}

p95_of() {
  quantile_of "$1" 0.95
}

RPS="$(promq "sum($(delta "http_server_requests_seconds_count{$MAIN}")) / $WINDOW_SECONDS")"
P95_MS="$(p95_of "http_server_requests_seconds_bucket{$MAIN}")"
TOMCAT_BUSY_MAX="$(promq "max(max_over_time(tomcat_threads_busy_threads{$RUN}[$W]))")"
HIKARI_ACTIVE_MAX="$(promq "max(max_over_time(hikaricp_connections_active{$RUN}[$W]))")"
HIKARI_PENDING_MAX="$(promq "max(max_over_time(hikaricp_connections_pending{$RUN}[$W]))")"
HEAP_MB_MAX="$(promq "max(max_over_time(sum(jvm_memory_used_bytes{$RUN, area=\"heap\"})[$W:5s])) / 1024 / 1024")"
LOCK_WAIT_P95_MS="$(p95_of "upbid_bid_lock_wait_seconds_bucket{$RUN}")"
# 기다린 시간과 짝이다. 대기가 길고 유지가 짧으면 줄이 긴 것이고, 유지도 같이 길면 앞사람이
# 오래 잡고 있는 것이라 줄을 짧게 해도 안 풀린다.
LOCK_HOLD_P95_MS="$(p95_of "upbid_bid_lock_hold_seconds_bucket{$RUN}")"
# 접수와 거절은 락 안에서 하는 일이 다르다. 거절은 검증하고 롤백해서 짧고, 접수는 insert 와
# update, 커밋까지 간다. 합친 p95 만 보면 거절이 다수인 조건에서 접수 경로의 값을 알 수 없다.
LOCK_HOLD_ACC_P95_MS="$(p95_of "upbid_bid_lock_hold_seconds_bucket{$RUN, result=\"accepted\"}")"
LOCK_HOLD_REJ_P95_MS="$(p95_of "upbid_bid_lock_hold_seconds_bucket{$RUN, result=\"rejected\"}")"
# 락을 잡으러 가기 전에 쓴 시간. 커넥션 획득 + 락 없이 되는 SELECT 3개다.
# 이 값에서 conn_acquire_p95 를 빼면 SELECT 3개가 얼마인지 나온다.
BEFORE_LOCK_P95_MS="$(p95_of "upbid_bid_before_lock_seconds_bucket{$RUN}")"
# 락 보유 중 커밋이 차지하는 몫. 쿼리를 빼서 줄일 수 있는 부분과 못 줄이는 부분을 가른다.
COMMIT_P95_MS="$(p95_of "upbid_bid_commit_seconds_bucket{$RUN}")"
CONN_ACQUIRE_P95_MS="$(p95_of "hikaricp_connections_acquire_seconds_bucket{$RUN}")"
CLOSE_DELAY_P95_MS="$(p95_of "upbid_auction_close_delay_seconds_bucket{$RUN}")"
SSE_HEARTBEAT_P95_MS="$(p95_of "upbid_sse_heartbeat_seconds_bucket{$RUN}")"
SSE_BROADCAST_P95_MS="$(p95_of "upbid_sse_broadcast_seconds_bucket{$RUN}")"
SSE_CONN_MAX="$(promq "max(max_over_time(upbid_sse_connections{$RUN}[$W]))")"

# 마감은 p95 만으로 부족하다. 한 건이라도 크게 튀면 스레드가 그만큼 묶이므로 최대값을 같이 본다.
CLOSE_DELAY_P50_MS="$(quantile_of "upbid_auction_close_delay_seconds_bucket{$RUN}" 0.50)"
CLOSE_DELAY_MAX_MS="$(promq "max(max_over_time(upbid_auction_close_delay_seconds_max{$RUN}[$W])) * 1000")"
# 락을 기다린 시간이 여기 잡힌다. 실제로 닫은 마감만 본다(재예약은 result 로 갈라 둔다).
CLOSE_DURATION_P95_MS="$(quantile_of "upbid_auction_close_duration_seconds_bucket{$RUN, result=\"closed\"}" 0.95)"
# 실패가 0인 실행에서는 시계열이 아예 없다. or vector(0) 으로 받지 않으면 NaN 이 된다.
CLOSE_FAILURES="$(promq "sum($(delta "upbid_auction_close_failures_total{$RUN}")) or vector(0)")"

# 스케줄러 일꾼. Boot 가 ThreadPoolTaskScheduler 를 자동으로 계측해 줘서 직접 만들 게 없었다.
# 가상 스레드를 켜면 SimpleAsyncTaskScheduler 로 바뀌어 풀도 큐도 없어지므로 여기는 NaN 이 된다.
SCHED="$RUN, name=\"taskScheduler\""
SCHED_ACTIVE_MAX="$(promq "max(max_over_time(executor_active_threads{$SCHED}[$W]))")"
SCHED_QUEUED_MAX="$(promq "max(max_over_time(executor_queued_tasks{$SCHED}[$W]))")"
GC_PAUSE_MS_PER_S="$(promq "sum($(delta "jvm_gc_pause_seconds_sum{$RUN}")) / $WINDOW_SECONDS * 1000")"

# 입찰 결과는 k6 요약에서 읽는다.
#
# 서버 지표로는 못 가른다 — 입찰 거절이 BID_AMOUNT_TOO_LOW 도 CONCURRENT_BID_CONFLICT 도
# 전부 409 라, status 로 세면 "규칙대로 거절"과 "행 락이 못 막은 경합"이 한 칸에 섞인다.
k6sum() {
  jq -r --arg m "$1" '.metrics[$m].values.count // 0' "$RESULT_DIR/summary.json" 2>/dev/null || echo 0
}

if [ -f "$RESULT_DIR/summary.json" ]; then
  ACCEPTED="$(k6sum bid_accepted)"
  REJECTED_AMOUNT="$(k6sum bid_rejected_amount)"
  REJECTED_ALREADY_TOP="$(k6sum bid_rejected_already_top)"
  REJECTED_CLOSED="$(k6sum bid_rejected_closed)"
  CONCURRENT_CONFLICT="$(k6sum bid_concurrent_conflict)"
  REJECTED_OTHER="$(k6sum bid_rejected_other)"
  FAILED_5XX="$(k6sum bid_failed)"
else
  ACCEPTED=0; REJECTED_AMOUNT=0; REJECTED_ALREADY_TOP=0; REJECTED_CLOSED=0
  CONCURRENT_CONFLICT=0; REJECTED_OTHER=0; FAILED_5XX=0
fi

REJECTED_4XX=$((REJECTED_AMOUNT + REJECTED_ALREADY_TOP + REJECTED_CLOSED + CONCURRENT_CONFLICT + REJECTED_OTHER))

# 거절된 입찰도 물품과 방과 참여자를 다 읽고 나서 거절된다. 그래서 SELECT 를 나눌 때 쓰는
# 분모는 접수 건수가 아니라 시도 건수다.
BID_ATTEMPTS=$((ACCEPTED + REJECTED_4XX + FAILED_5XX))

# 계단을 올리면 워크로드 구성 자체가 바뀐다. 금액 격자 위에서 도착 순서가 섞이면 "역대 최고"
# 갱신만 접수되므로, VU 가 늘수록 접수 비율이 떨어지고 싼 거절이 늘어난다.
# 실측: vus 20 에서 1.04%, vus 40 에서 0.61% 였고 접수 건수는 6159 -> 3217 로 반토막이었다.
# 총 처리량만 보면 0.86배라 "약간 하락"으로 읽히는데, 실제로 한 일은 절반이다.
ACCEPTED_PER_S="$(awk -v a="$ACCEPTED" -v w="$WINDOW_SECONDS" 'BEGIN { printf "%.1f", (w > 0 ? a / w : 0) }')"

# k6 가 클라이언트에서 본 p95. 서버 p95 와 나란히 두면 서로 검산이 된다.
# 둘이 벌어지면 그 차이가 네트워크와 부하 발생기 몫이라, 그 자체가 신호다.
K6_P95_MS="$(jq -r '.metrics.http_req_duration.values["p(95)"] // "NaN"' "$RESULT_DIR/summary.json" 2>/dev/null || echo NaN)"

# 컨테이너별로 평균과 최대를 뽑는다. 앱과 MySQL 은 --cpus 로 나눠 "준 코어의 몇 %" 로
# 바꾸고, k6 는 제한을 안 걸었으니 호스트 코어 기준 그대로 둔다.
cpu_stat() {
  awk -v role="$1" -v div="$2" '
    $1 == role { n++; sum += $2; if ($2 > max) max = $2 }
    END {
      if (n == 0) { print "NaN NaN"; exit }
      printf "%.1f %.1f\n", sum / n / div, max / div
    }' "$CPU_LOG" 2>/dev/null || echo "NaN NaN"
}
read -r APP_CPU_AVG APP_CPU_MAX <<<"$(cpu_stat app "$CPUS")"
read -r MYSQL_CPU_AVG MYSQL_CPU_MAX <<<"$(cpu_stat mysql "$CPUS")"
read -r _ K6_CPU_MAX <<<"$(cpu_stat k6 1)"

# 자릿수를 맞춘다. 시간은 ms 정수, 메모리는 MB 정수, 처리량은 소수 1자리, CPU 는 % 정수.
# 다섯 명이 각자 소수점을 몇 자리까지 적을지 정하면 표가 안 읽힌다.
# NaN 은 그대로 둔다 — 0으로 바꾸면 "안 나온 값"과 "0이었던 값"이 구분되지 않는다.
round() {
  awk -v v="$1" -v d="$2" 'BEGIN { if (v == "NaN" || v == "") print "NaN"; else printf "%.*f\n", d, v }'
}

RPS="$(round "$RPS" 1)"
P95_MS="$(round "$P95_MS" 0)"
TOMCAT_BUSY_MAX="$(round "$TOMCAT_BUSY_MAX" 0)"
HIKARI_ACTIVE_MAX="$(round "$HIKARI_ACTIVE_MAX" 0)"
HIKARI_PENDING_MAX="$(round "$HIKARI_PENDING_MAX" 0)"
HEAP_MB_MAX="$(round "$HEAP_MB_MAX" 0)"
# 락 구간만 소수 첫째 자리까지 남긴다. 거절 경로가 1ms 안팎, 접수 경로가 5ms 안팎이라
# 정수로 반올림하면 1 과 5 만 남고 그 사이의 변화를 볼 수 없다.
LOCK_WAIT_P95_MS="$(round "$LOCK_WAIT_P95_MS" 1)"
LOCK_HOLD_P95_MS="$(round "$LOCK_HOLD_P95_MS" 1)"
LOCK_HOLD_ACC_P95_MS="$(round "$LOCK_HOLD_ACC_P95_MS" 1)"
LOCK_HOLD_REJ_P95_MS="$(round "$LOCK_HOLD_REJ_P95_MS" 1)"
BEFORE_LOCK_P95_MS="$(round "$BEFORE_LOCK_P95_MS" 1)"
COMMIT_P95_MS="$(round "$COMMIT_P95_MS" 1)"
CONN_ACQUIRE_P95_MS="$(round "$CONN_ACQUIRE_P95_MS" 0)"
CLOSE_DELAY_P95_MS="$(round "$CLOSE_DELAY_P95_MS" 0)"
SSE_HEARTBEAT_P95_MS="$(round "$SSE_HEARTBEAT_P95_MS" 0)"
SSE_BROADCAST_P95_MS="$(round "$SSE_BROADCAST_P95_MS" 0)"
SSE_CONN_MAX="$(round "$SSE_CONN_MAX" 0)"
CLOSE_DELAY_P50_MS="$(round "$CLOSE_DELAY_P50_MS" 0)"
CLOSE_DELAY_MAX_MS="$(round "$CLOSE_DELAY_MAX_MS" 0)"
CLOSE_DURATION_P95_MS="$(round "$CLOSE_DURATION_P95_MS" 0)"
CLOSE_FAILURES="$(round "$CLOSE_FAILURES" 0)"
SCHED_ACTIVE_MAX="$(round "$SCHED_ACTIVE_MAX" 0)"
SCHED_QUEUED_MAX="$(round "$SCHED_QUEUED_MAX" 0)"
GC_PAUSE_MS_PER_S="$(round "$GC_PAUSE_MS_PER_S" 1)"
K6_CPU_MAX="$(round "$K6_CPU_MAX" 0)"
# 앱·MySQL 은 소수 첫째 자리까지 남긴다. --cpus 를 올렸을 때 몇 % 로 내려갔는지가
# 이 실험의 답이라, 정수로 반올림하면 그 변화가 뭉개진다.
APP_CPU_AVG="$(round "$APP_CPU_AVG" 1)"
APP_CPU_MAX="$(round "$APP_CPU_MAX" 1)"
MYSQL_CPU_AVG="$(round "$MYSQL_CPU_AVG" 1)"
MYSQL_CPU_MAX="$(round "$MYSQL_CPU_MAX" 1)"
K6_P95_MS="$(round "$K6_P95_MS" 0)"

# 입찰 한 건이 SELECT 를 몇 번 했는지. Com_select 는 서버 전체 누적이라 배경 SSE 나 시딩까지
# 섞이지 않도록 k6 직전과 직후의 차이만 쓴다. 입찰이 하나도 안 붙은 실행은 나눌 수가 없다.
#
# 나누는 값이 접수 건수가 아니라 시도 건수여야 한다. 분자에는 거절된 입찰이 던진 SELECT 도
# 들어 있어서, 접수로 나누면 접수 비율(계단에 따라 1% 안팎까지 떨어진다)의 역수만큼 부풀고
# **VU 를 올리는 것만으로 이 값이 따라 올라간다.** 총 처리량에서 걷어낸 함정과 같은 것이다.
# ── MySQL 쪽에서 본 대기와 fsync ────────────────────────────────────
#
# 앱이 잰 값(upbid.bid.lock.wait, upbid.bid.commit)과 나란히 놓으려고 뽑는다. 앱은 "얼마나
# 걸렸나"만 알고 MySQL 안에서 무엇을 기다렸는지는 모른다.
#
# 행 락 카운터는 SHOW GLOBAL STATUS 라 누적이다. 구간 차이를 낸다.
# 측정 구간보다 대기 총합이 클 수 있다 — 여러 커넥션이 동시에 기다리면 합이 그렇게 된다.
row_lock_delta() {
  awk -v k="$1" 'FNR==NR { if ($1==k) b=$2; next } $1==k { print $2-b }' \
    "$RESULT_DIR/innodb_row_lock_before.txt" "$RESULT_DIR/innodb_row_lock_after.txt"
}
ROW_LOCK_WAITS="$(row_lock_delta Innodb_row_lock_waits)"
ROW_LOCK_TIME_MS="$(row_lock_delta Innodb_row_lock_time)"
ROW_LOCK_TIME_MAX_MS="$(awk '$1=="Innodb_row_lock_time_max"{print $2}' \
  "$RESULT_DIR/innodb_row_lock_after.txt")"

# I/O 한 번에 걸린 평균. 커밋이 느려졌을 때 그게 로그 파일 때문인지 여기서 갈린다.
# 타이머는 피코초라 1e9 로 나눠 ms 로 만든다.
#
# $2/$3 이 전체 I/O(COUNT_STAR, SUM_TIMER_WAIT), $4/$5 가 동기화만(COUNT_MISC, SUM_TIMER_MISC)이다.
io_avg_ms() {
  awk -v e="$1" -v cc="$2" -v tc="$3" '
    FNR==NR { if ($1==e) { c=$(cc); t=$(tc) } ; next }
    $1==e   { dc=$(cc)-c; if (dc>0) printf "%.3f", ($(tc)-t)/1e9/dc; else printf "NaN" }
  ' "$RESULT_DIR/mysql_io_before.txt" "$RESULT_DIR/mysql_io_after.txt"
}
io_count() {
  awk -v e="$1" -v cc="$2" 'FNR==NR { if ($1==e) c=$(cc); next } $1==e { print $(cc)-c }' \
    "$RESULT_DIR/mysql_io_before.txt" "$RESULT_DIR/mysql_io_after.txt"
}
REDO_IO_COUNT="$(io_count 'wait/io/file/innodb/innodb_log_file' 2)"
REDO_IO_AVG_MS="$(io_avg_ms 'wait/io/file/innodb/innodb_log_file' 2 3)"
REDO_FSYNC_COUNT="$(io_count 'wait/io/file/innodb/innodb_log_file' 4)"
REDO_FSYNC_AVG_MS="$(io_avg_ms 'wait/io/file/innodb/innodb_log_file' 4 5)"
BINLOG_IO_COUNT="$(io_count 'wait/io/file/sql/binlog' 2)"
BINLOG_IO_AVG_MS="$(io_avg_ms 'wait/io/file/sql/binlog' 2 3)"
BINLOG_FSYNC_COUNT="$(io_count 'wait/io/file/sql/binlog' 4)"
BINLOG_FSYNC_AVG_MS="$(io_avg_ms 'wait/io/file/sql/binlog' 4 5)"

# 게이지 표본의 평균과 최대. 표본이 없으면 NaN 으로 둔다 (실행이 짧으면 그럴 수 있다).
gauge_stat() {
  awk -v k="$1" '$1==k { n++; s+=$2; if ($2>m) m=$2 }
    END { if (n>0) printf "%.1f %d", s/n, m; else printf "NaN NaN" }' "$CONCURRENCY_LOG"
}
read -r THREADS_RUNNING_AVG THREADS_RUNNING_MAX <<<"$(gauge_stat Threads_running)"
read -r LOG_PENDING_AVG LOG_PENDING_MAX <<<"$(gauge_stat Innodb_os_log_pending_fsyncs)"
read -r ROW_LOCK_BLOCKED_AVG ROW_LOCK_BLOCKED_MAX <<<"$(gauge_stat Innodb_row_lock_current_waits)"

# 실제로 일하고 있던 수. Threads_running 에서 행 락에 막혀 있던 수를 뺀다.
# 커넥션을 쥐고 락을 기다리는 스레드는 MySQL 에 일을 안 시키므로 빼야 한다.
THREADS_WORKING_AVG="$(awk -v r="$THREADS_RUNNING_AVG" -v b="$ROW_LOCK_BLOCKED_AVG" \
  'BEGIN { if (r=="NaN" || b=="NaN") printf "NaN"; else printf "%.1f", r-b }')"

SELECT_PER_BID="$(awk -v before="$COM_SELECT_BEFORE" -v after="$COM_SELECT_AFTER" \
                      -v attempts="$BID_ATTEMPTS" \
  'BEGIN { if (attempts + 0 <= 0 || after == "" || before == "") print "NaN";
           else printf "%.1f\n", (after - before) / attempts }')"

# 그래프를 나중에 다시 그릴 수 있게 원본 시계열도 남긴다.
# 숫자 몇 개만 남기면 "이 계단은 왜 이렇지"를 되짚을 수 없다.
{
  echo "metric,timestamp,value"
  for series in \
    "sum(rate(http_server_requests_seconds_count{$NOT_ACTUATOR}[30s]))|throughput_req_per_s" \
    "tomcat_threads_busy_threads{$RUN}|tomcat_busy" \
    "hikaricp_connections_active{$RUN}|hikari_active" \
    "hikaricp_connections_pending{$RUN}|hikari_pending" \
    "sum(jvm_memory_used_bytes{$RUN, area=\"heap\"})|heap_bytes" \
    "histogram_quantile(0.95, sum by (le) (rate(upbid_bid_lock_wait_seconds_bucket{$RUN}[30s])))|lock_wait_p95_s" \
    "histogram_quantile(0.95, sum by (le) (rate(upbid_bid_lock_hold_seconds_bucket{$RUN}[30s])))|lock_hold_p95_s" \
    "upbid_sse_connections{$RUN}|sse_connections" \
    "histogram_quantile(0.95, sum by (le) (rate(upbid_auction_close_delay_seconds_bucket{$RUN}[30s])))|close_delay_p95_s"
  do
    q="${series%|*}"; label="${series##*|}"
    curl -sG "$PROM_URL/api/v1/query_range" \
      --data-urlencode "query=$q" \
      --data-urlencode "start=$WINDOW_START_EPOCH" \
      --data-urlencode "end=$WINDOW_END_EPOCH" \
      --data-urlencode "step=5" \
      | jq -r --arg m "$label" '.data.result[]?.values[]? | "\($m),\(.[0]),\(.[1])"'
  done
} >"$RESULT_DIR/metrics.csv"

# ── 8. 결과 저장 ───────────────────────────────────────────────────
STATUS="ok"
[ "$K6_EXIT" -ne 0 ] && STATUS="aborted"

# 증가분이 음수면 구간 앞뒤가 다른 컨테이너를 본 것이다. 그 줄은 표에 쓰면 안 된다.
if awk -v v="$RPS" 'BEGIN { exit !(v + 0 < 0) }' 2>/dev/null; then
  STATUS="aborted"
  echo "※ 처리량이 음수다($RPS). 구간 시작값으로 직전 실행 값을 집어온 것이라 이 줄은 못 쓴다." >&2
  echo "  meta.json 에 aborted 로 남긴다. 다시 돌린다." >&2
fi

jq -n \
  --arg run_id "$RUN_ID" --arg who "$WHO" --arg commit "$COMMIT" \
  --arg status "$STATUS" --arg start "$WINDOW_START" --arg end "$WINDOW_END" \
  --arg dirty "$([ -n "$DIRTY" ] && echo true || echo false)" \
  --argjson scenario "$SCENARIO" --argjson vus "$VUS" --argjson pool "$POOL" \
  --argjson items "$ITEMS" --argjson sse "$SSE" --argjson users "$USERS" \
  --argjson heartbeat_ms "$HEARTBEAT_MS" --argjson scheduler_pool "$SCHEDULER_POOL" \
  --arg virtual_threads "$VIRTUAL_THREADS" --arg xmx "$JAVA_OPTS" \
  --arg isolation "$ISOLATION" --arg sweep_index "$SWEEP_INDEX" \
  --argjson bulk_items "$BULK_ITEMS" \
  --argjson window_seconds "$WINDOW_SECONDS" \
  '{run_id:$run_id, scenario:$scenario, commit:$commit, dirty:($dirty=="true"), who:$who,
    params:{vus:$vus, pool_size:$pool, items:$items, sse:$sse, users:$users,
            heartbeat_ms:$heartbeat_ms, scheduler_pool:$scheduler_pool,
            virtual_threads:($virtual_threads=="true"), xmx:$xmx,
            isolation:$isolation, bulk_items:$bulk_items,
            sweep_index:($sweep_index=="on")},
    window:{start:$start, end:$end, seconds:$window_seconds},
    status:$status}' >"$RESULT_DIR/meta.json"

HEADER="run_id,who,commit,status,scenario,vus,pool,items,sse,throughput_req_per_s,accepted_per_s,p95_ms,k6_p95_ms,tomcat_busy_max,hikari_active_max,hikari_pending_max,conn_acquire_p95_ms,heap_mb_max,before_lock_p95_ms,lock_wait_p95_ms,lock_hold_p95_ms,lock_hold_acc_p95_ms,lock_hold_rej_p95_ms,commit_p95_ms,select_per_bid,isolation,gap_locks,close_delay_p50_ms,close_delay_p95_ms,close_delay_max_ms,close_duration_p95_ms,close_failures,sched_active_max,sched_queued_max,sse_heartbeat_p95_ms,sse_broadcast_p95_ms,sse_conn_max,gc_pause_ms_per_s,k6_cpu_max,cpus,app_cpu_avg,app_cpu_max,mysql_cpu_avg,mysql_cpu_max,virtual_threads,bulk_items,sweep_index,accepted,rejected_4xx,concurrent_conflict,failed_5xx,bottleneck,note"
INDEX="$PERF_DIR/results/index.csv"

# 헤더는 파일이 없을 때만 쓴다. 그래서 헤더가 바뀐 뒤에도 낡은 파일이 남아 있으면 새 줄이
# 한 칸씩 밀려 들어가고, 콘솔 결과 표도 헤더 순서대로 읽어서 엉뚱한 이름이 붙는다.
#
# 칸 수가 아니라 헤더 줄 전체를 비교한다. 칸 수는 그대로 두고 이름만 바꾸는 경우(rps →
# throughput_req_per_s)가 실제로 있었는데, 칸 수만 보면 그걸 못 잡아서 콘솔이 그 칸을
# 아예 못 찾고 표에서 조용히 빠졌다.
if [ -f "$INDEX" ]; then
  ACTUAL_HEADER="$(head -1 "$INDEX")"

  if [ "$ACTUAL_HEADER" != "$HEADER" ]; then
    BACKUP="$INDEX.$(date +%Y%m%d-%H%M%S).bak"
    mv "$INDEX" "$BACKUP"
    echo "※ index.csv 의 헤더가 달라져서 옛 파일을 옆으로 옮겼다 (지우지는 않았다):" >&2
    echo "  $(basename "$BACKUP")" >&2
  fi
fi

if [ ! -f "$INDEX" ]; then
  echo "$HEADER" >"$INDEX"
fi

# bottleneck 과 note 는 비워 둔다. 사람이 채우는 칸이 이 둘뿐이다.
#
# status 를 여기 넣는 이유. 예전에는 aborted 실행도 정상 줄과 똑같은 모양으로 들어갔다.
# k6 가 중간에 죽으면 summary.json 이 없어서 접수와 거절이 전부 0 인데 처리량은 그럴듯한
# 숫자가 박혀서, 그 줄만 봐서는 아무도 못 알아본다. 실측으로 겪었다 — 구간 128초짜리
# aborted 줄에 처리량 3490.7 이 들어갔고 접수는 0 이었다.
printf '%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,,\n' \
  "$RUN_ID" "$WHO" "$COMMIT" "$STATUS" "$SCENARIO" "$VUS" "$POOL" "$ITEMS" "$SSE" \
  "$RPS" "$ACCEPTED_PER_S" "$P95_MS" "$K6_P95_MS" "$TOMCAT_BUSY_MAX" "$HIKARI_ACTIVE_MAX" "$HIKARI_PENDING_MAX" \
  "$CONN_ACQUIRE_P95_MS" "$HEAP_MB_MAX" \
  "$BEFORE_LOCK_P95_MS" "$LOCK_WAIT_P95_MS" "$LOCK_HOLD_P95_MS" \
  "$LOCK_HOLD_ACC_P95_MS" "$LOCK_HOLD_REJ_P95_MS" "$COMMIT_P95_MS" \
  "$SELECT_PER_BID" "$ISOLATION" "$GAP_LOCKS" \
  "$CLOSE_DELAY_P50_MS" "$CLOSE_DELAY_P95_MS" "$CLOSE_DELAY_MAX_MS" \
  "$CLOSE_DURATION_P95_MS" "$CLOSE_FAILURES" "$SCHED_ACTIVE_MAX" "$SCHED_QUEUED_MAX" \
  "$SSE_HEARTBEAT_P95_MS" "$SSE_BROADCAST_P95_MS" "$SSE_CONN_MAX" \
  "$GC_PAUSE_MS_PER_S" "$K6_CPU_MAX" \
  "$CPUS" "$APP_CPU_AVG" "$APP_CPU_MAX" "$MYSQL_CPU_AVG" "$MYSQL_CPU_MAX" "$VIRTUAL_THREADS" "$BULK_ITEMS" "$SWEEP_INDEX" \
  "$ACCEPTED" "$REJECTED_4XX" "$CONCURRENT_CONFLICT" "$FAILED_5XX" \
  >>"$INDEX"

# var-run 을 박아야 이 실행의 시계열만 보인다. 안 붙이면 대시보드가 여태 돌린 실행을
# 전부 겹쳐 그려서, 계단 하나를 보려는데 다른 계단이 같이 나온다.
GRAFANA_LINK="$GRAFANA_URL/d/upbid-perf?var-run=$RUN_ID&from=${WINDOW_START_EPOCH}000&to=${WINDOW_END_EPOCH}000"

# 가상 스레드는 전역 스위치라 톰캣도 함께 바뀐다. 이 단서를 안 적으면 나중에 이 숫자를
# 스케줄러 근거로 잘못 쓴다. 스케줄러가 SimpleAsyncTaskScheduler 로 바뀌어 풀도 큐도
# 없어지므로 sched_* 열은 NaN 이 되는데, 그것도 정상이라고 적어 둔다.
VIRTUAL_THREADS_NOTE=""
if [ "$VIRTUAL_THREADS" = "true" ]; then
  VIRTUAL_THREADS_NOTE="> **가상 스레드를 켜고 잰 숫자다.** 스케줄러뿐 아니라 톰캣도 함께 바뀌었으므로
> \"스케줄러에 가상 스레드를 쓴 효과\"가 아니라 \"앱 전체에 쓴 효과\"로 읽는다.
> 스케줄러 일꾼/대기열이 NaN 인 것도 정상이다 (풀도 큐도 없는 구현으로 바뀐다).

"
fi

cat >"$RESULT_DIR/note.md" <<EOF
# $RUN_ID

숫자는 run.sh 가 채웠습니다. 여기서 사람이 쓰는 건 아래 판정 칸뿐입니다.
그래프는 $GRAFANA_LINK 를 열어 grafana.png 로 이 폴더에 저장합니다.

| | |
|---|---|
| 처리량 (주인공 요청만) | ${RPS} req/s |
| **접수 처리량** | **${ACCEPTED_PER_S} 건/s** ← 계단 비교는 이 값으로 |
| p95 (서버 / k6) | ${P95_MS} / ${K6_P95_MS} ms |
| 톰캣 스레드 max | ${TOMCAT_BUSY_MAX} |
| 커넥션 active / pending max | ${HIKARI_ACTIVE_MAX} / ${HIKARI_PENDING_MAX} |
| 커넥션 획득 p95 | ${CONN_ACQUIRE_P95_MS} ms |
| 힙 max | ${HEAP_MB_MAX} MB |
| **T2** 락 앞 (커넥션 획득 + SELECT 3개) p95 | ${BEFORE_LOCK_P95_MS} ms |
| **T3** 락 대기 p95 | ${LOCK_WAIT_P95_MS} ms |
| **T4** 락 유지 p95 (커밋까지) — 전체 | ${LOCK_HOLD_P95_MS} ms |
| &nbsp;&nbsp;└ 접수 경로 | ${LOCK_HOLD_ACC_P95_MS} ms |
| &nbsp;&nbsp;└ 거절 경로 | ${LOCK_HOLD_REJ_P95_MS} ms |
| &nbsp;&nbsp;└ 그중 커밋 (접수만) | ${COMMIT_P95_MS} ms |
| **MySQL — 동시 작업** \`Threads_running\` 평균 / 최대 | ${THREADS_RUNNING_AVG} / ${THREADS_RUNNING_MAX} |
| &nbsp;&nbsp;└ 그중 행 락에 막힌 수 \`Innodb_row_lock_current_waits\` 평균 / 최대 | ${ROW_LOCK_BLOCKED_AVG} / ${ROW_LOCK_BLOCKED_MAX} |
| &nbsp;&nbsp;└ **실제로 일한 수** (위 둘의 차) | **${THREADS_WORKING_AVG}** |
| **MySQL — 대기** 행 락 대기 횟수 | ${ROW_LOCK_WAITS} 회 |
| &nbsp;&nbsp;└ 행 락 대기 총 시간 | ${ROW_LOCK_TIME_MS} ms |
| &nbsp;&nbsp;└ 행 락 대기 최대 | ${ROW_LOCK_TIME_MAX_MS} ms |
| **MySQL — 커밋** redo 로그 I/O 전체 횟수 / 평균 | ${REDO_IO_COUNT} / ${REDO_IO_AVG_MS} ms |
| &nbsp;&nbsp;└ 그중 fsync 횟수 / 평균 | ${REDO_FSYNC_COUNT} / ${REDO_FSYNC_AVG_MS} ms |
| &nbsp;&nbsp;└ binlog I/O 전체 횟수 / 평균 | ${BINLOG_IO_COUNT} / ${BINLOG_IO_AVG_MS} ms |
| &nbsp;&nbsp;└ 그중 fsync 횟수 / 평균 | ${BINLOG_FSYNC_COUNT} / ${BINLOG_FSYNC_AVG_MS} ms |
| &nbsp;&nbsp;└ fsync 대기 \`Innodb_os_log_pending_fsyncs\` 평균 / 최대 | ${LOG_PENDING_AVG} / ${LOG_PENDING_MAX} |
| 입찰 한 건당 SELECT | ${SELECT_PER_BID} 회 |
| 격리 수준 / 갭 락 표본 | ${ISOLATION} / ${GAP_LOCKS} |
| 마감 지연 p50 / p95 / 최대 | ${CLOSE_DELAY_P50_MS} / ${CLOSE_DELAY_P95_MS} / ${CLOSE_DELAY_MAX_MS} ms |
| 마감 소요 p95 (락 대기 포함) | ${CLOSE_DURATION_P95_MS} ms |
| 마감 실패 | ${CLOSE_FAILURES} 건 |
| 스케줄러 일꾼 / 대기열 max | ${SCHED_ACTIVE_MAX} / ${SCHED_QUEUED_MAX} |
| SSE 접속 max | ${SSE_CONN_MAX} |
| SSE heartbeat p95 | ${SSE_HEARTBEAT_P95_MS} ms |
| SSE broadcast p95 | ${SSE_BROADCAST_P95_MS} ms |
| k6 CPU max | ${K6_CPU_MAX} % |
| 앱 CPU (준 ${CPUS} 코어 기준) | 평균 ${APP_CPU_AVG} % / 최대 ${APP_CPU_MAX} % |
| MySQL CPU (준 ${CPUS} 코어 기준) | 평균 ${MYSQL_CPU_AVG} % / 최대 ${MYSQL_CPU_MAX} % |
| 접수 (201) | ${ACCEPTED} |
| 거절 — 금액 규칙 (7004·7005) | ${REJECTED_AMOUNT} |
| 거절 — 이미 최고 입찰자 (7003) | ${REJECTED_ALREADY_TOP} |
| 거절 — 마감·미진행 (7001·7002) | ${REJECTED_CLOSED} |
| **경합 충돌 (7006)** | **${CONCURRENT_CONFLICT}** ← 0이 정상. 0보다 크면 그게 발견이다 |
| 그 밖의 거절 (401·403 등) | ${REJECTED_OTHER} ← 0이어야 한다. 크면 세팅이 잘못된 것 |
| 실패 (5xx·타임아웃) | ${FAILED_5XX} |

${VIRTUAL_THREADS_NOTE}## 판정

판정:  Y / N / ?  —
       (직전 계단 대비 처리량이 몇 배인지, 그때 자원 넷이 어땠는지)
       →

## 확인 실험 (있으면. 반드시 before/after 쌍으로)

가설:
바꾼 값:   (이것 하나만)
before:
after:
결론:
EOF

# 배경 SSE 를 붙였으면 여기서 끊는다. 안 그러면 다음 실행의 앱에 그대로 따라붙는다.
[ "$SSE" -gt 0 ] && cleanup_oneoff

echo
echo "═══ 끝 ═══"
echo "결과   $RESULT_DIR"
echo "그래프 $GRAFANA_LINK  ← 열어서 grafana.png 로 저장"
echo "표     $INDEX"
echo
printf '처리량 %s req/s   p95 %s ms\n' "$RPS" "$P95_MS"
printf '락앞 p95 %s ms → 락대기 p95 %s ms → 락유지 p95 %s ms (접수 %s / 거절 %s, 커밋 %s)\n' \
  "$BEFORE_LOCK_P95_MS" "$LOCK_WAIT_P95_MS" "$LOCK_HOLD_P95_MS" \
  "$LOCK_HOLD_ACC_P95_MS" "$LOCK_HOLD_REJ_P95_MS" "$COMMIT_P95_MS"
printf '입찰당 SELECT %s 회   격리 %s   갭 락 표본 %s\n' \
  "$SELECT_PER_BID" "$ISOLATION" "$GAP_LOCKS"
printf '스레드 %s   커넥션 %s active / %s pending (획득 p95 %s ms)   힙 %s MB   k6 CPU %s%%\n' \
  "$TOMCAT_BUSY_MAX" "$HIKARI_ACTIVE_MAX" "$HIKARI_PENDING_MAX" "$CONN_ACQUIRE_P95_MS" "$HEAP_MB_MAX" "$K6_CPU_MAX"
printf 'CPU %s 코어   앱 평균 %s%% 최대 %s%%   MySQL 평균 %s%% 최대 %s%%\n' \
  "$CPUS" "$APP_CPU_AVG" "$APP_CPU_MAX" "$MYSQL_CPU_AVG" "$MYSQL_CPU_MAX"
printf 'SSE 접속 max %s   마감 지연 p95 %s ms\n' "$SSE_CONN_MAX" "$CLOSE_DELAY_P95_MS"
echo
printf '접수 %s   경합충돌(7006) %s   그밖의거절 %s   실패5xx %s\n' \
  "$ACCEPTED" "$CONCURRENT_CONFLICT" "$REJECTED_OTHER" "$FAILED_5XX"
echo

# 이 두 줄이 오늘 밤에 실제로 걸렸던 함정을 잡는 안전망이다.
# 세션이 안 붙으면 입찰이 전부 401 로 거절되는데, 401 도 4xx 라 "정상 거절"로 세어져서
# 그래프만 봐서는 안 드러난다. 락 대기가 NaN 인 걸 보고서야 알게 된다.
if [ "$REJECTED_OTHER" != "0" ] && [ "$SCENARIO" != "0" ] && [ "$SCENARIO" != "3" ]; then
  echo "※ 경고: 입찰이 401·403 으로 거절된 게 ${REJECTED_OTHER}건이다. 세션이나 약관 동의가 안 붙은 것이라" >&2
  echo "  이 줄의 숫자는 서버 한계가 아니다. 폴더는 남기되 표에는 쓰지 않는다." >&2
fi

if [ "$CONCURRENT_CONFLICT" != "0" ]; then
  echo "※ CONCURRENT_BID_CONFLICT 가 ${CONCURRENT_CONFLICT}건 나왔다. 행 락이 있으면 안 나오는 게 정상이라 그 자체가 발견이다." >&2
fi

# 붙여넣을 줄은 성공한 실행에서만 찍는다.
#
# 예전에는 aborted 여도 줄을 찍고 그 아래에 경고를 붙였는데, 경고가 줄 뒤에 오니까 이미
# 복사한 뒤였다. 다섯 명이 각자 수십 번 돌리면 그 한 번이 조용히 노션에 들어간다.
if [ "$STATUS" = "aborted" ]; then
  echo "※ 이 실행은 aborted 다. k6 가 중간에 끝났거나 구간 앞뒤가 다른 컨테이너를 봤다." >&2
  echo "  index.csv 에는 status=aborted 로 남겼다. **이 줄은 노션에 붙여넣지 않는다.**" >&2
  echo "  폴더는 지우지 않는다 (나중에 '이 계단은 왜 비었지'가 되지 않게)." >&2
  echo "  같은 조건으로 다시 돌린다: $RESULT_DIR" >&2
  exit 0
fi

echo "note.md 의 판정 칸을 채우고, 아래 한 줄을 노션 측정 결과 표에 붙여 넣는다."
echo "(결과 폴더는 커밋되지 않는다. 숫자를 합치는 곳은 노션이다.)"
echo
tail -1 "$INDEX"
exit 0
