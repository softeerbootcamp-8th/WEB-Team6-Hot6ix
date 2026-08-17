#!/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CONF="$ROOT/upbid-persistence.conf"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

assert_line() {
  grep -Fqx "$1" "$CONF" || fail "설정 누락: $1"
}

[ -f "$CONF" ] || fail "upbid-persistence.conf가 없습니다"

assert_line "appendonly yes"
assert_line "appendfsync always"
assert_line "maxmemory-policy noeviction"
assert_line "dir /var/lib/redis"
assert_line "appenddirname appendonlydir"

if grep -Eiq 'requirepass|masterauth|password|secret|BEGIN (RSA |EC )?PRIVATE KEY' "$CONF"; then
  fail "설정 조각에 인증 정보가 포함됐습니다"
fi

INSTALL="$ROOT/install-persistence-config.sh"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEST_ROOT"' EXIT

mkdir -p \
  "$TEST_ROOT/etc/redis" \
  "$TEST_ROOT/var/lib/redis" \
  "$TEST_ROOT/backups"
printf 'appendonly no\n' > "$TEST_ROOT/etc/redis/redis.conf"
printf 'rdb-fixture\n' > "$TEST_ROOT/var/lib/redis/dump.rdb"

install_fixture() {
  REDIS_CONFIG_FILE="$TEST_ROOT/etc/redis/redis.conf" \
  REDIS_SNIPPET_FILE="$TEST_ROOT/etc/redis/upbid-persistence.conf" \
  REDIS_RDB_FILE="$TEST_ROOT/var/lib/redis/dump.rdb" \
  BACKUP_DIR="$TEST_ROOT/backups" \
  SKIP_OWNER_CHECK=1 \
    "$INSTALL"
}

REDIS_CONFIG_FILE="$TEST_ROOT/etc/redis/redis.conf" \
REDIS_SNIPPET_FILE="$TEST_ROOT/etc/redis/upbid-persistence.conf" \
REDIS_RDB_FILE="$TEST_ROOT/var/lib/redis/dump.rdb" \
BACKUP_DIR="$TEST_ROOT/backups" \
SKIP_OWNER_CHECK=1 \
  "$INSTALL" --backup-only >/dev/null

if grep -Fq 'include ' "$TEST_ROOT/etc/redis/redis.conf"; then
  fail "backup-only 단계에서 Redis include를 설치했습니다"
fi
[ ! -e "$TEST_ROOT/etc/redis/upbid-persistence.conf" ] \
  || fail "backup-only 단계에서 persistence 설정을 설치했습니다"

install_fixture >/dev/null
install_fixture >/dev/null

INCLUDE_LINE="include $TEST_ROOT/etc/redis/upbid-persistence.conf"
grep -Fqx "$INCLUDE_LINE" "$TEST_ROOT/etc/redis/redis.conf" \
  || fail "include가 설치되지 않았습니다"
[ "$(grep -Fc "$INCLUDE_LINE" "$TEST_ROOT/etc/redis/redis.conf")" -eq 1 ] \
  || fail "include가 중복됐습니다"

cmp -s "$CONF" "$TEST_ROOT/etc/redis/upbid-persistence.conf" \
  || fail "설치된 persistence 설정이 저장소와 다릅니다"
find "$TEST_ROOT/backups" -type f -name 'redis.conf.*' | grep -q . \
  || fail "redis.conf 백업이 없습니다"
find "$TEST_ROOT/backups" -type f -name 'dump.rdb.*' | grep -q . \
  || fail "dump.rdb 백업이 없습니다"

if grep -En '^[[:space:]]*(sudo[[:space:]]+)?(systemctl[[:space:]]+(restart|stop)|service[[:space:]]+redis.*(restart|stop)|kill|reboot|shutdown)\b' \
  "$INSTALL" >/dev/null; then
  fail "설치 스크립트에 프로세스 중단 명령이 있습니다"
fi

ACTIVATE="$ROOT/activate-aof.sh"
FAKE_BIN="$TEST_ROOT/fake-bin"
FAKE_REDIS_LOG="$TEST_ROOT/redis-cli.log"
mkdir -p "$FAKE_BIN"
touch "$TEST_ROOT/ca.crt" "$FAKE_REDIS_LOG"

