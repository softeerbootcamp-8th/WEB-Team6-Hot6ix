# Hot6ix Backend

Hot6ix는 링크·QR로 참여하는 SNS 연계 실시간 경매 서비스다.
이 문서는 Spring 백엔드의 상시 규칙만 정의한다. 상세 규칙은 작업에 맞춰
`.claude/skills/hot6ix-development/`를 참고한다.

## 기술과 구조

- Java 21, Spring Boot 4.x, JUnit 5를 기본 스택으로 사용한다.
- **영속성 계층(JPA·MySQL)이 도입되어 있다.** `build.gradle`에
  `spring-boot-starter-data-jpa`와 MySQL 드라이버가 있고, `application.yaml`에
  datasource 설정과 `spring.jpa.open-in-view=false`가 반영되어 있다.
  아래 JPA 관련 규칙은 지금부터 적용 대상이다.
<!-- - Redis는 자동 도입하지 않는다. 캐싱·rate limiting 등 목적, TTL, 장애 시 fallback을 명시할 때만 파생 상태 저장소로 도입한다. -->
- 도메인형 패키지와 `Controller → Service → Repository` 구조를 따른다.
- 트랜잭션은 Service에서만 관리한다.

## 필수 규칙

- 관련 코드·테스트·API 명세를 먼저 읽고 최소 범위만 수정한다.
- Entity를 요청·응답으로 사용하지 않는다.
- Request/Response DTO를 분리하고 `record`로 작성한다.
- 필드 형식은 DTO에서, 권한·상태 전이·입찰 규칙은 Service에서 검증한다.
- `ApplicationException`, 도메인별 `ErrorType`, `GlobalExceptionHandler`를 사용한다.
- 생성자 주입을 사용한다.
- API Method, Path, 응답, 권한, DB schema, 이벤트 payload를 임의 변경하지 않는다.
- DB 연결이 필요한 테스트(`@DataJpaTest` 등)는 로컬 개발 DB를 직접 쓰지 않고
  Testcontainers로 격리한다. 자세한 내용은 backend.md의 "테스트" 절 참고.

## 금지

- Controller의 비즈니스 로직 또는 트랜잭션
- `System.out.println()`, `e.printStackTrace()`, 필드 주입
- 무분별한 EAGER, `CascadeType.ALL`, 양방향 연관관계
- DB보다 Redis나 메모리를 원본으로 취급하는 구현
- 동시성 검증 없는 read-modify-write
- 팀 확인 없는 의존성·API·DB·인증·이벤트 계약 변경
- `DROP`, `TRUNCATE`, `flyway clean`, `docker compose down -v`
- `git push --force`, `git reset --hard`, `git clean`
- 요청받지 않은 대규모 리팩터링

## 검증과 완료 보고

- Gradle Wrapper와 저장소에 정의된 명령을 사용한다.
- 변경한 기능의 단위·통합 테스트를 실행한다.
- 완료 시 변경 내용, 계약 영향, 테스트 결과, 미검증 사항을 보고한다.

## 상세 규칙 라우팅

- 백엔드 구현: `.claude/skills/hot6ix-development/references/backend.md`
- API·실시간 계약: `.claude/skills/hot6ix-development/references/contracts.md`
- 서비스 도메인: `.claude/skills/hot6ix-development/references/domain.md`
- 협업·Git·안전: `.claude/skills/hot6ix-development/references/workflow.md`
