# Spring Backend

## 구조

- Java 21, Spring Boot 4.x, JPA, MySQL
- Redis는 자동 도입하지 않고, 목적·TTL·fallback을 명시할 때만 파생 상태 저장소로 사용
- 도메인형 패키지
- `Controller → Service → Repository`
- 트랜잭션은 Service에서만 관리
- `spring.jpa.open-in-view=false`
- JPA 객체 연관관계를 기본으로 하되 N+1과 락 영향을 확인

Controller는 요청·`@Valid`·응답만 담당한다.
Service는 권한·상태 전이·비즈니스 검증·트랜잭션을 담당한다.
Repository는 영속성 접근만 담당한다.

## DTO·예외

- Entity 직접 반환 금지
- Request/Response 분리
- DTO는 `record`
- 이름: `{Domain}{Action}RequestDto`, `{Domain}{Action}ResponseDto`, `{Domain}{Action}Dto`
- 변환: `from`, `of`
- 필드 검증: Bean Validation
- 비즈니스 검증: Service
- `ApplicationException` + 도메인별 `ErrorType`
- `GlobalExceptionHandler`에서 공통 실패 응답 변환

## JPA

- Lazy Loading을 Controller까지 넘기지 않는다.
- N+1을 EAGER로 숨기지 않는다.
- 불필요한 양방향 관계와 `CascadeType.ALL`을 피한다.
- soft delete 이력과 참조 무결성을 보존한다.
- 조회에는 필요에 따라 fetch join, EntityGraph, 전용 DTO를 사용한다.

## 테스트

- JUnit 5 + Mockito
- 테스트 메서드명은 한글
- 권한, 입찰 단위, 마감 후 입찰, 동시 입찰, 3개 병렬 제한,
  Soft Close, 이벤트 발행 시점, 낙찰 멱등성, 예외 응답을 우선 검증한다.

## 금지

- 필드 주입, `System.out`, `printStackTrace`
- Controller 비즈니스 로직
- 예외 무시
- 트랜잭션 전 성공 이벤트
- 근거 없는 캐시와 비동기화
