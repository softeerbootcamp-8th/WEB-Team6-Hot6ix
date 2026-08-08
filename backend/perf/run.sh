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
    --duration) DURATION="$2"; shift 2 ;;
    --warmup) WARMUP="$2"; shift 2 ;;
    --who) WHO="$2"; shift 2 ;;
    --skip-build) SKIP_BUILD=1; shift ;;
    --port) PERF_HTTP_PORT="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "모르는 옵션: $1" >&2; usage; exit 1 ;;
  esac
done

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
CPU_SAMPLER=""
cleanup() {
  # || true 가 없으면 이미 죽은 샘플러에 kill 이 실패하고, set -e 가 그걸 잡아서
  # 정상 종료인데도 종료 코드가 1 로 나간다 (실측으로 확인).
  if [ -n "$CPU_SAMPLER" ]; then
    kill "$CPU_SAMPLER" 2>/dev/null || true
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
STAMP="$(date +%Y-%m-%dT%H-%M)"
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
  "${COMPOSE[@]}" exec -T mysql \
    mysql -uroot -p1234 -N -B -e "SHOW GLOBAL STATUS LIKE 'Innodb_row_lock%'" 2>/dev/null || true
}
row_lock_status >"$RESULT_DIR/innodb_row_lock_before.txt"

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

# k6 컨테이너 CPU 를 따로 샘플링한다. k6 는 자기 CPU 를 지표로 안 내보내서,
# "여기부턴 부하 발생기가 병목"을 판정하려면 밖에서 봐야 한다.
CPU_LOG="$RESULT_DIR/k6_cpu.txt"
: >"$CPU_LOG"
(
  while true; do
    docker stats --no-stream --format '{{.Name}} {{.CPUPerc}}' 2>/dev/null \
      | awk -v skip="$SSE_CONTAINER" \
          'tolower($1) ~ /k6/ && (skip == "" || $1 != skip) { gsub(/%/, "", $2); print $2 }' \
      >>"$CPU_LOG" || true
    sleep 5
  done
) &
CPU_SAMPLER=$!
# 잡 제어에서 떼어낸다. 안 그러면 끝낼 때 셸이 "Terminated" 를 뱉어서 결과 출력이 지저분해진다.
disown "$CPU_SAMPLER" 2>/dev/null || true

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

WINDOW_END="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
WINDOW_END_EPOCH="$(date +%s)"
WINDOW_SECONDS=$((WINDOW_END_EPOCH - WINDOW_START_EPOCH))

row_lock_status >"$RESULT_DIR/innodb_row_lock_after.txt"

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

