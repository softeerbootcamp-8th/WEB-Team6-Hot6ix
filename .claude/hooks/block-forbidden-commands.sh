#!/usr/bin/env bash
# CLAUDE.md "금지" 목록의 파괴적 명령을 PreToolUse 단계에서 차단한다.
# stdin으로 hook 입력 JSON을 받고, 차단 시 permissionDecision=deny를 출력한다.
set -uo pipefail

cmd="$(jq -r '.tool_input.command // empty' 2>/dev/null)"
[ -z "$cmd" ] && exit 0

deny() {
  jq -n --arg reason "$1" '{
    hookSpecificOutput: {
      hookEventName: "PreToolUse",
      permissionDecision: "deny",
      permissionDecisionReason: $reason
    }
  }'
  exit 0
}

match() { printf '%s' "$cmd" | grep -Eiq "$1"; }

# --- DB 파괴 ---
match '(^|[^[:alnum:]_-])drop[[:space:]]+(table|database|schema|index|view|user|role)([[:space:]]|;|$)' &&
  deny "CLAUDE.md 금지 명령: DROP. DB schema 변경은 팀 확인 후 사람이 직접 실행한다."

match '(^|[^[:alnum:]_-])truncate[[:space:]]+(table[[:space:]]|[[:alnum:]_`"]+[[:space:]]*;)' &&
  deny "CLAUDE.md 금지 명령: TRUNCATE. 데이터 삭제는 팀 확인 후 사람이 직접 실행한다."

# `flyway clean`, `flywayClean`(Gradle task), `flyway:clean`(Maven), `flyway -url=... clean`
match '(^|[^[:alnum:]_-])flyway([[:space:]:_-]*clean|[[:space:]][^;|&]*[[:space:]]clean)([^[:alnum:]]|$)' &&
  deny "CLAUDE.md 금지 명령: flyway clean. migration 이력과 데이터가 전부 삭제된다."

# --- 컨테이너 볼륨 삭제 ---
match '(docker[[:space:]]+compose|docker-compose)([[:space:]]|[^;|&])*down([[:space:]]|[^;|&])*(-v([[:space:]]|$)|--volumes)' &&
  deny "CLAUDE.md 금지 명령: docker compose down -v. 로컬 DB 볼륨이 삭제된다. -v 없이 실행한다."

# --- Git 이력·작업물 파괴 ---
# --force-with-lease는 허용한다.
match 'git([[:space:]]|[^;|&])*[[:space:]]push([[:space:]]|[^;|&])*[[:space:]](--force([[:space:]]|$)|-f([[:space:]]|$))' &&
  deny "CLAUDE.md 금지 명령: git push --force. 필요하면 --force-with-lease를 팀 확인 후 사용한다."

match 'git([[:space:]]|[^;|&])*[[:space:]]reset([[:space:]]|[^;|&])*[[:space:]]--hard([[:space:]]|$)' &&
  deny "CLAUDE.md 금지 명령: git reset --hard. 작업 중인 변경이 복구 불가능하게 사라진다."

match 'git([[:space:]]|[^;|&])*[[:space:]]clean([[:space:]]|$)' &&
  deny "CLAUDE.md 금지 명령: git clean. 추적되지 않는 파일이 복구 불가능하게 삭제된다."

exit 0
