# API and Realtime Contracts

## API

- prefix: `/api/v1`
- URL: kebab-case, 복수 명사
- 명세의 Method, Path, 인증 수준을 유지한다.
- 권한 수준: 불필요, 회원, 판매자, 소유자, 참여자/소유자

데이터 없는 성공 응답:

    {
      "success": true,
      "message": "성공 응답입니다."
    }

데이터 있는 성공 응답:

    {
      "success": true,
      "message": "성공 응답입니다.",
      "data": {
        "id": 1,
        "name": "홍길동"
      }
    }

에러 응답:

    {
      "success": false,
      "code": 2003,
      "message": "요청한 리소스를 찾을 수 없습니다."
    }

Validation 에러 응답:

    {
      "success": false,
      "code": 2002,
      "message": "잘못된 요청입니다.",
      "errors": [
        { "field": "name", "message": "이름은 필수입니다." },
        { "field": "email", "message": "이메일 형식이 아닙니다." }
      ]
    }

위 예시는 읽기 쉬운 순서로 적은 것이고, **실제 직렬화 순서는 다르다.**
`CommonResponse` record의 필드 선언 순서가 그대로 JSON 순서가 된다.

    success → data → code → message → errors

`@JsonInclude(NON_NULL)`이 붙어 있어 null 필드는 응답에서 빠진다.
성공 응답에 `code`와 `errors`가 없고, 데이터 없는 성공에 `data`가 없는 이유다.
JSON 객체의 키 순서는 계약이 아니므로 파싱에는 영향이 없다.

## 페이지네이션

**cursor 방식으로 확정됐다.** `CursorPageResponse`가 이미 구현돼 있다.

    {
      "content": [],
      "nextCursor": 42,
      "hasNext": true,
      "size": 20
    }

- `nextCursor`가 `null`이면 `hasNext`는 `false`다 (`of` 팩터리가 그렇게 만든다)
- `size`는 요청한 크기가 아니라 **실제 반환된 `content`의 개수**다
- 목록 API는 offset 방식을 새로 도입하지 않는다

공용 계약에는 Method, Path, 필드, null, enum, 권한, status/code, 시간대,
금액, 페이지네이션, 파일 제한, 실시간 채널과 payload가 포함된다.

## 계약 변경

1. 확정 명세인지 확인한다.
2. 프론트 화면, 백엔드 endpoint, 테스트 영향을 찾는다.
3. 하위 호환성과 배포 순서를 정한다.
4. 서버·클라이언트·Swagger·문서를 함께 수정한다.
5. 통합 흐름을 검증한다.

## 미확정 사항

다음 항목은 확정 명세나 팀 합의 없이 임의로 결정하지 않는다.

- 인증 방식과 세션 저장소
- SSE와 WebSocket 선택
- 진행 중 경매 판정 이외 Redis 사용 범위
- Transactional Outbox 도입 여부
- 영속성 스택(JPA·MySQL) 도입 시점과 마이그레이션 도구

## 실시간

- 경매 시작 전과 마감 Stream의 MySQL 반영 완료 후에는 MySQL이 판정 원본이다.
- `IN_PROGRESS`와 마감 Stream 반영 전 `CLOSING`에서는 Redis Lua가 판정 원본이다.
- 입찰 HTTP 201은 Redis Lua의 상태 변경, 멱등 결과 저장, MySQL 영속화 Stream 기록이
  한 실행에서 성공했다는 뜻이다. 해당 입찰의 MySQL 커밋 완료를 뜻하지 않는다.
- `BID_PLACED`, `SOFT_CLOSE_EXTENDED`, `ITEM_CLOSE_ADVANCED`, `ITEM_ENDED`(유찰 포함)는
  Redis 판정 직후 기존 SSE payload로 best-effort 발행한다.
- Redis-first SSE 발행 실패는 이미 승인된 입찰·연장·마감을 실패로 되돌리지 않는다.
  별도 SSE Stream을 두지 않으므로 Lua 성공 직후 프로세스가 종료되면 이벤트 한 건이
  누락될 수 있으며, 화면은 다음 이벤트·replay·상태 재조회로 복구한다.
- MySQL 커밋 후 DomainEvent는 낙찰 후보 생성 등 내부 후속 작업을 위해 유지하지만,
  Redis-first 이벤트를 SSE로 다시 발행하지 않는다.
- Redis-first 목록에 없는 성공 이벤트는 기존처럼 DB 커밋 후에만 발행한다.
- 방 단위와 물품 단위 채널의 책임을 구분한다.
- 클라이언트 timer는 종료 판정의 원본이 아니다.
- SSE/WebSocket 방식은 확정 명세와 기존 구현을 따른다.
- 재연결 replay 로 메울 수 없는 구간이 있으면 `SYSTEM_EVENTS_LOST`를 그 연결에만
  발행한다. 화면은 이 신호를 받으면 방 정보·물품 목록·물품 상세를 다시 조회한다.
  `EventType`에 없는 이름이고, `PARTICIPANT_COUNT_UPDATED`처럼 id 없이 나가
  `Last-Event-ID`에 영향을 주지 않는다. 유실된 이벤트 자체는 복구하지 않는다 —
  버퍼에서 밀려난 이벤트는 이름도 남지 않아 서버도 무엇이 사라졌는지 모른다.

주요 이벤트: 물품 시작, 입찰 성공/거절, 현재가, 리더보드, Soft Close,
물품 마감, 낙찰 결과, 방 종료.
