---
description: 팀 컨벤션에 맞춰 GitHub 이슈를 생성한다
argument-hint: <작업 내용 설명> [assignee 지정 시 함께 언급]
allowed-tools: Bash(gh issue:*), Bash(gh label:*), Bash(gh api:*), Bash(gh auth:*), Bash(git branch:*), Bash(git switch:*), Bash(git log:*), Bash(git status:*), Read, Write, Grep, Glob
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

## 3. assignee와 label

**assignee** — 사용자가 지정하지 않으면 **본인**으로 설정한다.

    --assignee @me

다른 사람을 지정했으면 그 GitHub 아이디를 쓴다.

**label** — 저장소에 실제 존재하는 label 중에서 고른다.

    gh label list --limit 100 --json name,description

가져온 목록에서 이번 작업의 **타입**(feat/fix/bug/refactor/chore/docs)과
**영역**(BE/FE)에 해당하는 것을 고른다. 이름이 정확히 일치하지 않아도
의미가 맞으면 쓴다 (예: `enhancement` ← feat, `documentation` ← docs).

마땅한 label이 없으면 **label 없이 진행하고 그 사실을 알린다.**
label을 새로 만들지 않는다. 저장소 전체에 영향을 주는 변경이라
팀 확인이 필요하다. 만들면 좋을 label이 있으면 제안만 한다.

## 4. 생성

본문은 임시 파일에 쓰고 `--body-file`로 넘긴다. 인라인 `--body`는
따옴표 처리가 깨지기 쉽고, 금지 명령어 차단 hook의 오탐도 유발한다.

    gh issue create --title "{제목}" --body-file {임시파일} \
      --assignee @me --label "{label1},{label2}"

`--label`에 없는 label을 넘기면 명령 전체가 실패한다. 반드시 위에서
조회한 목록에 있는 이름만 쓴다.

생성된 번호 `N`을 받아 본문의 `TBD`를 실제 브랜치명으로 교체한다.

    gh issue edit N --body-file {수정한 임시파일}

## 5. 보고

생성된 이슈 번호·URL·확정된 브랜치명, 그리고 **설정된 assignee와 label**을
알린다. label을 못 붙였으면 이유(마땅한 label 없음)를 함께 알린다.

그리고 브랜치를 지금 만들지 물어본다. 만든다고 하면:

    git switch -c {브랜치명}

현재 브랜치가 `dev`가 아니면 어디서 분기할지 먼저 확인한다.
컨벤션상 기능 브랜치는 `dev`에서 딴다 (`main ← dev ← 기능 브랜치`).
