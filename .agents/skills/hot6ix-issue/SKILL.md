---
name: hot6ix-issue
description: Hot6ix 팀 템플릿과 브랜치·label·assignee 규칙에 맞춰 GitHub 이슈를 생성한다. 사용자가 이슈 생성, GitHub issue 등록, 작업 티켓 생성을 요청할 때 사용한다.
---

# Hot6ix Issue

`.claude/commands/issue.md`를 완전히 읽고 canonical workflow로 따른다.
Claude 전용 표현은 다음처럼 Codex에 맞게 해석한다.

- `$ARGUMENTS`는 사용자가 설명한 작업 범위와 assignee 힌트다.
- `Read`, `Write`, `Grep`, `Glob`, `Bash`는 Codex의 동등한 도구를 뜻한다.
- GitHub 연결 도구가 저장소·label·이슈 조회와 생성을 지원하면 우선
  사용하고, 부족한 작업은 `gh`로 수행한다.

외부 상태를 바꾸기 전에 인증, 실제 label 목록, 제목·본문·assignee를
확인한다. 이슈 생성 후 번호를 반영해 본문의 브랜치명을 확정하고, 결과를
보고한 다음 브랜치 생성 여부는 사용자에게 묻는다.
