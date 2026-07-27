---
name: hot6ix-verify
description: Hot6ix 변경 영역을 감지해 백엔드 테스트와 프론트엔드 lint·타입 검사·빌드·포맷 검사를 실행하고 완료 보고로 정리한다. 사용자가 검증, 테스트, lint, build, CI 전 확인을 요청할 때 사용한다.
---

# Hot6ix Verify

`.claude/commands/verify.md`를 완전히 읽고 canonical workflow로 따른다.
Claude 전용 표현은 다음처럼 Codex에 맞게 해석한다.

- `$ARGUMENTS`는 `be`, `fe`, `all` 중 사용자가 지정한 검증 범위다.
- `Read`, `Grep`, `Glob`, `Bash`는 Codex의 동등한 도구를 뜻한다.

범위를 주지 않으면 변경 경로로 자동 감지한다. 한쪽 검증이 실패해도 다른
검증은 계속 실행한다. 실패를 숨기거나 테스트·규칙을 약화하지 않는다.
실행 결과, 변경 영역, 계약 영향, 미검증 사항과 남은 위험을 모두 보고한다.
