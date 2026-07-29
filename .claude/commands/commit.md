---
description: 팀 컨벤션에 맞춰 변경사항을 커밋한다
argument-hint: [커밋할 내용 힌트 (선택)]
allowed-tools: Bash(git status:*), Bash(git diff:*), Bash(git add:*), Bash(git commit:*), Bash(git log:*), Bash(git restore:*), Read, Write, Grep, Glob
---

# 커밋

추가 지시: **$ARGUMENTS**

## 1. 변경사항 파악

먼저 저장소 루트를 기준으로 고정한다. `backend/`나 `frontend/` 안에서
실행하면 `git status`가 `../.claude/...` 같은 상대경로를 출력해
스테이징할 때 헷갈린다.

    R=$(git rev-parse --show-toplevel)
    git -C "$R" status --short
    git -C "$R" diff
    git -C "$R" diff --staged

이후 모든 git 명령에 `-C "$R"`를 붙이고, 경로는 루트 기준으로 쓴다.

**diff를 실제로 읽는다.** 파일명만 보고 요약하지 않는다.
최근 커밋(`git log --oneline -5`)으로 현재 스타일을 확인한다.

## 2. 커밋 분리 판단

성격이 다른 변경이 섞여 있으면 **나눠서 커밋한다.** 예를 들어
기능 구현과 무관한 포맷 변경, 서로 다른 도메인의 수정은 분리한다.

되돌릴 단위가 다르면 커밋도 다르다. 판단이 서지 않으면 사용자에게 묻는다.

## 3. 메시지 작성

형식: `[영역] 타입: 요약`  (콜론 앞에 공백 없음)

**영역** — 변경된 경로로 판단한다.

| 경로 | 영역 |
|---|---|
| `backend/` 만 | `BE` |
| `frontend/` 만 | `FE` |
| 양쪽 또는 루트(`.github/`, `.claude/`, `CLAUDE.md`, `.gitignore`) | `ALL` |

브랜치명에는 `Infra`를 쓰지만 **커밋 prefix에는 `BE`/`FE`/`ALL`만 쓴다**
(기존 이력 기준. `[ALL] chore: PR-Agent 기반 AI 코드 리뷰 설정`).

**타입** — `feat` / `fix` / `refactor` / `chore` / `test` / `docs`

**요약** — 한글, 명사형 종결. 무엇을 했는지 드러나게.
"수정" 같은 모호한 표현 대신 "입찰 단위 검증 추가"처럼 쓴다.

본문이 필요하면(왜 그렇게 했는지, 트레이드오프) 빈 줄 뒤에 덧붙인다.
단순한 변경이면 제목 한 줄로 끝낸다. 본문을 억지로 만들지 않는다.

**제목에 핵심을 담는다.** 본문에 "무엇을 했는지"를 나열하지 않는다 —
diff에 이미 드러난다. 본문은 diff만 봐서는 알 수 없는 "왜"가 있을 때만,
그것도 여러 줄로 늘어놓지 말고 핵심 이유 한 줄 정도로 짧게 쓴다.

**`Co-Authored-By` 등 AI 공동 작성자 트레일러를 붙이지 않는다.**
커밋 author는 실제 작업자이며, 도구 이름을 이력에 남기지 않는다.

## 4. 커밋

메시지는 **반드시 파일로 작성해 `-F`로 넘긴다.**

    git commit -F {임시파일}

`-m`과 heredoc은 쓰지 않는다. 메시지에 `git reset --hard`,
`docker compose down -v` 같은 문자열이 들어가면 금지 명령어 차단 hook이
실행이 아닌 텍스트를 오탐해 커밋이 막힌다.

## 5. 커밋 전 확인

- `.env`, 토큰, 개인정보가 포함되지 않았는지 확인한다
- 현재 브랜치가 `main`이나 `dev`면 **중단하고** 알린다.
  컨벤션상 기능 브랜치에서 작업한다 (`main ← dev ← 기능 브랜치`)
- `git add .` 대신 의도한 경로만 명시해 스테이징한다

## 6. 보고

커밋 해시와 제목을 보여준다. 여러 개면 전부 나열한다.
푸시는 **하지 않는다.** 필요하면 사용자가 지시한다.
