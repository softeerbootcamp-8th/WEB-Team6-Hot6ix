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
- 목록 API별 cursor/offset 방식

## 실시간

- DB 커밋 후에만 성공 이벤트를 발행한다.
- 방 단위와 물품 단위 채널의 책임을 구분한다.
- 클라이언트 timer는 종료 판정의 원본이 아니다.
- SSE/WebSocket 방식은 확정 명세와 기존 구현을 따른다.

주요 이벤트: 물품 시작, 입찰 성공/거절, 현재가, 리더보드, Soft Close,
물품 마감, 낙찰 결과, 방 종료.
