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