cat > "$FAKE_BIN/redis-cli" <<'EOF'
#!/bin/bash
set -euo pipefail
printf '%s\n' "$*" >> "$FAKE_REDIS_LOG"
if [ -n "${FAKE_AUTH_LOG:-}" ]; then
  printf '%s\n' "${REDISCLI_AUTH:-}" >> "$FAKE_AUTH_LOG"
fi

case "$*" in
  *" PING")
    echo PONG
    ;;
  *" CONFIG SET "*)
    echo OK
    ;;
  *" CONFIG GET appendonly")
    printf 'appendonly\nyes\n'
    ;;
  *" CONFIG GET appendfsync")
    printf 'appendfsync\nalways\n'
    ;;
  *" CONFIG GET maxmemory-policy")
    printf 'maxmemory-policy\nnoeviction\n'
    ;;
  *" CONFIG GET dir")
    printf 'dir\n%s\n' "${FAKE_REDIS_DATA_DIR:-/var/lib/redis}"
    ;;
  *" CONFIG GET appenddirname")
    printf 'appenddirname\nappendonlydir\n'
    ;;
  *" INFO persistence")
    printf '%s\n' \
      'aof_enabled:1' \
      'aof_rewrite_in_progress:0' \
      'aof_rewrite_scheduled:0' \
      'aof_last_bgrewrite_status:ok' \
      'aof_last_write_status:ok'
    ;;
  *" HSET "*)
    echo 4
    ;;
  *" XGROUP CREATE "*)
    echo OK
    ;;
  *" XADD "*)
    echo '1750000000000-0'
    ;;
  *" XREADGROUP GROUP "*)
    printf '%s\n' '1750000000000-0' 'type' 'BID_ACCEPTED'
    ;;
  *" XPENDING "*)
    printf '%s\n' '1' '1750000000000-0' '1750000000000-0' 'verifier' '1'
    ;;
  *" EXISTS "*)
    echo "${FAKE_EXISTS_VALUE:-1}"
    ;;
  *" HGETALL "*)
    printf '%s\n' 'marker' 'aof-contract' 'currentPrice' '10000' 'leaderUserId' '342'
    ;;
  *" XRANGE "*)
    printf '%s\n' '1750000000000-0' 'type' 'BID_ACCEPTED'
    ;;
  *" XINFO GROUPS "*)
    printf '%s\n' 'name' 'upbid-recovery' 'pending' '1'
    ;;
  *)
    echo "unexpected redis-cli call: $*" >&2
    exit 90
    ;;
esac
EOF
chmod +x "$FAKE_BIN/redis-cli"

cat > "$FAKE_BIN/id" <<'EOF'
#!/bin/bash
if [ "${1:-}" = "-u" ]; then
  echo 0
else
  /usr/bin/id "$@"
fi
EOF
chmod +x "$FAKE_BIN/id"

run_activate() {
  PATH="$FAKE_BIN:$PATH" \
  FAKE_REDIS_LOG="$FAKE_REDIS_LOG" \
  REDISCLI_AUTH=test-password \
  REDIS_CA_CERT="$TEST_ROOT/ca.crt" \
  UPBID_AOF_ACTIVATION_CONFIRM=activate-without-restart \
    "$ACTIVATE"
}

: > "$FAKE_REDIS_LOG"
if PATH="$FAKE_BIN:$PATH" \
  FAKE_REDIS_LOG="$FAKE_REDIS_LOG" \
  REDISCLI_AUTH=test-password \
  REDIS_CA_CERT="$TEST_ROOT/ca.crt" \
  "$ACTIVATE" >/dev/null 2>&1; then
  fail "확인 토큰 없이 AOF 활성화가 성공했습니다"
fi
[ ! -s "$FAKE_REDIS_LOG" ] || fail "확인 토큰 전에 Redis에 연결했습니다"

