# API 연동 가이드

화면은 **대부분 목업 데이터**(`src/mocks/`)로 돌아갑니다.
이 문서는 목업을 실제 API 로 갈아 끼우는 순서와, 그 과정에서 쓸 도구를 정리합니다.

### 이미 연동된 화면

| 화면                                          | 쓰는 API                                                                            |
| --------------------------------------------- | ----------------------------------------------------------------------------------- |
| `/seller/rooms/$roomId/created` 공유 링크·QR  | `GET /api/v1/auction-rooms/{roomId}/share`                                          |
| 라이브 경매방 공유 패널(`SharePanel`)         | 없음 — 주소의 shareCode 로 링크를 화면에서 조립한다                                 |
| `/join/$shareCode` 방 정보 (물품 목록은 목업) | `GET /api/v1/auction-rooms/share/{shareCode}`                                       |
| `/trades` 거래 내역                           | `GET /api/v1/deals`                                                                 |
| `/trades/$itemId` 낙찰 후보·최종 순위         | `GET /api/v1/auction-items/{id}/deal-candidates` + `GET /api/v1/auction-rooms/share/{shareCode}/auction-items/{id}` |
| `/rooms/$shareCode/result`, 종료된 경매방(`ClosedRoomView`) | `GET /api/v1/auction-rooms/share/{shareCode}/results`                  |
| 종료된 경매방의 판매자 전용 거래 현황(`RoomDealStatus`)   | `GET /api/v1/auction-rooms/{roomId}/deals`                                |

QR 은 서버가 이미지를 만들지 않습니다. 서버는 `shareUrl` 문자열만 주고,
`components/qr-code.tsx`(화면) 와 `lib/qr.ts`(PNG 저장)가 그립니다.

---

## 1. 준비

```bash
# 1. 백엔드를 8080 으로 띄운다
# 2. OpenAPI 문서에서 훅·타입 생성 (src/api/generated 아래)
pnpm api:gen

# 3. 개발 서버
pnpm dev
```

`.env.local` (없으면 `.env.example` 참고해서 생성)

```bash
VITE_API_BASE_URL=          # 비우면 상대경로 → vite 프록시가 8080 으로 전달
VITE_DEV_TOOLS=off          # (선택) 개발용 패널을 처음부터 숨기고 시작
```

> `src/api/generated/**` 와 `src/routeTree.gen.ts` 는 **자동 생성물**입니다.
> 손으로 고치지 마세요. 다음 생성 때 사라집니다.

## 2. 화면 하나를 옮기는 순서

한 번에 다 바꾸지 말고 **화면 하나씩** 옮기세요. 목업과 API 가 잠시 섞여 있어도 됩니다.

1. **어떤 목업을 쓰는지 찾는다** — 화면 파일에서 `MOCK_` 로 시작하는 import
2. **대응하는 Orval 훅을 찾는다** — `src/api/generated` 에서 경로로 검색
3. **데이터 소스만 교체한다** — JSX 는 그대로 두고 위쪽 값만 바꾼다

```tsx
// before
const rooms = MOCK_ROOMS

// after
const { data, isPending, isError, refetch } = useGetAuctionRooms()
const rooms = data ?? []
```

4. **로딩·에러·빈 상태를 붙인다** (아래 3번 규칙)
5. **화면을 직접 확인한다** — 개발 패널에서 지연 2초 / 실패 항상 을 켜고 본다
6. `pnpm build` + `pnpm lint`

### 화면 ↔ 목업 대응표

| 목업                | 쓰는 화면                                                    | 성격                        |
| ------------------- | ------------------------------------------------------------ | --------------------------- |
| `MOCK_ROOMS`        | `/rooms`, `/rooms/$shareCode`(요약)                          | 경매방 목록                 |
| `MOCK_ROOM_DETAIL`  | `/rooms/$shareCode`, 물품 상세                               | 방 상세 + 물품 배열         |
| `MOCK_EMPTY_ROOM`   | `/rooms/7`                                                   | 물품 0개인 방(빈 상태 확인) |
| `MOCK_ROOM_EVENTS`  | 라이브 이벤트 피드                                           | 실시간으로 대체될 값        |
| `MOCK_PRODUCTS`     | `/seller`, `/seller/products`, 물품 추가 모달                | 판매자 상품                 |
| `MOCK_TRADES`       | 판매자 상품 상세, `/seller` 요약 (`/trades`, 종료된 경매방은 연동 완료) | 거래           |
| `themedRoomItems()` | 방 제목에 맞춰 물품 이름을 바꾸는 목업 전용 함수             | **연동 시 삭제**            |

목업이 하나도 남지 않으면 `src/mocks/` 폴더와 `src/lib/session.ts` 의
`MOCK_MEMBER`/`MOCK_SELLER`, `src/components/dev/` 를 통째로 지웁니다.

### 사진 (`mocks/images.ts` — 삭제 완료)

상품·프로필 사진을 이름으로 골라 주던 목업이었습니다. #138·#139 에서 서버
`imageUrl` 로 바꾸면서 파일을 지웠습니다. `ProductThumbnail`·`ProfilePhoto` 는
**`src` 가 없으면 목업으로 떨어지지 않고 회색 아이콘·사람 아이콘을 그립니다.**

아직 서버 사진이 안 붙어 회색으로 보이는 자리:

