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

## 날짜·시각

**모든 날짜·시각 응답에 타임존 오프셋이 실린다.** REST와 SSE 모두 같다.

    "endAt": "2026-08-04T12:52:25.949725+09:00"

- 필드 타입은 `OffsetDateTime`. 오프셋은 서버 시계의 존(`ZoneId.systemDefault()`)이라
  서버가 어느 시간대로 뜨든 값이 사실과 일치한다. `Asia/Seoul`을 하드코딩하지 않는다.
- 서버가 만드는 값(`LocalDateTime.now()`, DB에서 읽은 `LocalDateTime` 컬럼)은 응답
  경계에서 `ServerTime.toOffset(...)`으로 변환한다. 엔티티·서비스 내부는 여전히
  `LocalDateTime`이다 — 오프셋은 응답에만 실린다.
- SSE의 `ItemStartedDto.endedTime`, `SoftCloseExtendedDto.endedTime`은 REST의
  `endAt`과 반드시 같은 포맷이다. 프론트가 같은 필드로 합쳐 쓰기 때문에, 한쪽만
  바뀌면 소프트클로즈 연장 수신 시 카운트다운이 튄다.
- 프론트는 오프셋이 있으므로 서버 TZ와 무관하게 `new Date(iso)`를 그대로 쓴다.
  날짜 표시(`formatDate`/`formatTime`)는 보는 사람의 로컬 시간대로 그린다 — 이건
  의도한 동작이고 바꾸지 않는다.
- 기기 시계 자체가 틀어진 경우의 카운트다운 보정은 별도 이슈다. 이 계약은
  서버·클라이언트 시간대가 다른 경우만 다룬다.

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
- Redis의 구체적인 사용 범위
- Transactional Outbox 도입 여부
- 영속성 스택(JPA·MySQL) 도입 시점과 마이그레이션 도구

## 실시간

- DB 커밋 후에만 성공 이벤트를 발행한다.
- 방 단위와 물품 단위 채널의 책임을 구분한다.
- 클라이언트 timer는 종료 판정의 원본이 아니다.
- SSE/WebSocket 방식은 확정 명세와 기존 구현을 따른다.

주요 이벤트: 물품 시작, 입찰 성공/거절, 현재가, 리더보드, Soft Close,
물품 마감, 낙찰 결과, 방 종료.
