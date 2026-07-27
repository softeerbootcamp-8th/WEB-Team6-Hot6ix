# Hot6ix Repository

Hot6ix는 링크·QR로 참여하는 SNS 연계 실시간 경매 서비스다.
이 문서는 프론트엔드와 백엔드를 함께 관리하는 저장소의 공통 규칙이다.
영역별 상세 규칙은 하위 `CLAUDE.md`와
`.claude/skills/hot6ix-development/`를 필요할 때 참고한다.

## 공통 필수 규칙

- 작업 전에 관련 코드·테스트·문서를 읽고 최소 범위만 수정한다.
- API Method, Path, 요청·응답, 권한, enum, 날짜·금액 형식은 공용 계약이다.
- DB schema, 인증 방식, 실시간 이벤트 payload 변경은 양쪽 영향과 배포 순서를 확인한다.
- 프론트는 화면 제어만으로 권한을 보장하지 않고, 백엔드는 실제 권한을 검증한다.
- DB를 경매 상태의 Source of Truth로 사용한다.
- 실시간 성공 상태는 서버 커밋 전 확정하지 않는다.
- 기존 빌드 도구, lockfile, formatter, lint 설정을 유지한다.
- 새 의존성이나 주요 구조 변경은 근거와 영향 범위를 제시하고 팀 확인을 받는다.
- 비밀값·토큰·개인정보를 코드, 로그, 클라이언트에 노출하지 않는다.

## 영역별 책임

- Frontend: 화면 조합, 사용자 상태, API 소비, 실시간 연결과 재연결, 접근성
- Backend: 비즈니스 규칙, 권한, 트랜잭션, 영속성, 동시성, 이벤트 발행
- Infra: 환경 설정, Docker, CI/CD, 배포와 운영
- 공용 계약 변경 시 프론트·백엔드·문서·테스트를 함께 갱신한다.

## 금지

- 팀 확인 없는 API·DB·인증·이벤트 계약 변경
- 새 패키지 관리자나 lockfile 혼용
- 서버 응답 전 입찰·낙찰·마감을 성공으로 확정
- 정리되지 않는 timer, listener, 실시간 subscription
- 테스트 삭제 또는 검증 완화로 CI 통과
- 관련 없는 대규모 리팩터링이나 전체 포맷 변경
- `DROP`, `TRUNCATE`, `flyway clean`, `docker compose down -v`
- `git push --force`, `git reset --hard`, `git clean`

위 명령 중 일부는 `.claude/hooks/block-forbidden-commands.sh`가 자동 차단한다.
다만 이는 **1차 방어선이며 보증이 아니다.** 훅은 Bash 명령 문자열만 보므로
파일 안의 SQL(`mysql < migrate.sql`), Gradle 태스크가 감싼 실행, 스크립트 경유
실행은 잡지 못한다. 최종 방어선은 DB 권한 분리, 백업, 브랜치 보호 규칙이다.

## 검증과 완료 보고

- 변경한 영역의 테스트, lint, 타입 검사, 빌드를 실행한다.
- 공용 계약 변경은 프론트–백엔드 통합 흐름까지 검증한다.
- 완료 시 변경 영역, 계약 영향, 검증 결과, 미검증 사항을 보고한다.

## 상세 규칙 라우팅

`.claude/skills/hot6ix-development/`에서 작업에 필요한 문서만 읽는다.

- 서비스 범위: `.claude/skills/hot6ix-development/references/domain.md`
- API·인증·실시간 계약: `.claude/skills/hot6ix-development/references/contracts.md`
- Spring 백엔드: `.claude/skills/hot6ix-development/references/backend.md`
- 프론트엔드: `.claude/skills/hot6ix-development/references/frontend.md`
- 협업·Git·환경·안전: `.claude/skills/hot6ix-development/references/workflow.md`
