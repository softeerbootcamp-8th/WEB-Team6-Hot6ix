---
description: 지정한 커밋부터 현재까지의 변경사항으로 팀 컨벤션에 맞는 PR을 생성한다
argument-hint: <시작 커밋 SHA> [assignee 지정 시 함께 언급]
allowed-tools: Bash(gh pr:*), Bash(gh label:*), Bash(gh auth:*), Bash(git log:*), Bash(git diff:*), Bash(git show:*), Bash(git status:*), Bash(git branch:*), Bash(git rev-parse:*), Bash(git rev-list:*), Bash(git push:*), Read, Write, Grep, Glob
---

# PR 생성

시작 커밋: **$1**

`$1` 커밋을 **포함해서** 현재 HEAD까지의 변경사항으로 PR을 만든다.
`.github/PULL_REQUEST_TEMPLATE.md` 형식을 따른다.

## 1. 사전 확인

`gh auth status`로 인증을 확인한다. 실패하면 `gh auth login`을 안내하고 중단한다.

인자가 없으면 최근 커밋 목록(`git log --oneline -15`)을 보여주고
어느 커밋부터 묶을지 물어본다. 임의로 정하지 않는다.

`git rev-parse --verify $1^{commit}` 으로 SHA가 유효한지 확인한다.

## 2. 범위 계산

`$1`을 포함해야 하므로 범위는 `$1^..HEAD` 다.

    git log --oneline $1^..HEAD

`$1`이 최초 커밋이면 `$1^`가 없어 실패한다. 이때는 `--root`를 쓴다.

    git log --oneline --root HEAD

범위에 잡힌 커밋 목록을 사용자에게 먼저 보여주고, 의도한 범위가 맞는지
확인받은 뒤 진행한다. 범위를 잘못 잡으면 남의 커밋까지 PR에 들어간다.

## 3. 변경사항 파악

    git diff --stat $1^..HEAD
    git diff $1^..HEAD

커밋 메시지만 요약하지 말고 실제 diff를 읽는다. 커밋 메시지에 안 적힌
설계 판단이나 트레이드오프가 본문의 "주요 고민 및 해결 과정"에 들어가야 한다.

## 4. 관련 이슈 번호 찾기

순서대로 시도한다.

1. 현재 브랜치명에서 추출 — `be/feat/7-xxx` → `7`
2. 범위 내 커밋 메시지의 `#N` 참조
3. 못 찾으면 사용자에게 묻는다. 이슈 없이 진행하면 `Closes #` 줄은 비워 둔다

## 5. 제목

커밋 메시지와 같은 형식: `[영역] 타입: 요약`

- 영역: `BE` / `FE` / `ALL` / `Infra`
- 범위 내 커밋이 여러 영역에 걸치면 `ALL`
- 예: `[BE] feat: 경매방 생성 API 구현`

## 6. 본문

템플릿 4개 섹션을 채운다. 인용문(`>`) 안내 문구와 예시 항목은 지운다.
스크린샷 섹션은 UI 변경이 있을 때만 남기고, 없으면 통째로 지운다.

```markdown
## 📌 관련 이슈

- Closes #{번호}

---

## ✨ 작업 개요

- {무엇을 했는지 항목별로}

---

## 🤔 주요 고민 및 해결 과정

- {고민한 지점}
- {선택한 해결과 그 이유}

---

## 🙏 리뷰 요청 및 전달사항

- {중점적으로 봐줬으면 하는 부분}
- {공유할 사항 — 미검증 항목, 남은 위험}
```

**작업 개요**는 커밋 제목 나열이 아니라 변경의 의미를 쓴다.

**주요 고민 및 해결 과정**은 diff에서 판단이 개입된 지점을 찾아 쓴다.
왜 그렇게 했는지가 없으면 리뷰어가 알 수 없는 내용을 우선한다.
고민할 지점이 없던 단순 작업이면 억지로 만들지 말고 그렇게 쓴다.

**리뷰 요청**에는 검증하지 못한 부분과 남은 위험을 반드시 포함한다
(`references/workflow.md` 완료 보고 기준).

## 7. assignee · 리뷰어 · label

**assignee** — 사용자가 지정하지 않으면 본인으로 한다. `--assignee @me`

**리뷰어** — **자동으로 지정하지 않는다.** 이 저장소는 부트캠프 조직에
속해 있어 collaborator 목록에 팀원이 아닌 사람까지 포함된다. 잘못 지정하면
관계없는 사람에게 리뷰 요청이 간다. 사용자가 명시한 경우에만 지정한다.

**label** — **타입 1개 + 영역 label**을 붙인다.
label 이름에 이모지가 붙어 있고 `--label`은 정확히 일치해야 하므로
이모지를 포함해 그대로 넘긴다.

| 타입 | label |   | 영역 | label |
|---|---|---|---|---|
| feat | `✨feat` |   | 백엔드만 | `BE` |
| fix / bug | `🛠️fix` |   | 프론트만 | `FE` |
| refactor | `🌿refactor` |   | 양쪽 | `BE`, `FE` |
| chore | `📃chore` |   | 루트 전용 | 없음 |
| test | `🧪test` |   | | |
| docs | `📃chore` (전용 없음) |   | | |

위 표는 작성 시점 기준이다. **실행할 때 실제 목록을 다시 확인한다.**

    gh label list --limit 100 --json name,description

마땅한 게 없으면 label 없이 진행하고 알린다. **label을 새로 만들지 않는다.**

## 8. 푸시와 생성

브랜치가 원격에 없으면 먼저 푸시한다.

    git push -u origin HEAD

본문은 임시 파일에 쓰고 `--body-file`로 넘긴다. 인라인 `--body`는
따옴표 처리가 깨지기 쉽고, 금지 명령어 차단 hook의 오탐도 유발한다.

    gh pr create --base dev --title "{제목}" --body-file {임시파일} \
      --assignee @me --label "{label1},{label2}"

**base는 `dev`** 다 (`main ← dev ← 기능 브랜치`). `main`으로 열지 않는다.
현재 브랜치가 `dev`나 `main`이면 PR을 만들 수 없으므로 중단하고 알린다.

label 지정이 실패해도 **PR 본체는 살린다.** 실패하면 PR을 먼저 만든 뒤
`gh pr edit`으로 재시도하고, 그래도 안 되면 사실대로 알린다.

## 9. 보고

PR URL과 함께 지정된 assignee·label을 알린다.
label을 못 붙였으면 이유를 알린다.

**리뷰어는 지정하지 않았음을 알리고, 직접 지정하도록 안내한다.**
팀 컨벤션상 최소 2명의 리뷰가 필요하다.

    gh pr edit {번호} --add-reviewer {아이디},{아이디}
