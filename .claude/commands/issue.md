---
description: 팀 컨벤션에 맞춰 GitHub 이슈를 생성한다
argument-hint: <작업 내용 설명>
allowed-tools: Bash(gh issue:*), Bash(gh auth:*), Bash(git branch:*), Bash(git switch:*), Bash(git log:*), Bash(git status:*), Read, Grep, Glob
---

# 이슈 생성

사용자 요청: **$ARGUMENTS**

`.github/ISSUE_TEMPLATE/issue_template.md` 형식과 팀 컨벤션에 맞춰 이슈를 생성한다.

## 사전 확인

`gh auth status`로 인증을 확인한다. 실패하면 아래를 안내하고 중단한다.

    brew install gh && gh auth login

## 1. 제목 결정

형식: `[영역] 타입 : 이슈 이름`  (콜론 양쪽에 공백, 템플릿 형식 그대로)

- 영역: `BE` / `FE` / `ALL`(양쪽 공통) / `Infra`(CI·배포·개발도구)
- 타입: `feat` / `fix` / `bug` / `refactor` / `chore` / `test` / `docs`

요청 내용만으로 영역·타입이 애매하면 추측하지 말고 사용자에게 묻는다.
이름은 무엇을 하는지 드러나게 쓴다. "API 작업" 대신 "경매방 생성 API 구현".

예: `[BE] feat : 경매방 생성 API 구현`

## 2. 본문 작성

템플릿 두 섹션을 채운다. 인용문(`>`) 안내 문구는 지우고 실제 내용을 넣는다.

```markdown
## 🌿 Branch Name

`{브랜치명}`

---

## 📄 상세 내용

- 작업 내용 1
- 작업 내용 2
```

**상세 내용**은 구현 대상과 작업 범위를 항목으로 쓴다. 요청이 짧으면
관련 코드·문서(`.claude/skills/hot6ix-development/references/`)를 읽고
필요한 작업을 구체화한다. 추측으로 범위를 부풀리지 않는다.

**브랜치명** 형식: `{영역}/{타입}/{이슈번호}-{기능}`

- 영역은 소문자 (`be`, `fe`), 단 인프라는 `Infra` (기존 이력 기준)
- 기능은 영문 kebab-case
- 예: `be/feat/7-auction-room-create`, `Infra/chore/5-claude-code-setup`

이슈 번호는 생성 전에는 모르므로, 먼저 브랜치명 자리를 `TBD`로 두고
이슈를 만든 뒤 실제 번호로 채워 넣는다.

## 3. 생성

본문은 임시 파일에 쓰고 `--body-file`로 넘긴다. 인라인 `--body`는
따옴표 처리가 깨지기 쉽고, 금지 명령어 차단 hook의 오탐도 유발한다.

    gh issue create --title "{제목}" --body-file {임시파일}

생성된 번호 `N`을 받아 본문의 `TBD`를 실제 브랜치명으로 교체한다.

    gh issue edit N --body-file {수정한 임시파일}

## 4. 보고

생성된 이슈 번호·URL·확정된 브랜치명을 알린다.
그리고 브랜치를 지금 만들지 물어본다. 만든다고 하면:

    git switch -c {브랜치명}

현재 브랜치가 `dev`가 아니면 어디서 분기할지 먼저 확인한다.
컨벤션상 기능 브랜치는 `dev`에서 딴다 (`main ← dev ← 기능 브랜치`).
