#!/usr/bin/env bash
# CLAUDE.md "금지" 목록의 파괴적 명령을 PreToolUse 단계에서 차단한다.
# stdin으로 hook 입력 JSON을 받고, 차단 시 permissionDecision=deny를 출력한다.
#
# ── 이 훅의 한계 (1차 방어선이며 보증이 아니다) ────────────────────────
# * Bash 도구의 명령 문자열만 검사한다. 다음은 잡지 못한다.
#   - 파일 안에 든 파괴적 SQL:  mysql < migrate.sql
#   - 태스크가 감싼 실행:        ./gradlew flywayMigrate, ./gradlew someTask
#   - Write/Edit 도구의 파일 조작 (matcher가 Bash라 대상이 아님)
#   - 스크립트 경유 실행:        ./deploy.sh (내부에서 무엇을 하든)
# * 최종 방어선은 DB 권한 분리, 백업, 브랜치 보호 규칙이다.
# ────────────────────────────────────────────────────────────────────
set -uo pipefail

# jq가 없으면 명령을 읽을 수 없다. 조용히 통과시키면 보호가 사라진 걸
# 아무도 모르므로, 경고를 띄워 드러낸다 (fail-open이되 조용하지 않게).
if ! command -v jq >/dev/null 2>&1; then
  printf '{"systemMessage":"%s"}\n' \
    "[경고] 금지 명령어 차단 훅 비활성: jq가 설치되지 않았습니다. brew install jq 로 설치하세요."
  exit 0
fi

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

# 명령어 위치: 문자열 시작이거나 셸 연산자(; & | 괄호) 직후.
# 이걸 앞에 붙여야 `echo 'do not use git clean'` 같은 인용문 안의 단어를
# 실행으로 오인하지 않는다.
CMDPOS='(^|[;&|(])[[:space:]]*'

# git 전역 옵션 (서브커맨드 앞에 올 수 있는 것들)
GOPT='((-[cC][[:space:]]+[^[:space:]]+|--(git-dir|work-tree|exec-path)=[^[:space:]]+|--no-pager|--bare|-p)[[:space:]]+)*'

# --- Git 이력·작업물 파괴 ---
# 서브커맨드 위치를 고정한다. `git commit -m 'chore: clean up'`은
# git 뒤가 commit이라 clean 규칙에 걸리지 않는다.

match "${CMDPOS}git[[:space:]]+${GOPT}clean([[:space:]]|;|$)" &&
  deny "CLAUDE.md 금지 명령: git clean. 추적되지 않는 파일이 복구 불가능하게 삭제된다."

# --force-with-lease는 허용한다 (--force 뒤에 - 가 오므로 매치되지 않음)
match "${CMDPOS}git[[:space:]]+${GOPT}push([[:space:]][^;|&]*)?[[:space:]](--force|-f)([[:space:]]|;|$)" &&
  deny "CLAUDE.md 금지 명령: git push --force. 필요하면 --force-with-lease를 팀 확인 후 사용한다."

# refspec 앞의 +도 강제 푸시다: git push origin +dev
match "${CMDPOS}git[[:space:]]+${GOPT}push([[:space:]][^;|&]*)?[[:space:]]\+[^[:space:]]+" &&
  deny "CLAUDE.md 금지 명령: refspec 강제 푸시(+). git push origin +branch 는 --force와 동일하다."

match "${CMDPOS}git[[:space:]]+${GOPT}reset([[:space:]][^;|&]*)?[[:space:]]--hard([[:space:]]|;|$)" &&
  deny "CLAUDE.md 금지 명령: git reset --hard. 작업 중인 변경이 복구 불가능하게 사라진다."

# --- 컨테이너 볼륨 삭제 ---
match "${CMDPOS}(docker[[:space:]]+compose|docker-compose)([[:space:]]|[^;|&])*down([[:space:]]|[^;|&])*(-v([[:space:]]|;|$)|--volumes)" &&
  deny "CLAUDE.md 금지 명령: docker compose down -v. 로컬 DB 볼륨이 삭제된다. -v 없이 실행한다."

# --- Flyway ---
# 명령어 위치의 `flyway ... clean`
match "${CMDPOS}flyway([[:space:]][^;|&]*)?[[:space:]]clean([[:space:]]|;|$)" &&
  deny "CLAUDE.md 금지 명령: flyway clean. migration 이력과 데이터가 전부 삭제된다."

# Gradle/Maven 태스크 형태는 인자 위치에 오므로 위치를 고정할 수 없다.
# 대신 공백 없는 형태만 매치해 산문에서의 오탐을 피한다.
match 'flyway[:_-]?clean([^[:alnum:]]|$)' &&
  deny "CLAUDE.md 금지 명령: flyway clean 태스크. migration 이력과 데이터가 전부 삭제된다."

# --- DB 파괴 ---
# DB 클라이언트를 실제로 호출할 때만 검사한다. 이 조건이 없으면
# 문서 작성, grep, SQL 파일 편집이 전부 막힌다.
DBCLIENT='(^|[;&|([:space:]])(mysql|mysqlsh|mysqladmin|mysqldump|mariadb|psql|sqlite3)([[:space:]]|$)'

# 나아가 -e / -c / --execute / --command 로 넘기는 SQL 인자 안에 있을 때만
# 잡는다. 같은 줄의 무관한 문자열(echo 등)에 걸리지 않는다.
SQLARG='(-e|-c|--execute|--command)[[:space:]]*["'"'"'][^"'"'"']*'

if match "$DBCLIENT"; then
  match "${SQLARG}drop[[:space:]]+(table|database|schema|index|view|user|role)" &&
    deny "CLAUDE.md 금지 명령: DROP. DB schema 변경은 팀 확인 후 사람이 직접 실행한다."

  match "${SQLARG}truncate[[:space:]]+(table|[[:alnum:]_\`]+)" &&
    deny "CLAUDE.md 금지 명령: TRUNCATE. 데이터 삭제는 팀 확인 후 사람이 직접 실행한다."
fi

exit 0
