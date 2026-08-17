#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/redis-cli-common.sh
source "$SCRIPT_DIR/lib/redis-cli-common.sh"

fail() {
  echo "ERROR: $1" >&2
  exit 1
}

RUN_ID="${RECOVERY_RUN_ID:-aof-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
[[ "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ ]] || fail "invalid recovery run ID: $RUN_ID"

PREFIX="upbid:recovery:test:$RUN_ID"
ITEM_KEY="$PREFIX:item"
STREAM_KEY="$PREFIX:stream"
GROUP="upbid-recovery"
CONSUMER="verifier"
FIXTURE_STATE_FILE="${FIXTURE_STATE_FILE:-/var/tmp/upbid-redis-recovery-fixture.env}"

[ "$(redis_call PING)" = "PONG" ] || fail "Redis PING failed"
[ "$(redis_call EXISTS "$ITEM_KEY")" = "0" ] \
  || fail "fixture item key already exists: $ITEM_KEY"
[ "$(redis_call EXISTS "$STREAM_KEY")" = "0" ] \
  || fail "fixture stream key already exists: $STREAM_KEY"

redis_call HSET "$ITEM_KEY" \
  marker "$RUN_ID" \
  currentPrice 10000 \
  leaderUserId 342 >/dev/null
redis_call XGROUP CREATE "$STREAM_KEY" "$GROUP" 0 MKSTREAM >/dev/null

STREAM_ID="$(redis_call XADD "$STREAM_KEY" '*' \
  type BID_ACCEPTED \
  requestId "recovery-$RUN_ID" \
  itemId 342 \
  amount 10000)"
[ -n "$STREAM_ID" ] || fail "XADD did not return a stream ID"

redis_call XREADGROUP \
  GROUP "$GROUP" "$CONSUMER" \
  COUNT 1 \
  STREAMS "$STREAM_KEY" '>' >/dev/null

PENDING_COUNT="$(redis_call --raw XPENDING "$STREAM_KEY" "$GROUP" | sed -n '1p' | tr -d '\r')"
[ "$PENDING_COUNT" = "1" ] || fail "expected one PEL entry, got: $PENDING_COUNT"

umask 077
{
  printf 'RUN_ID=%s\n' "$RUN_ID"
  printf 'ITEM_KEY=%s\n' "$ITEM_KEY"
  printf 'STREAM_KEY=%s\n' "$STREAM_KEY"
  printf 'GROUP=%s\n' "$GROUP"
  printf 'STREAM_ID=%s\n' "$STREAM_ID"
} > "$FIXTURE_STATE_FILE"
chmod 0600 "$FIXTURE_STATE_FILE"

echo "Recovery fixture prepared: $RUN_ID"
echo "Fixture state file: $FIXTURE_STATE_FILE"
echo "Keep this fixture unacknowledged until restart recovery is verified"
