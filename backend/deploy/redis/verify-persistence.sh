#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/redis-cli-common.sh
source "$SCRIPT_DIR/lib/redis-cli-common.sh"

FIXTURE_STATE_FILE="${FIXTURE_STATE_FILE:-/var/tmp/upbid-redis-recovery-fixture.env}"

fail() {
  echo "ERROR: $1" >&2
  exit 1
}

fixture_value() {
  local field="$1"
  awk -F= -v field="$field" '$1 == field { sub(/^[^=]*=/, ""); print; exit }' \
    "$FIXTURE_STATE_FILE"
}

config_value() {
  redis_call --raw CONFIG GET "$1" | tail -n 1 | tr -d '\r'
}

info_value() {
  local payload="$1"
  local field="$2"
  printf '%s\n' "$payload" \
    | tr -d '\r' \
    | awk -F: -v field="$field" '$1 == field { print $2; exit }'
}

[ -r "$FIXTURE_STATE_FILE" ] || fail "fixture state file is not readable: $FIXTURE_STATE_FILE"

RUN_ID="$(fixture_value RUN_ID)"
ITEM_KEY="$(fixture_value ITEM_KEY)"
STREAM_KEY="$(fixture_value STREAM_KEY)"
GROUP="$(fixture_value GROUP)"
STREAM_ID="$(fixture_value STREAM_ID)"

[ -n "$RUN_ID" ] && [ -n "$ITEM_KEY" ] && [ -n "$STREAM_KEY" ] \
  && [ -n "$GROUP" ] && [ -n "$STREAM_ID" ] \
  || fail "fixture state file is incomplete"
[[ "$ITEM_KEY" == "upbid:recovery:test:$RUN_ID:"* ]] \
  || fail "fixture item key is outside the recovery namespace"
[[ "$STREAM_KEY" == "upbid:recovery:test:$RUN_ID:"* ]] \
  || fail "fixture stream key is outside the recovery namespace"

[ "$(redis_call PING)" = "PONG" ] || fail "Redis PING failed"

APPENDONLY="$(config_value appendonly)"
APPENDFSYNC="$(config_value appendfsync)"
MAXMEMORY_POLICY="$(config_value maxmemory-policy)"
REDIS_DIR="$(config_value dir)"
APPEND_DIRNAME="$(config_value appenddirname)"

[ "$APPENDONLY" = "yes" ] || fail "appendonly is $APPENDONLY"
[ "$APPENDFSYNC" = "always" ] || fail "appendfsync is $APPENDFSYNC"
[ "$MAXMEMORY_POLICY" = "noeviction" ] || fail "maxmemory-policy is $MAXMEMORY_POLICY"

PERSISTENCE_INFO="$(redis_call INFO persistence)"
[ "$(info_value "$PERSISTENCE_INFO" aof_enabled)" = "1" ] || fail "AOF is not enabled"
[ "$(info_value "$PERSISTENCE_INFO" aof_rewrite_in_progress)" = "0" ] \
  || fail "AOF rewrite is still in progress"
[ "$(info_value "$PERSISTENCE_INFO" aof_rewrite_scheduled)" = "0" ] \
  || fail "AOF rewrite is still scheduled"
[ "$(info_value "$PERSISTENCE_INFO" aof_last_bgrewrite_status)" = "ok" ] \
  || fail "last AOF rewrite failed"
[ "$(info_value "$PERSISTENCE_INFO" aof_last_write_status)" = "ok" ] \
  || fail "last AOF write failed"

[ "$(redis_call EXISTS "$ITEM_KEY")" = "1" ] || fail "fixture item key is missing"
[ "$(redis_call EXISTS "$STREAM_KEY")" = "1" ] || fail "fixture stream key is missing"

PENDING_COUNT="$(redis_call --raw XPENDING "$STREAM_KEY" "$GROUP" | sed -n '1p' | tr -d '\r')"
[ "$PENDING_COUNT" = "1" ] || fail "expected one PEL entry, got: $PENDING_COUNT"

AOF_DIR="$REDIS_DIR/$APPEND_DIRNAME"
[ -d "$AOF_DIR" ] || fail "AOF directory is missing: $AOF_DIR"
find "$AOF_DIR" -maxdepth 1 -type f -name '*.manifest' | grep -q . \
  || fail "AOF manifest is missing"
find "$AOF_DIR" -maxdepth 1 -type f -name '*.base.*' | grep -q . \
  || fail "AOF base file is missing"
find "$AOF_DIR" -maxdepth 1 -type f -name '*.incr.aof' | grep -q . \
  || fail "AOF incremental file is missing"

echo "appendonly=$APPENDONLY"
echo "appendfsync=$APPENDFSYNC"
echo "maxmemory-policy=$MAXMEMORY_POLICY"
echo "AOF directory=$AOF_DIR"
if command -v findmnt >/dev/null 2>&1; then
  findmnt -T "$AOF_DIR"
fi
df -h "$AOF_DIR"
redis_call HGETALL "$ITEM_KEY"
redis_call XRANGE "$STREAM_KEY" "$STREAM_ID" "$STREAM_ID"
redis_call XINFO GROUPS "$STREAM_KEY"
redis_call XPENDING "$STREAM_KEY" "$GROUP"
echo "Redis persistence and recovery fixture verification passed"
