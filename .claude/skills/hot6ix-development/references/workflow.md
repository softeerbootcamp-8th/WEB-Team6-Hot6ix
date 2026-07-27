# Workflow and Safety

## Git

- 흐름: `main ← dev ← be/fe/feat/{issue}-{feature}`
- 커밋: `[BE] <type>: <summary>` 또는 `[FE] <type>: <summary>`
- PR: 관련 이슈, 작업 개요, 고민과 해결, 리뷰 요청
- 최소 2명 리뷰

## 환경

- 실제 값은 `.env` 또는 secret 저장소에서 관리한다.
- `.env.example`에는 key만 기록한다.
- local/prod 설정을 분리한다.
- 백엔드 secret을 프론트 빌드 환경에 전달하지 않는다.
- 환경 변수 추가 시 example, README, 배포 설정을 함께 확인한다.
- 기존 package manager와 lockfile을 유지한다.

## 사람 확인이 필요한 변경

- 인증·인가
- API 요청·응답
- 실시간 방식과 payload
- DB schema와 migration
- 입찰 동시성 전략
- 환경 변수·배포
- 주요 의존성·공용 상태 관리·디자인 시스템

## 금지

- secret, token, 개인정보 출력·커밋
- `DROP`, `TRUNCATE`, `flyway clean`
- 조건 없는 `DELETE`·`UPDATE`
- `docker compose down -v`
- `git push --force`, `git reset --hard`, `git clean`
- 테스트 삭제·약화
- 관련 없는 리팩터링·전체 포맷 변경

## 완료 보고

- 변경 기능과 파일
- 영향 영역
- API·DB·이벤트 계약 영향
- 테스트·lint·타입 검사·빌드 결과
- 미검증 사항과 남은 위험
