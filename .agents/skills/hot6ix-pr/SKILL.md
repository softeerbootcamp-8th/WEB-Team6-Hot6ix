---
name: hot6ix-pr
description: 지정한 시작 커밋부터 현재 HEAD까지의 실제 diff를 분석해 Hot6ix 팀 템플릿과 규칙에 맞는 Pull Request를 생성한다. 사용자가 PR, pull request, 머지 요청 생성을 요청할 때 사용한다.
---

# Hot6ix Pull Request

`.claude/commands/pr.md`를 완전히 읽고 canonical workflow로 따른다.
Claude 전용 표현은 다음처럼 Codex에 맞게 해석한다.

- `$1`은 사용자가 지정한 시작 커밋 SHA다.
- `Read`, `Write`, `Grep`, `Glob`, `Bash`는 Codex의 동등한 도구를 뜻한다.
- GitHub 연결 도구가 PR 메타데이터나 생성 작업을 지원하면 우선 사용하고,
  커밋 범위·diff·push 등 로컬 Git 작업과 미지원 작업은 `git`/`gh`로 한다.

시작 SHA가 없으면 임의 선택하지 않는다. `$1`을 포함한 범위를 보여주고
사용자 확인을 받은 뒤, 실제 diff를 읽어 본문을 작성한다. base는 `dev`로
하고 리뷰어는 사용자가 명시한 경우에만 지정한다.
