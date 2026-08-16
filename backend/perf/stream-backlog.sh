#!/usr/bin/env bash
# 실제 입찰 API로 Redis Stream backlog를 만든 뒤 Consumer drain 처리량을 측정한다.
# 로컬 전용이며, 직접 XADD하거나 운영 서버의 Consumer를 제어하지 않는다.

set -euo pipefail

PERF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "$PERF_DIR/.." && pwd)"
PROJECT_NAME="upbid-stream-drain"
COMPOSE=(docker compose -p "$PROJECT_NAME" -f "$PERF_DIR/docker-compose.perf.yml")

VUS=40
RATE=50
DURATION=20s
ITEMS=3
SSE=0
TARGET_BACKLOG=500
USERS=80
STREAM_DRAIN_TIMEOUT_SECONDS="${STREAM_DRAIN_TIMEOUT_SECONDS:-180}"
MAX_BUILD_ROUNDS=5
START_PRICE=10000
BID_UNIT=1000

PERF_HTTP_PORT="${PERF_HTTP_PORT:-18180}"
PERF_PROM_PORT="${PERF_PROM_PORT:-19190}"
PERF_GRAFANA_PORT="${PERF_GRAFANA_PORT:-13100}"
APP_URL="http://127.0.0.1:$PERF_HTTP_PORT"
PROM_URL="http://127.0.0.1:$PERF_PROM_PORT"

usage() {
  cat <<'USAGE'
사용법: stream-backlog.sh [옵션]

  --vus N              k6 VU 수 (기본 40)
  --rate N             초당 입찰 시도 수 (기본 50)
  --duration D         backlog 생성 라운드 길이 (기본 20s)
  --items N            진행 물품 수, 방당 최대 3개로 분산 (기본 3)
  --sse N              drain 전에 연결할 SSE 수 (기본 0)
  --target-backlog N   drain 전에 만들 최소 Stream 길이 (기본 500)
  --users N            시딩할 입찰자 수 (기본 80, VU 이상이어야 함)
  --timeout N          drain 제한시간 초 (기본 180)

이 도구는 로컬 Compose만 조작한다. --base 같은 원격 실행 옵션은 제공하지 않는다.
USAGE
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --vus) VUS="$2"; shift 2 ;;
    --rate) RATE="$2"; shift 2 ;;
    --duration) DURATION="$2"; shift 2 ;;
    --items) ITEMS="$2"; shift 2 ;;
    --sse) SSE="$2"; shift 2 ;;
    --target-backlog) TARGET_BACKLOG="$2"; shift 2 ;;
    --users) USERS="$2"; shift 2 ;;
    --timeout) STREAM_DRAIN_TIMEOUT_SECONDS="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "지원하지 않는 로컬 측정 옵션: $1" >&2; usage; exit 1 ;;
  esac
done

for command in docker jq curl; do
  command -v "$command" >/dev/null || {
    echo "$command 명령이 필요합니다." >&2
    exit 1
  }
done

for number in "$VUS" "$RATE" "$ITEMS" "$TARGET_BACKLOG" "$USERS"; do
  [ "$number" -gt 0 ] 2>/dev/null || {
    echo "VU, rate, items, target-backlog, users는 양의 정수여야 합니다." >&2
    exit 1
  }
done
[ "$USERS" -ge "$VUS" ] || {
  echo "--users는 --vus 이상이어야 합니다." >&2
  exit 1
}

RUN_ID="stream-drain-$(date +%Y%m%d-%H%M%S)"
RESULT_DIR="$PERF_DIR/results/$RUN_ID"
SEED_ENV="$RESULT_DIR/seed.env"
mkdir -p "$RESULT_DIR"

export PERF_HTTP_PORT PERF_PROM_PORT PERF_GRAFANA_PORT
export PERF_RUN_ID="$RUN_ID"
export MAX_IN_PROGRESS_PER_ROOM=3

