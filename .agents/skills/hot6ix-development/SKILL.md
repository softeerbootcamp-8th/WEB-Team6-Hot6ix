---
name: hot6ix-development
description: Hot6ix 실시간 경매 서비스의 프론트엔드·Spring 백엔드 기능 구현과 리뷰를 위한 팀 전용 개발 지침. API, 인증, 입찰, 실시간 이벤트, 경매 상태, DTO, JPA, UI 상태, 테스트 또는 공용 계약을 다루는 작업에 사용한다.
---

# Hot6ix Development

저장소 루트와 작업 영역의 `AGENTS.md` 및 `CLAUDE.md`를 먼저 읽는다.
아래 canonical reference 중 작업에 필요한 문서만 읽는다. Claude와 Codex가
동일한 규칙을 사용하도록 내용을 복제하지 않고 공유한다.

- 서비스 기능과 도메인 경계:
  `.claude/skills/hot6ix-development/references/domain.md`
- API, 인증, 응답 또는 실시간 이벤트:
  `.claude/skills/hot6ix-development/references/contracts.md`
- Spring 구현 또는 리뷰:
  `.claude/skills/hot6ix-development/references/backend.md`
- 프론트엔드 구현 또는 리뷰:
  `.claude/skills/hot6ix-development/references/frontend.md`
- Git, 환경 변수, PR 또는 위험 작업:
  `.claude/skills/hot6ix-development/references/workflow.md`

## 작업 절차

1. 관련 reference와 기존 코드·테스트·API 명세를 읽는다.
2. 공용 계약 영향을 먼저 식별한다.
3. 최소 범위로 구현한다.
4. 변경 영역과 통합 경계를 검증한다.
5. 변경 내용, 계약 영향, 검증 결과와 남은 위험을 보고한다.

불명확한 계약을 한쪽 영역의 편의로 결정하지 않는다. Notion API 명세가
초안 또는 검토 중이면 확정 여부를 확인한다.
