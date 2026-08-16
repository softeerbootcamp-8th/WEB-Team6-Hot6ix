#!/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
RUN="$ROOT/perf/run.sh"
DASHBOARD="$ROOT/perf/grafana/dashboards/upbid.json"
DEPLOY_COMPOSE="$ROOT/deploy/docker-compose.yml"
PERF_COMPOSE="$ROOT/perf/docker-compose.perf.yml"
BACKEND_WORKFLOW="$ROOT/../.github/workflows/CICD-BE.yml"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

for profile in application-perf.yaml application-prod.yaml; do
  file="$ROOT/src/main/resources/$profile"
  grep -q 'hikaricp.connections.usage: true' "$file" \
    || fail "$profile 에 Hikari usage histogram 설정이 없습니다"
done

for variable in \
  BID_STREAM_CONSUMER_ENABLED \
  BID_STREAM_MAX_RECORDS_PER_POLL \
  BID_STREAM_MAX_DRAIN_DURATION; do
  for file in "$DEPLOY_COMPOSE" "$PERF_COMPOSE" "$BACKEND_WORKFLOW"; do
    grep -q "$variable" "$file" \
      || fail "$(basename "$file")에 $variable 전달 설정이 없습니다"
  done
done

for metric in \
  hikaricp_connections_usage_seconds_bucket \
  hikaricp_connections_timeout_total \
  process_cpu_usage \
  system_cpu_usage \
  system_load_average_1m \
  upbid_bid_stream_backlog \
  upbid_bid_stream_delivery_lag_milliseconds_bucket \
  upbid_bid_stream_persist_seconds_bucket \
  upbid_bid_stream_drain_records_bucket \
  upbid_bid_stream_drain_duration_seconds_bucket; do
  grep -q "$metric" "$RUN" || fail "run.sh 사전 검사에 $metric 이 없습니다"
done

if grep -Fq 'run=\"$run\"' "$DASHBOARD"; then
  fail "Grafana의 All 실행 필터와 호환되지 않는 정확 일치 run selector가 있습니다"
fi

for metric in \
  upbid_bid_stream_backlog \
  upbid_bid_stream_delivery_lag_milliseconds_bucket \
  upbid_bid_stream_drain_records_bucket \
  upbid_bid_stream_drain_duration_seconds_bucket; do
  grep -q "$metric" "$DASHBOARD" || fail "Grafana에 $metric 패널이 없습니다"
done
grep -q 'Pending은 이미 Consumer에게 전달된 이벤트만 포함' "$DASHBOARD" \
  || fail "Grafana에 backlog와 Pending의 차이 설명이 없습니다"

echo "PASS: 성능 계측 설정 계약"