| 화면                    | 이유                                                           |
| ----------------------- | -------------------------------------------------------------- |
| `/trades` 목록          | `DealSummaryResponseDto` 에 `imageUrl` 이 없다 (서버 변경 필요) |
| `/join/$shareCode` 커버 | `coverImageUrl` 을 채울 화면이 없다 (#163)                      |

## 3. 상태 처리 규칙

```tsx
if (isPending) return <RoutePending />        // 또는 스켈레톤
if (isError) return <RouteError error={error} reset={() => void refetch()} />
if (rooms.length === 0) return <EmptyState ... />
```

`RouteError` 의 prop 은 `error` / `reset` 이다(`onRetry` 아님). 라우터 에러
경계와 같은 시그니처라 그대로 꽂아 쓸 수 있다.

**404 를 에러로 처리하지 않는 경우가 있다.** 예를 들어 `/join/$shareCode` 는
없는 공유 코드가 "장애" 가 아니라 "유효하지 않은 링크" 안내다. 이럴 땐
`error?.response?.status` 를 보고 갈라 준다.

- **로딩**: 라우트 전체면 `main.tsx` 에 등록된 `RoutePending`, 카드 단위면
  `animate-skeleton` 유틸로 자리를 잡아 둔다.
- **에러**: 서버 원문 메시지를 화면에 그대로 노출하지 않는다. 사용자에게는
  다음 행동을, 개발자에게는 `console.error`(DEV 한정).
- **빈 목록**: `EmptyState` 에 "무엇을 하면 되는지"까지 적는다.
- **뮤테이션**: 성공/실패는 `toast` 로 알린다. 화면 안 인라인 문구는 두지 않는다
  (같은 내용을 두 번 말하게 된다).

## 4. 절대 어기면 안 되는 것

- **서버 응답 전에 성공으로 확정하지 않는다.** 입찰·낙찰·마감은 서버가 확정한
  뒤에만 성공 토스트를 띄운다. (루트 `CLAUDE.md` 규칙)
- **화면 제어는 권한이 아니다.** `requireMember`, `isOwner` 같은 분기는 UX 일 뿐
  이고 실제 권한은 백엔드가 검증한다. 판매자 전용 동작(경매방 종료, 물품 추가/
  빼기, 거래 성사/실패)은 서버에서도 막혀 있어야 한다.
- **직접 `axios`/`fetch` 를 부르지 않는다.** 모든 요청은 Orval 훅 →
  `custom-instance` 를 통과해야 인증·에러·개발 도구가 한 곳에서 걸린다.
  - **예외는 S3 이미지 업로드 하나다** (`features/seller/use-image-upload.ts`).
    발급받은 presigned URL 로 보내는 `PUT` 은 우리 서버가 아니라 S3 로 간다.
    `custom-instance` 는 `withCredentials: true` 라 세션 쿠키를 싣는데, S3 는 서명
    말고 다른 인증 정보가 붙으면 403 으로 거절한다. 그래서 순수 `fetch` 에
    `credentials: 'omit'` 으로 보낸다. **presigned URL 을 받는 요청 자체는**
    Orval 훅(`useCreatePresignedUrl`)을 그대로 쓴다.
- 쿼리 키는 Orval 이 만든 것을 쓰고, 뮤테이션 뒤에는 관련 쿼리를
  `invalidateQueries` 로 무효화한다.

## 5. 실시간 (SSE / WebSocket)

- 현재 `useRealtimeStatus()` 는 연결 상태만 흉내 냅니다. 실제 채널이 붙으면
  이 훅 안만 바꾸면 화면(`ConnectionBanner`)은 그대로 동작합니다.
- 이벤트 피드·리더보드는 배열을 갈아 끼우기만 하면 애니메이션이 따라옵니다.
  - 새 이벤트: 배열 끝에 추가 → 등장 연출은 `useEventEntrance` 가 처리
  - 순위 변동: 리더보드 배열 재정렬 → FLIP 은 닉네임 기준으로 따라감
- **구독·타이머는 반드시 정리한다.** 화면을 떠날 때 해제되지 않으면 방을
  오갈수록 이벤트가 중복으로 쌓인다.

## 6. 개발용 도구

화면 우하단 **DEV 패널** (개발 빌드에서만, `⌘/Ctrl + Shift + D` 로 숨김)

| 항목      | 값                 | 쓰임                                           |
| --------- | ------------------ | ---------------------------------------------- |
| 세션      | 게스트/회원/판매자 | 역할별 화면 분기 확인 (라우트 가드까지 재실행) |
| 응답 지연 | 없음/0.6초/2초     | 로딩 UI·스켈레톤 확인                          |
| 요청 실패 | 없음/50%/항상      | 에러 UI·재시도·토스트 확인                     |

지연·실패는 `src/lib/dev-network.ts` 가 `custom-instance` 앞단에서 적용합니다.
**Orval 훅으로 나가는 모든 요청**에 걸리며, 목업만 쓰는 화면에는 영향이 없습니다.
프로덕션 빌드에서는 패널도 주입 코드도 번들에 포함되지 않습니다.

그 밖에 확인용으로 준비된 화면들:

- `/rooms/7` 물품이 하나도 없는 방
- `/rooms/3` 종료된 경매방
- `/trades/5` 유찰된 거래
- `/seller` 판매자 프로필 미등록 상태 (DEV 패널에서 `회원` 선택)

## 7. 자주 막히는 곳

- **CORS** — `VITE_API_BASE_URL` 을 비우고 vite 프록시를 쓰면 안 만납니다.
- **401** — 세션 쿠키(`SESSION`, httpOnly) 방식이라 `custom-instance.ts` 에
  `withCredentials: true` 를 켜 두었습니다. dev 는 vite 프록시로 동일 출처가 되어
  없어도 되지만, `VITE_API_BASE_URL` 이 다른 호스트를 가리키는 순간 이게 없으면
  로그인한 사용자도 401 을 받습니다.
- **응답 필드가 화면과 다름** — 화면을 고치기 전에 백엔드와 계약을 맞추세요.
  API Method/Path/필드/enum/날짜·금액 형식은 공용 계약입니다(루트 `CLAUDE.md`).
