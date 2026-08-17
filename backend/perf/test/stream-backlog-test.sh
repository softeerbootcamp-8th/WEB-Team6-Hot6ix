#!/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT="$ROOT/perf/stream-backlog.sh"
K6_SCRIPT="$ROOT/perf/k6/stream-backlog.js"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

[ -f "$SCRIPT" ] || fail "stream-backlog.sh가 없습니다"
[ -f "$K6_SCRIPT" ] || fail "stream-backlog.js가 없습니다"

for contract in \
  'BID_STREAM_CONSUMER_ENABLED=false' \
  'BID_STREAM_CONSUMER_ENABLED=true' \
  'redis-cli XLEN auction:bid:stream' \
  'stream-drain.json' \
  'timed_out' \
  'target-backlog'; do
  grep -q "$contract" "$SCRIPT" || fail "stream-backlog.sh에 $contract 계약이 없습니다"
done

for field in \
  backlog_start backlog_end drain_seconds processed processed_per_second \
  lag_p95_ms persist_p95_ms drain_records_p95 drain_duration_p95_ms timed_out; do
  grep -q "$field" "$SCRIPT" || fail "stream-drain.json에 $field 필드가 없습니다"
done

grep -q 'submitBid' "$K6_SCRIPT" || fail "backlog 부하가 실제 입찰 API 공통 함수를 사용하지 않습니다"
grep -q 'BACKLOG_EPOCH_MS' "$K6_SCRIPT" || fail "단조 증가 금액 기준 시각이 없습니다"
grep -q 'Idempotency-Key' "$ROOT/perf/k6/bid.js" || fail "실제 입찰 API에 멱등 키를 보내지 않습니다"

if grep -q 'item-detail' "$K6_SCRIPT"; then
  fail "Consumer OFF에서 stale MySQL 현재가를 읽으면 승인 backlog를 만들 수 없습니다"
fi
if grep -Eq 'redis-cli[[:space:]]+XADD|/consumer/(enable|disable)|consumer-enabled' "$K6_SCRIPT"; then
  fail "k6가 Stream이나 Consumer를 직접 조작하면 안 됩니다"
fi
if grep -Eq 'redis-cli[[:space:]]+XADD|/consumer/(enable|disable)' "$SCRIPT"; then
  fail "측정 스크립트가 직접 XADD 또는 HTTP 제어 API를 사용하면 안 됩니다"
fi

echo "PASS: Stream backlog 측정 도구 계약"
