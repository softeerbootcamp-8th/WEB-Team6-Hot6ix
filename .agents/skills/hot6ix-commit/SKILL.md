---
name: hot6ix-commit
description: Hot6ix 저장소의 변경사항을 팀 컨벤션에 맞게 분석·분리·스테이징하여 안전하게 커밋한다. 사용자가 커밋, commit, 변경사항 저장, 커밋 메시지 작성을 요청할 때 사용한다.
---

# Hot6ix Commit

`.claude/commands/commit.md`를 완전히 읽고 canonical workflow로 따른다.
Claude 전용 표현은 다음처럼 Codex에 맞게 해석한다.

- `$ARGUMENTS`는 사용자가 함께 제공한 커밋 힌트다.
- `Read`, `Write`, `Grep`, `Glob`, `Bash`는 Codex의 동등한 파일·검색·셸
  도구를 뜻한다.
- AI 공동 작성자 trailer가 필요하면 Claude 이름을 복사하지 말고
  `Co-Authored-By: Codex <noreply@openai.com>`을 사용한다.

실제 diff를 읽고, 현재 브랜치가 `main` 또는 `dev`면 중단하며, 의도한
경로만 스테이징한다. 커밋 메시지는 임시 파일과 `git commit -F`를 사용한다.
푸시는 사용자가 별도로 요청하지 않는 한 수행하지 않는다.