cleanup() {
  "${COMPOSE[@]}" down --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

wait_for_health() {
  local ready=0
  for _ in $(seq 1 90); do
    if curl -fsS "$APP_URL/actuator/health" >/dev/null 2>&1; then
      ready=1
      break
    fi
    sleep 2
  done
  [ "$ready" -eq 1 ] || {
    echo "로컬 perf 앱이 준비되지 않았습니다." >&2
    "${COMPOSE[@]}" logs --tail 80 app >&2 || true
    exit 1
  }
}

stream_length() {
  "${COMPOSE[@]}" exec -T redis redis-cli XLEN auction:bid:stream \
    | tr -d '\r[:space:]'
}

promq() {
  local query="$1"
  curl -fsSG "$PROM_URL/api/v1/query" --data-urlencode "query=$query" \
    | jq -r '.data.result[0].value[1] // "NaN"' 2>/dev/null || echo "NaN"
}

echo "[1/7] 격리된 로컬 Compose 초기화"
cleanup

echo "[2/7] 애플리케이션 jar 빌드"
(cd "$BACKEND_DIR" && ./gradlew --no-daemon -q bootJar)
find "$BACKEND_DIR/build/libs" -name '*.jar' ! -name '*-plain.jar' \
  -exec cp {} "$BACKEND_DIR/app.jar" \;
[ -f "$BACKEND_DIR/app.jar" ] || {
  echo "app.jar를 만들지 못했습니다." >&2
  exit 1
}

echo "[3/7] Consumer OFF로 로컬 perf 스택 기동"
BID_STREAM_CONSUMER_ENABLED=false "${COMPOSE[@]}" up -d --build nginx prometheus
wait_for_health

echo "[4/7] 실제 경매 데이터 시딩"
BASE_URL="$APP_URL/api/v1" "$PERF_DIR/seed.sh" \
  --users "$USERS" --items "$ITEMS" --per-room 3 \
  --start-price "$START_PRICE" --unit "$BID_UNIT" \
  --start all --out "$SEED_ENV"
# shellcheck source=/dev/null
. "$SEED_ENV"

if [ "$SSE" -gt 0 ] 2>/dev/null; then
  echo "      SSE 연결 $SSE개 준비"
  SSE_CLIENT_BASE_URL="http://nginx/api/v1" \
  SSE_CLIENT_RUN_ID="$RUN_ID" \
  SSE_CLIENT_SHARE_CODES="$SHARE_CODES" \
  SSE_CLIENT_CONNECTIONS="$SSE" \
  SSE_CLIENT_RAMP_SECONDS=10 \
    "${COMPOSE[@]}" up -d sse-client
  sleep 12
fi

echo "[5/7] 실제 Lua 승인 이벤트로 backlog 생성"
BACKLOG_EPOCH_MS="$(( $(date +%s) * 1000 ))"
round=1
backlog=0
while [ "$round" -le "$MAX_BUILD_ROUNDS" ]; do
  K6_RUN_ID="$RUN_ID-build-$round"
  mkdir -p "$PERF_DIR/results/$K6_RUN_ID"

  set +e
  VUS="$VUS" RATE="$RATE" DURATION="$DURATION" RUN_ID="$K6_RUN_ID" \
  SHARE_CODE="$SHARE_CODE" SHARE_CODES="$SHARE_CODES" \
  ITEM_IDS="$ITEM_IDS" ROOM_ITEM_IDS="$ROOM_ITEM_IDS" \
  START_PRICE="$START_PRICE" BID_UNIT="$BID_UNIT" \
  BACKLOG_EPOCH_MS="$BACKLOG_EPOCH_MS" \
    "${COMPOSE[@]}" run --rm \
      --user "$(id -u):$(id -g)" \
      -e VUS -e RATE -e DURATION -e RUN_ID \
      -e SHARE_CODE -e SHARE_CODES -e ITEM_IDS -e ROOM_ITEM_IDS \
      -e START_PRICE -e BID_UNIT -e BACKLOG_EPOCH_MS \
      k6 run /scripts/stream-backlog.js \
      2>&1 | tee "$RESULT_DIR/build-$round.log"
  k6_exit="${PIPESTATUS[0]}"
  set -e

  [ "$k6_exit" -eq 0 ] || {
    echo "k6 backlog 생성이 실패했습니다 (exit=$k6_exit)." >&2
    exit 1
  }

  backlog="$(stream_length)"
  echo "      round=$round, XLEN=$backlog / 목표=$TARGET_BACKLOG"
  [ "$backlog" -ge "$TARGET_BACKLOG" ] && break
  round=$((round + 1))
done

[ "$backlog" -ge "$TARGET_BACKLOG" ] || {
  echo "최대 $MAX_BUILD_ROUNDS 라운드 안에 목표 backlog를 만들지 못했습니다." >&2
  exit 1
}

backlog_start="$backlog"
drain_started_at="$(date +%s)"

echo "[6/7] Consumer ON으로 앱만 재생성하고 drain"
BID_STREAM_CONSUMER_ENABLED=true "${COMPOSE[@]}" \
  up -d --no-deps --force-recreate app
wait_for_health

deadline=$((drain_started_at + STREAM_DRAIN_TIMEOUT_SECONDS))
timed_out=false
while true; do
  backlog_end="$(stream_length)"
  [ "$backlog_end" -eq 0 ] && break
  if [ "$(date +%s)" -ge "$deadline" ]; then
    timed_out=true
    break
  fi
  sleep 1
done
processed=$((backlog_start - backlog_end))

# 새 앱의 누적 histogram은 이 drain만 포함한다. 마지막 scrape가 완료된 뒤 cumulative bucket으로
# 계산하면 drain이 5초보다 짧아도 rate() 표본 부족으로 p95가 사라지지 않는다.
sleep 7
RUN_SELECTOR="run=\"$RUN_ID\""
drain_active_seconds="$(promq "sum(upbid_bid_stream_drain_duration_seconds_sum{$RUN_SELECTOR})")"
drain_tick_count="$(promq "sum(upbid_bid_stream_drain_duration_seconds_count{$RUN_SELECTOR})")"
lag_p95_ms="$(promq "histogram_quantile(0.95, sum by (le) (upbid_bid_stream_delivery_lag_milliseconds_bucket{$RUN_SELECTOR}))")"
persist_p95_seconds="$(promq "histogram_quantile(0.95, sum by (le) (upbid_bid_stream_persist_seconds_bucket{$RUN_SELECTOR}))")"
drain_records_p95="$(promq "histogram_quantile(0.95, sum by (le) (upbid_bid_stream_drain_records_bucket{$RUN_SELECTOR}))")"
drain_duration_p95_seconds="$(promq "histogram_quantile(0.95, sum by (le) (upbid_bid_stream_drain_duration_seconds_bucket{$RUN_SELECTOR}))")"

# 실제 처리 tick의 누적 실행시간에 tick 사이 fixedDelay(기본 100ms)만 더한다. 앱 부팅시간을
# 포함한 외부 wall clock으로 나누면 Consumer가 아니라 Spring 부팅 속도를 재게 된다.
drain_seconds="$(awk -v active="$drain_active_seconds" -v ticks="$drain_tick_count" \
  'BEGIN {
    if (active == "NaN" || ticks == "NaN" || ticks + 0 < 1) print 1;
    else printf "%.6f", active + ((ticks - 1) * 0.1)
  }')"