RPS="$(promq "sum($(delta "http_server_requests_seconds_count{$NOT_ACTUATOR}")) / $WINDOW_SECONDS")"
P95_MS="$(p95_of "http_server_requests_seconds_bucket{$NOT_ACTUATOR}")"
TOMCAT_BUSY_MAX="$(promq "max(max_over_time(tomcat_threads_busy_threads{$RUN}[$W]))")"
HIKARI_ACTIVE_MAX="$(promq "max(max_over_time(hikaricp_connections_active{$RUN}[$W]))")"
HIKARI_PENDING_MAX="$(promq "max(max_over_time(hikaricp_connections_pending{$RUN}[$W]))")"
HEAP_MB_MAX="$(promq "max(max_over_time(sum(jvm_memory_used_bytes{$RUN, area=\"heap\"})[$W:5s])) / 1024 / 1024")"
LOCK_WAIT_P95_MS="$(p95_of "upbid_bid_lock_wait_seconds_bucket{$RUN}")"
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

K6_CPU_MAX="$(sort -g "$CPU_LOG" 2>/dev/null | tail -1)"
K6_CPU_MAX="${K6_CPU_MAX:-NaN}"

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
LOCK_WAIT_P95_MS="$(round "$LOCK_WAIT_P95_MS" 0)"
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
  --argjson window_seconds "$WINDOW_SECONDS" \
  '{run_id:$run_id, scenario:$scenario, commit:$commit, dirty:($dirty=="true"), who:$who,
    params:{vus:$vus, pool_size:$pool, items:$items, sse:$sse, users:$users,
            heartbeat_ms:$heartbeat_ms, scheduler_pool:$scheduler_pool,
            virtual_threads:($virtual_threads=="true"), xmx:$xmx},
    window:{start:$start, end:$end, seconds:$window_seconds},
    status:$status}' >"$RESULT_DIR/meta.json"

HEADER="run_id,who,commit,scenario,vus,pool,items,sse,throughput_req_per_s,p95_ms,tomcat_busy_max,hikari_active_max,hikari_pending_max,conn_acquire_p95_ms,heap_mb_max,lock_wait_p95_ms,close_delay_p50_ms,close_delay_p95_ms,close_delay_max_ms,close_duration_p95_ms,close_failures,sched_active_max,sched_queued_max,sse_heartbeat_p95_ms,sse_broadcast_p95_ms,sse_conn_max,gc_pause_ms_per_s,k6_cpu_max,virtual_threads,accepted,rejected_4xx,concurrent_conflict,failed_5xx,bottleneck,note"
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
printf '%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,,\n' \
  "$RUN_ID" "$WHO" "$COMMIT" "$SCENARIO" "$VUS" "$POOL" "$ITEMS" "$SSE" \
  "$RPS" "$P95_MS" "$TOMCAT_BUSY_MAX" "$HIKARI_ACTIVE_MAX" "$HIKARI_PENDING_MAX" \
  "$CONN_ACQUIRE_P95_MS" "$HEAP_MB_MAX" "$LOCK_WAIT_P95_MS" \
  "$CLOSE_DELAY_P50_MS" "$CLOSE_DELAY_P95_MS" "$CLOSE_DELAY_MAX_MS" \
  "$CLOSE_DURATION_P95_MS" "$CLOSE_FAILURES" "$SCHED_ACTIVE_MAX" "$SCHED_QUEUED_MAX" \
  "$SSE_HEARTBEAT_P95_MS" "$SSE_BROADCAST_P95_MS" "$SSE_CONN_MAX" \
  "$GC_PAUSE_MS_PER_S" "$K6_CPU_MAX" "$VIRTUAL_THREADS" \
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
| 처리량 | ${RPS} req/s |
| p95 | ${P95_MS} ms |
| 톰캣 스레드 max | ${TOMCAT_BUSY_MAX} |
| 커넥션 active / pending max | ${HIKARI_ACTIVE_MAX} / ${HIKARI_PENDING_MAX} |
| 커넥션 획득 p95 | ${CONN_ACQUIRE_P95_MS} ms |
| 힙 max | ${HEAP_MB_MAX} MB |
| 락 대기 p95 | ${LOCK_WAIT_P95_MS} ms |
| 마감 지연 p50 / p95 / 최대 | ${CLOSE_DELAY_P50_MS} / ${CLOSE_DELAY_P95_MS} / ${CLOSE_DELAY_MAX_MS} ms |
| 마감 소요 p95 (락 대기 포함) | ${CLOSE_DURATION_P95_MS} ms |
| 마감 실패 | ${CLOSE_FAILURES} 건 |
| 스케줄러 일꾼 / 대기열 max | ${SCHED_ACTIVE_MAX} / ${SCHED_QUEUED_MAX} |
| SSE 접속 max | ${SSE_CONN_MAX} |
| SSE heartbeat p95 | ${SSE_HEARTBEAT_P95_MS} ms |
| SSE broadcast p95 | ${SSE_BROADCAST_P95_MS} ms |
| k6 CPU max | ${K6_CPU_MAX} % |
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
printf '처리량 %s req/s   p95 %s ms   락대기 p95 %s ms\n' "$RPS" "$P95_MS" "$LOCK_WAIT_P95_MS"
printf '스레드 %s   커넥션 %s active / %s pending (획득 p95 %s ms)   힙 %s MB   k6 CPU %s%%\n' \
  "$TOMCAT_BUSY_MAX" "$HIKARI_ACTIVE_MAX" "$HIKARI_PENDING_MAX" "$CONN_ACQUIRE_P95_MS" "$HEAP_MB_MAX" "$K6_CPU_MAX"
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

echo "note.md 의 판정 칸을 채우고, 아래 한 줄을 노션 측정 결과 표에 붙여 넣는다."
echo "(결과 폴더는 커밋되지 않는다. 숫자를 합치는 곳은 노션이다.)"
echo
tail -1 "$INDEX"

[ "$STATUS" = "aborted" ] && echo "※ k6 가 실패로 끝났다. meta.json 에 aborted 로 남겼다. 폴더를 지우지 않는다." >&2
exit 0
