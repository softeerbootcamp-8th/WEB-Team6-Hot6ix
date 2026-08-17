#!/bin/bash

REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_CA_CERT="${REDIS_CA_CERT:-/etc/redis/tls/ca.crt}"
REDIS_CONFIG_FILE="${REDIS_CONFIG_FILE:-/etc/redis/redis.conf}"

redis_cli_fail() {
  echo "ERROR: $1" >&2
  return 1
}

[ -f "$REDIS_CA_CERT" ] || redis_cli_fail "Redis CA certificate not found: $REDIS_CA_CERT"

if [ -z "${REDISCLI_AUTH:-}" ]; then
  if [ "$(id -u)" -ne 0 ]; then
    redis_cli_fail "set REDISCLI_AUTH or run as root to read the existing Redis config"
  fi
  [ -r "$REDIS_CONFIG_FILE" ] \
    || redis_cli_fail "Redis config is not readable: $REDIS_CONFIG_FILE"
  REDISCLI_AUTH="$(awk '
    $1 == "requirepass" && $0 !~ /^[[:space:]]*#/ { print $2; exit }
  ' "$REDIS_CONFIG_FILE")"
  [ -n "$REDISCLI_AUTH" ] \
    || redis_cli_fail "active requirepass was not found in $REDIS_CONFIG_FILE"
  if [[ "$REDISCLI_AUTH" == \"*\" && "$REDISCLI_AUTH" == *\" ]]; then
    REDISCLI_AUTH="${REDISCLI_AUTH:1:${#REDISCLI_AUTH}-2}"
  fi
  export REDISCLI_AUTH
fi

REDIS_CLI=(
  redis-cli
  --no-auth-warning
  --tls
  --cacert "$REDIS_CA_CERT"
  -h "$REDIS_HOST"
  -p "$REDIS_PORT"
)

redis_call() {
  "${REDIS_CLI[@]}" "$@"
}