processed_per_second="$(awk -v count="$processed" -v seconds="$drain_seconds" \
  'BEGIN { printf "%.3f", count / seconds }')"

persist_p95_ms="$(awk -v value="$persist_p95_seconds" \
  'BEGIN { if (value == "NaN") print "NaN"; else printf "%.3f", value * 1000 }')"
drain_duration_p95_ms="$(awk -v value="$drain_duration_p95_seconds" \
  'BEGIN { if (value == "NaN") print "NaN"; else printf "%.3f", value * 1000 }')"

RESULT_FILE="$RESULT_DIR/stream-drain.json"
jq -n \
  --argjson backlog_start "$backlog_start" \
  --argjson backlog_end "$backlog_end" \
  --argjson drain_seconds "$drain_seconds" \
  --argjson processed "$processed" \
  --argjson processed_per_second "$processed_per_second" \
  --arg lag_p95_ms "$lag_p95_ms" \
  --arg persist_p95_ms "$persist_p95_ms" \
  --arg drain_records_p95 "$drain_records_p95" \
  --arg drain_duration_p95_ms "$drain_duration_p95_ms" \
  --argjson timed_out "$timed_out" \
  '{
    backlog_start: $backlog_start,
    backlog_end: $backlog_end,
    drain_seconds: $drain_seconds,
    processed: $processed,
    processed_per_second: $processed_per_second,
    lag_p95_ms: ($lag_p95_ms | tonumber?),
    persist_p95_ms: ($persist_p95_ms | tonumber?),
    drain_records_p95: ($drain_records_p95 | tonumber?),
    drain_duration_p95_ms: ($drain_duration_p95_ms | tonumber?),
    timed_out: $timed_out
  }' >"$RESULT_FILE"

echo "[7/7] 결과: $RESULT_FILE"
jq . "$RESULT_FILE"

if [ "$timed_out" = true ]; then
  echo "제한시간 안에 backlog를 비우지 못했습니다. 결과 파일은 보존했습니다." >&2
  exit 1
fi
