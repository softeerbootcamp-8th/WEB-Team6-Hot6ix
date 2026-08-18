# Redis Live State 트랜잭션 경계 설계

## 배경

Redis Live State 조회는 진행 중 물품을 MySQL에서 조회한 뒤 Redis Seed 상태와
라이브 상태를 확인한다. 기존 구현은 읽기 트랜잭션 안에서 이 Redis 네트워크 I/O를
수행하므로 Redis가 지연되는 동안 JDBC 커넥션도 반환되지 않는다. HikariCP 풀이
고갈되면 Redis와 무관한 DB 요청까지 대기하는 연쇄 장애가 발생할 수 있다.

## 목표

- MySQL 조회가 끝나면 Redis 호출 전에 DB 트랜잭션을 종료한다.
- Redis Seed 복구에 필요한 JPA 엔티티를 트랜잭션 밖으로 노출하지 않는다.
- 기존 Live State 응답, Seed 멱등성 및 예외 계약을 유지한다.

## 구조

### Live State 조회

`AuctionLiveStateService`는 기존 호출자의 트랜잭션도 명시적으로 중단하는
오케스트레이터가 된다. 별도 조회
컴포넌트가 짧은 읽기 트랜잭션에서 진행 중 물품 ID를 불변 목록으로 반환한다.
오케스트레이터는 그 트랜잭션이 끝난 뒤 Redis 초기화와 Live State 조회를 수행한다.

### Redis Seed 초기화

`AuctionRedisInitializer`도 `NOT_SUPPORTED` 전파로 기존 호출자의 트랜잭션을
중단한다. 별도 Seed 스냅샷 로더가
다음 DB 작업만 짧은 읽기 트랜잭션으로 수행한다.

1. 물품의 진행 상태와 경매방 ID 조회
2. Seed가 없을 때만 물품·판매자·참여자·리더보드를 불변 `AuctionRedisSeed`로 변환

`isSeedReady`와 `seed`는 각 로더 호출이 반환되어 트랜잭션이 종료된 뒤 실행한다.
두 DB 조회 사이에 물품이 마감될 수 있으므로 두 번째 조회에서도 `IN_PROGRESS`를
다시 확인하고, 아니면 Seed를 만들지 않는다.

## 테스트

- Spring 트랜잭션 프록시가 적용된 서비스에서 Redis 의존성을 호출할 때 실제 DB
  트랜잭션이 비활성 상태인지 검증한다.
- 준비된 Seed는 참여자·리더보드 DB 스냅샷을 다시 읽지 않는 기존 동작을 유지한다.
- 조회 사이에 마감된 물품은 Redis에 새로 Seed하지 않는 기존 동작을 유지한다.

## 제외 범위

물품별 Redis 호출을 파이프라이닝하거나 일괄 조회하는 최적화는 이번 변경에 포함하지
않는다. 이번 변경은 DB 커넥션이 Redis 지연에 종속되지 않도록 트랜잭션 경계를
분리하는 데만 집중한다.