QUOTED_REDIS_CONFIG="$TEST_ROOT/quoted-redis.conf"
FAKE_AUTH_LOG="$TEST_ROOT/redis-auth.log"
printf 'requirepass "quoted-password"\n' > "$QUOTED_REDIS_CONFIG"
: > "$FAKE_AUTH_LOG"
env -u REDISCLI_AUTH \
  PATH="$FAKE_BIN:$PATH" \
  FAKE_REDIS_LOG="$FAKE_REDIS_LOG" \
  FAKE_AUTH_LOG="$FAKE_AUTH_LOG" \
  REDIS_CONFIG_FILE="$QUOTED_REDIS_CONFIG" \
  REDIS_CA_CERT="$TEST_ROOT/ca.crt" \
  UPBID_AOF_ACTIVATION_CONFIRM=activate-without-restart \
  "$ACTIVATE" >/dev/null
[ "$(sed -n '1p' "$FAKE_AUTH_LOG")" = "quoted-password" ] \
  || fail "따옴표로 감싼 requirepass를 올바르게 읽지 못했습니다"

: > "$FAKE_REDIS_LOG"
if PATH="$FAKE_BIN:$PATH" \
  FAKE_REDIS_LOG="$FAKE_REDIS_LOG" \
  FAKE_REDIS_DATA_DIR="$TEST_ROOT/not-persistent" \
  REDISCLI_AUTH=test-password \
  REDIS_CA_CERT="$TEST_ROOT/ca.crt" \
  UPBID_AOF_ACTIVATION_CONFIRM=activate-without-restart \
  "$ACTIVATE" >/dev/null 2>&1; then
  fail "예상하지 않은 Redis data dir에서 AOF 활성화가 성공했습니다"
fi
if grep -Fq 'CONFIG SET' "$FAKE_REDIS_LOG"; then
  fail "Redis data dir 검증 전에 런타임 설정을 변경했습니다"
fi

: > "$FAKE_REDIS_LOG"
run_activate >/dev/null

FSYNC_LINE="$(grep -nF 'CONFIG SET appendfsync always' "$FAKE_REDIS_LOG" | cut -d: -f1)"
EVICTION_LINE="$(grep -nF 'CONFIG SET maxmemory-policy noeviction' "$FAKE_REDIS_LOG" | cut -d: -f1)"
AOF_LINE="$(grep -nF 'CONFIG SET appendonly yes' "$FAKE_REDIS_LOG" | cut -d: -f1)"
[ "$FSYNC_LINE" -lt "$EVICTION_LINE" ] && [ "$EVICTION_LINE" -lt "$AOF_LINE" ] \
  || fail "AOF 런타임 설정 순서가 잘못됐습니다"

if grep -Eq '(^| )(-a|--pass)( |$)|test-password' "$FAKE_REDIS_LOG"; then
  fail "Redis 비밀번호가 명령행 인자에 노출됐습니다"
fi

if grep -En '^[[:space:]]*(sudo[[:space:]]+)?(systemctl[[:space:]]+(restart|stop)|service[[:space:]]+redis.*(restart|stop)|kill|reboot|shutdown)\b' \
  "$ACTIVATE" >/dev/null; then
  fail "활성화 스크립트에 프로세스 중단 명령이 있습니다"
fi

PREPARE="$ROOT/prepare-recovery-fixture.sh"
VERIFY="$ROOT/verify-persistence.sh"
FIXTURE_STATE_FILE="$TEST_ROOT/recovery-fixture.env"
FAKE_REDIS_DATA_DIR="$TEST_ROOT/redis-data"
mkdir -p "$FAKE_REDIS_DATA_DIR/appendonlydir"
touch \
  "$FAKE_REDIS_DATA_DIR/appendonlydir/appendonly.aof.manifest" \
  "$FAKE_REDIS_DATA_DIR/appendonlydir/appendonly.aof.1.base.rdb" \
  "$FAKE_REDIS_DATA_DIR/appendonlydir/appendonly.aof.1.incr.aof"

: > "$FAKE_REDIS_LOG"
PATH="$FAKE_BIN:$PATH" \
FAKE_REDIS_LOG="$FAKE_REDIS_LOG" \
FAKE_REDIS_DATA_DIR="$FAKE_REDIS_DATA_DIR" \
FAKE_EXISTS_VALUE=0 \
REDISCLI_AUTH=test-password \
REDIS_CA_CERT="$TEST_ROOT/ca.crt" \
FIXTURE_STATE_FILE="$FIXTURE_STATE_FILE" \
RECOVERY_RUN_ID=aof-contract \
  "$PREPARE" >/dev/null

