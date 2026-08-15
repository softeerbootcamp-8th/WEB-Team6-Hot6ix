#!/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
RUN="$ROOT/perf/run.sh"
README="$ROOT/perf/README.md"
DASHBOARD="$ROOT/perf/grafana/dashboards/upbid.json"
COMMON="$ROOT/perf/k6/common.js"
BURST="$ROOT/perf/k6/burst.js"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

for symbol in \
  P99_MS K6_P99_MS \
  CONN_ACQUIRE_P99_MS CONN_USAGE_P95_MS CONN_USAGE_P99_MS CONN_TIMEOUT_COUNT \
  PROCESS_CPU_AVG PROCESS_CPU_MAX SYSTEM_CPU_AVG SYSTEM_CPU_MAX SYSTEM_LOAD_AVG SYSTEM_LOAD_MAX \
  DROPPED_ITERATIONS BID_ATTEMPT_PER_S BID_ACCEPT_RATE; do
  count="$({ grep -o "$symbol" "$RUN" || true; } | wc -l | tr -d ' ')"
  [ "$count" -ge 3 ] || fail "$symbol 이 계산·CSV·note에 모두 연결되지 않았습니다 (등장 ${count}회)"
done

for symbol in PROM_SCRAPE_SETTLE_SECONDS METRIC_COLLECTION_END_EPOCH client_delta_sum SSE_CLIENT_DELIVERY_RATIO; do
  grep -q "$symbol" "$RUN" || fail "$symbol 기반 종료 경계 검증이 없습니다"
done

grep -q 'upbid_bid_before_lock_seconds_bucket' "$RUN" \
  || fail "측정 전 검사에 before-lock 지표가 없습니다"

grep -q 'ratio + 0 >= 99.9 && ratio + 0 <= 100.1' "$RUN" \
  || fail "BID_PLACED 실제 전달률 실패 판정이 없습니다"
grep -q 'SSE_MSG_LATENCY_SAMPLES.*SSE_CLIENT_BID_EVENTS_RECEIVED' "$RUN" \
  || fail "BID_PLACED 수신 수와 latency 표본 정합성 판정이 없습니다"
grep -q 'SSE_PERF=.*BID_PLACED' "$RUN" \
  || fail "시나리오 10 핵심 지연에서 heartbeat를 제외하지 않았습니다"
grep -q '^SSE_VT=false$' "$RUN" \
  || fail "동기 구현 결과가 sse_vt=false로 고정되지 않았습니다"
grep -q '^SSE_POOL=0$' "$RUN" \
  || fail "동기 구현 결과의 dispatch pool이 0으로 기록되지 않습니다"
grep -q 'SSE_VT_SUFFIX="_ssesync"' "$RUN" \
  || fail "시나리오 10 실행 이름에서 동기 구현을 구분하지 않습니다"
grep -q '동기 SSE 구현에서는 --sse-vt를 사용할 수 없다' "$RUN" \
  || fail "동기 구현에서 잘못된 --sse-vt 실행을 차단하지 않습니다"

grep -q 'hikaricp_connections_usage_seconds_bucket' "$DASHBOARD" \
  || fail "Grafana에 Hikari usage 지표가 없습니다"
grep -q 'process_cpu_usage' "$DASHBOARD" \
  || fail "Grafana에 앱 CPU 지표가 없습니다"
grep -q '입찰 시도율' "$README" || fail "README에 입찰 시도율 해석이 없습니다"
grep -q 'dropped iteration' "$README" || fail "README에 dropped iteration 판정이 없습니다"
grep -q "summaryTrendStats: \['avg', 'min', 'med', 'max', 'p(95)', 'p(99)'\]" "$COMMON" \
  || fail "공통 k6 요약에 p99 설정이 없습니다"
grep -q "summaryTrendStats: \['avg', 'min', 'med', 'max', 'p(95)', 'p(99)'\]" "$BURST" \
  || fail "burst k6 요약에 p99 설정이 없습니다"

echo "PASS: 성능 결과 스키마 계약"
