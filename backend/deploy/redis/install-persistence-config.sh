#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SOURCE_CONF="$SCRIPT_DIR/upbid-persistence.conf"
REDIS_CONFIG_FILE="${REDIS_CONFIG_FILE:-/etc/redis/redis.conf}"
REDIS_SNIPPET_FILE="${REDIS_SNIPPET_FILE:-/etc/redis/upbid-persistence.conf}"
REDIS_RDB_FILE="${REDIS_RDB_FILE:-/var/lib/redis/dump.rdb}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/upbid-redis}"
INCLUDE_LINE="include $REDIS_SNIPPET_FILE"
MODE="install"

if [ "${1:-}" = "--backup-only" ]; then
  MODE="backup-only"
  shift
fi

[ "$#" -eq 0 ] || {
  echo "Usage: $0 [--backup-only]" >&2
  exit 2
}

fail() {
  echo "ERROR: $1" >&2
  exit 1
}

[ -f "$SOURCE_CONF" ] || fail "source config not found: $SOURCE_CONF"
[ -f "$REDIS_CONFIG_FILE" ] || fail "Redis config not found: $REDIS_CONFIG_FILE"
[ -f "$REDIS_RDB_FILE" ] || fail "RDB backup source not found: $REDIS_RDB_FILE"

if [ -z "${SKIP_OWNER_CHECK:-}" ] && [ "$(id -u)" -ne 0 ]; then
  fail "run as root so Redis config ownership can be preserved"
fi

mkdir -p "$BACKUP_DIR" "$(dirname "$REDIS_SNIPPET_FILE")"

TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)-$$"
CONFIG_BACKUP="$BACKUP_DIR/redis.conf.$TIMESTAMP"
RDB_BACKUP="$BACKUP_DIR/dump.rdb.$TIMESTAMP"

cp -p "$REDIS_CONFIG_FILE" "$CONFIG_BACKUP"
cp -p "$REDIS_RDB_FILE" "$RDB_BACKUP"

if [ "$MODE" = "backup-only" ]; then
  if [ -z "${SKIP_OWNER_CHECK:-}" ]; then
    chmod 0750 "$BACKUP_DIR"
    chmod 0640 "$CONFIG_BACKUP" "$RDB_BACKUP"
  fi
  echo "Redis config backup: $CONFIG_BACKUP"
  echo "Redis RDB backup: $RDB_BACKUP"
  echo "Persistence config was not installed"
  echo "Redis restart was not performed"
  exit 0
fi

install -m 0640 "$SOURCE_CONF" "$REDIS_SNIPPET_FILE"

if ! grep -Fqx "$INCLUDE_LINE" "$REDIS_CONFIG_FILE"; then
  printf '\n# Managed by WEB-Team6-Hot6ix issue #342\n%s\n' "$INCLUDE_LINE" \
    >> "$REDIS_CONFIG_FILE"
fi

if [ -z "${SKIP_OWNER_CHECK:-}" ]; then
  chown root:redis "$REDIS_SNIPPET_FILE"
  chmod 0750 "$BACKUP_DIR"
  chmod 0640 "$CONFIG_BACKUP" "$RDB_BACKUP"
fi

echo "Redis config backup: $CONFIG_BACKUP"
echo "Redis RDB backup: $RDB_BACKUP"
echo "Redis restart was not performed"
