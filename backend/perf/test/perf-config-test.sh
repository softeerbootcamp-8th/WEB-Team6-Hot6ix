#!/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
RUN="$ROOT/perf/run.sh"
DASHBOARD="$ROOT/perf/grafana/dashboards/upbid.json"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

for profile in application-perf.yaml application-prod.yaml; do
  file="$ROOT/src/main/resources/$profile"
  grep -q 'hikaricp.connections.usage: true' "$file" \
    || fail "$profile 에 Hikari usage histogram 설정이 없습니다"
done

for metric in \
  hikaricp_connections_usage_seconds_bucket \
  hikaricp_connections_timeout_total \
  process_cpu_usage \
  system_cpu_usage \
  system_load_average_1m; do
  grep -q "$metric" "$RUN" || fail "run.sh 사전 검사에 $metric 이 없습니다"
done

if grep -Fq 'run=\"$run\"' "$DASHBOARD"; then
  fail "Grafana의 All 실행 필터와 호환되지 않는 정확 일치 run selector가 있습니다"
fi

echo "PASS: 성능 계측 설정 계약"