[ -f "$FIXTURE_STATE_FILE" ] || fail "fixture 식별 파일이 없습니다"
if [ "$(uname -s)" = "Darwin" ]; then
  FIXTURE_MODE="$(stat -f '%Lp' "$FIXTURE_STATE_FILE")"
else
  FIXTURE_MODE="$(stat -c '%a' "$FIXTURE_STATE_FILE")"
fi
[ "$FIXTURE_MODE" = "600" ] \
  || fail "fixture 식별 파일 mode가 600이 아닙니다"
grep -Fqx 'RUN_ID=aof-contract' "$FIXTURE_STATE_FILE" || fail "fixture run ID가 다릅니다"
grep -Fqx 'ITEM_KEY=upbid:recovery:test:aof-contract:item' "$FIXTURE_STATE_FILE" \
  || fail "fixture item key가 다릅니다"
grep -Fqx 'STREAM_KEY=upbid:recovery:test:aof-contract:stream' "$FIXTURE_STATE_FILE" \
  || fail "fixture stream key가 다릅니다"
grep -Fqx 'GROUP=upbid-recovery' "$FIXTURE_STATE_FILE" || fail "fixture group이 다릅니다"
grep -Fqx 'STREAM_ID=1750000000000-0' "$FIXTURE_STATE_FILE" \
  || fail "fixture stream ID가 다릅니다"
if grep -Eiq 'password|secret|auth' "$FIXTURE_STATE_FILE"; then
  fail "fixture 식별 파일에 인증 정보가 있습니다"
fi

grep -Fq 'HSET upbid:recovery:test:aof-contract:item marker aof-contract currentPrice 10000 leaderUserId 342' \
  "$FAKE_REDIS_LOG" || fail "fixture Hash가 생성되지 않았습니다"
grep -Fq 'XGROUP CREATE upbid:recovery:test:aof-contract:stream upbid-recovery 0 MKSTREAM' \
  "$FAKE_REDIS_LOG" || fail "fixture Consumer Group이 생성되지 않았습니다"
grep -Fq 'XADD upbid:recovery:test:aof-contract:stream * type BID_ACCEPTED requestId recovery-aof-contract itemId 342 amount 10000' \
  "$FAKE_REDIS_LOG" || fail "fixture Stream 레코드가 생성되지 않았습니다"
grep -Fq 'XREADGROUP GROUP upbid-recovery verifier COUNT 1 STREAMS upbid:recovery:test:aof-contract:stream >' \
  "$FAKE_REDIS_LOG" || fail "fixture 레코드가 PEL로 이동하지 않았습니다"

: > "$FAKE_REDIS_LOG"
PATH="$FAKE_BIN:$PATH" \
FAKE_REDIS_LOG="$FAKE_REDIS_LOG" \
FAKE_REDIS_DATA_DIR="$FAKE_REDIS_DATA_DIR" \
FAKE_EXISTS_VALUE=1 \
REDISCLI_AUTH=test-password \
REDIS_CA_CERT="$TEST_ROOT/ca.crt" \
FIXTURE_STATE_FILE="$FIXTURE_STATE_FILE" \
  "$VERIFY" >/dev/null

if grep -Eq ' (CONFIG SET|HSET|XADD|XACK|XDEL|DEL|UNLINK)( |$)' "$FAKE_REDIS_LOG"; then
  fail "읽기 전용 검증 스크립트가 Redis를 변경했습니다"
fi

README="$ROOT/README.md"
[ -f "$README" ] || fail "Redis 운영 README가 없습니다"
BACKUP_LINE="$(grep -nF './install-persistence-config.sh --backup-only' "$README" | head -n1 | cut -d: -f1)"
ACTIVATE_LINE="$(grep -nF './activate-aof.sh' "$README" | head -n1 | cut -d: -f1)"
INSTALL_LINE="$(grep -nF './install-persistence-config.sh' "$README" \
  | grep -v -- '--backup-only' | head -n1 | cut -d: -f1)"
[ "$BACKUP_LINE" -lt "$ACTIVATE_LINE" ] && [ "$ACTIVATE_LINE" -lt "$INSTALL_LINE" ] \
  || fail "README의 RDB→AOF 전환 순서가 안전하지 않습니다"

echo "PASS: Redis persistence 전체 계약"
