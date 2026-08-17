#!/bin/bash
set -euo pipefail

CONFIRM_VALUE="activate-without-restart"
if [ "${UPBID_AOF_ACTIVATION_CONFIRM:-}" != "$CONFIRM_VALUE" ]; then
  echo "ERROR: set UPBID_AOF_ACTIVATION_CONFIRM=$CONFIRM_VALUE to activate AOF" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/redis-cli-common.sh
source "$SCRIPT_DIR/lib/redis-cli-common.sh"

fail() {
  echo "ERROR: $1" >&2
  exit 1
}

field_value() {
  local payload="$1"
  local field="$2"
  printf '%s\n' "$payload" \
    | tr -d '\r' \
    | awk -F: -v field="$field" '$1 == field { print $2; exit }'
}

expect_ok() {
  local description="$1"
  shift
  local result
  result="$(redis_call "$@")"
  [ "$result" = "OK" ] || fail "$description failed: $result"
}

config_value() {
  redis_call --raw CONFIG GET "$1" | tail -n 1 | tr -d '\r'
}

[ "$(redis_call PING)" = "PONG" ] || fail "Redis PING failed"

EXPECTED_REDIS_DIR="${EXPECTED_REDIS_DIR:-/var/lib/redis}"
EXPECTED_APPEND_DIRNAME="${EXPECTED_APPEND_DIRNAME:-appendonlydir}"
CURRENT_REDIS_DIR="$(config_value dir)"
CURRENT_APPEND_DIRNAME="$(config_value appenddirname)"

[ "$CURRENT_REDIS_DIR" = "$EXPECTED_REDIS_DIR" ] \
  || fail "Redis dir is $CURRENT_REDIS_DIR, expected $EXPECTED_REDIS_DIR"
[ "$CURRENT_APPEND_DIRNAME" = "$EXPECTED_APPEND_DIRNAME" ] \
  || fail "Redis appenddirname is $CURRENT_APPEND_DIRNAME, expected $EXPECTED_APPEND_DIRNAME"

BEFORE="$(redis_call INFO persistence)"
if [ "$(field_value "$BEFORE" aof_rewrite_in_progress)" = "1" ]; then
  fail "an AOF rewrite is already in progress"
fi

expect_ok "appendfsync update" CONFIG SET appendfsync always
expect_ok "maxmemory policy update" CONFIG SET maxmemory-policy noeviction
expect_ok "AOF activation" CONFIG SET appendonly yes

MAX_ATTEMPTS="${AOF_REWRITE_MAX_ATTEMPTS:-300}"
SLEEP_SECONDS="${AOF_REWRITE_POLL_SECONDS:-1}"
LAST_INFO=""

for ((attempt = 1; attempt <= MAX_ATTEMPTS; attempt++)); do
  LAST_INFO="$(redis_call INFO persistence)"
  if [ "$(field_value "$LAST_INFO" aof_enabled)" = "1" ] \
    && [ "$(field_value "$LAST_INFO" aof_rewrite_in_progress)" = "0" ] \
    && [ "$(field_value "$LAST_INFO" aof_rewrite_scheduled)" = "0" ] \
    && [ "$(field_value "$LAST_INFO" aof_last_bgrewrite_status)" = "ok" ] \
    && [ "$(field_value "$LAST_INFO" aof_last_write_status)" = "ok" ]; then
    break
  fi

  if [ "$attempt" -eq "$MAX_ATTEMPTS" ]; then
    printf '%s\n' "$LAST_INFO" >&2
    fail "AOF rewrite did not become healthy within ${MAX_ATTEMPTS} attempts"
  fi
  sleep "$SLEEP_SECONDS"
done

redis_call CONFIG GET appendonly
redis_call CONFIG GET appendfsync
redis_call CONFIG GET maxmemory-policy
redis_call CONFIG GET dir
redis_call CONFIG GET appenddirname
printf '%s\n' "$LAST_INFO" \
  | tr -d '\r' \
  | grep -E '^aof_(enabled|rewrite_in_progress|rewrite_scheduled|last_bgrewrite_status|last_write_status):'

echo "AOF activation completed without restarting Redis"
