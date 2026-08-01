# 화면 지도

**이 문서를 먼저 읽으면 "어느 파일을 열어야 하는지"와 "그 화면을 어떻게 띄우는지"를 알 수 있다.**

지금 모든 화면은 `src/mocks/` 목업으로 돈다. API 를 붙이는 사람은
아래 표에서 라우트를 찾고 → 해당 파일의 `TODO:` 주석 자리에 훅을 끼우면 된다.

```bash
cd frontend && pnpm install && pnpm dev
```

---

## 1. 화면을 띄우는 법

### 로그인 상태 바꾸기

화면 **오른쪽 아래 개발용 버튼**으로 게스트 / 회원 / 판매자를 전환한다
(`src/components/dev/session-switcher.tsx`). 상태는 localStorage 에 남는다.

| 상태 | 뜻 | 못 들어가는 곳 |
|---|---|---|
| 게스트 | 비로그인 | `requireMember` 붙은 라우트 전부 (→ `/` 로 튕김) |
| 회원 | 로그인, 판매자 프로필 없음 | `/seller` 에서 "프로필 미등록" 화면이 뜬다 |
| 판매자 | 로그인 + 판매자 프로필 | 전부 |

### 내 프로필 ≠ 판매자 프로필

헷갈리기 쉬워서 적어 둔다.

| | 무엇 | 어디서 |
|---|---|---|
| 내 프로필 | 닉네임·프로필 사진 (모든 사용자) | `/my/profile/edit` |
| 판매자 프로필 | 가게 이름·SNS·연락처 (판매자만) | `/seller/profile/edit` |

### 화면 폭

웹과 모바일은 **`md`(768px) 기준으로 갈린다.** 라이브·물품 상세·마이페이지처럼
구성이 아예 다른 화면은 트리를 둘로 나눠 뒀다. 개발자도구에서 375px 로 줄여
모바일을 확인한다.

모바일에는 데스크톱 상단바의 섹션 알약이 없다. 대신 앱바 오른쪽 **햄버거로
서랍**(`MobileNavDrawer`)을 열어 내 경매방·판매자 정보·거래 내역·마이페이지로
이동한다. 라이브 화면의 전용 앱바에도 붙어 있다.

---

## 2. 라우트 전체

`?` = 로그인 필요(`requireMember`), 없으면 비로그인도 접근 가능.

### 인증 · 진입

| 경로 | 파일 | ? | 화면 |
|---|---|---|---|
| `/` | `index.tsx` | | 카카오 로그인 |
| `/signup/phone` | `signup.phone.tsx` | | 전화번호 인증 |
| `/signup/complete` | `signup.complete.tsx` | | 가입 완료 |
| `/terms/privacy` | `terms.privacy.tsx` | | 개인정보처리방침 |
| `/terms/service` | `terms.service.tsx` | | 이용약관 |

### 경매방

| 경로 | 파일 | ? | 화면 |
|---|---|---|---|
| `/rooms` | `rooms.index.tsx` | ✓ | 참여 경매방 목록 |
| `/rooms/1` | `rooms.$roomId.index.tsx` | | **라이브 경매방** (웹 3열 / 모바일 전용 뷰) |
| `/rooms/3` | 〃 | | **종료된 경매방** (`ClosedRoomView` 로 분기) |
| `/rooms/7` | 〃 | | **빈 경매방** — 물품·이벤트 0 (`MOCK_EMPTY_ROOM`) |
| `/rooms/1/items/1` | `rooms.$roomId.items.$itemId.tsx` | | 물품 상세 (LIVE) |
| `/rooms/1/result` | `rooms.$roomId.result.tsx` | | 경매방 종료 요약 |
| `/join/abc123` | `join.$shareCode.tsx` | | 링크 입장 — **정상** |
| `/join/expired` | 〃 | | 링크 입장 — **종료된 링크** |
| `/join/xxxx` | 〃 | | 링크 입장 — **유효하지 않은 링크** |

`/join` 의 상태 판정은 `resolveRoom()` 한 함수에 모여 있다.
API 를 붙일 때 이 함수만 `GET /api/v1/public/auction-rooms/{shareCode}` 로 바꾸면 된다.

### 거래

| 경로 | 파일 | ? | 상태 |
|---|---|---|---|
| `/trades` | `trades.index.tsx` | ✓ | 거래 내역 목록 |
| `/trades/3` | `trades.$itemId.tsx` | ✓ | 구매자 · 확인 필요 |
| `/trades/1` | 〃 | ✓ | 구매자 · 거래 중 |
| `/trades/2` | 〃 | ✓ | 판매자 · 거래 완료 |
| `/trades/5` | 〃 | ✓ | 판매자 · **유찰** |

`/trades/$itemId` 는 **한 파일에서 세 화면**을 그린다 — 구매자 최종순위 /
판매자 낙찰후보 / 유찰. `trade.role` 과 `trade.status` 로 갈린다.

### 판매자

| 경로 | 파일 | ? | 화면 |
|---|---|---|---|
| `/seller` | `seller.index.tsx` | ✓ | 판매자 정보 (프로필 없으면 "미등록" 안내) |
| `/seller/profile/new` | `seller.profile.new.tsx` | ✓ | 프로필 등록 |
| `/seller/profile/edit` | `seller.profile.edit.tsx` | ✓ | **판매자** 프로필 수정 (가게·SNS·연락처) |
| `/seller/products` | `seller.products.index.tsx` | ✓ | 상품 관리 |
| `/seller/products/new` | `seller.products.new.tsx` | ✓ | 상품 등록 |
| `/seller/products/3/edit` | `seller.products.$productId.edit.tsx` | ✓ | 상품 수정 (미진행이라 수정 가능) |
| `/seller/products/1/edit` | 〃 | ✓ | **경매 중이라 수정 잠김** |
| `/seller/rooms/new` | `seller.rooms.new.tsx` | ✓ | 경매방 생성 + 물품 추가 모달 |
| `/seller/rooms/1/created` | `seller.rooms.$roomId.created.tsx` | ✓ | 생성 완료 (링크·QR) |

### 계정

| 경로 | 파일 | ? | 화면 |
|---|---|---|---|
| `/my` | `my.index.tsx` | ✓ | 마이페이지 |
| `/my/profile/edit` | `my.profile.edit.tsx` | ✓ | **내 프로필** 수정 (닉네임·사진) |
| `/my/withdraw` | `my.withdraw.index.tsx` | ✓ | 탈퇴 확인 |
| `/my/withdraw/complete` | `my.withdraw.complete.tsx` | | 탈퇴 완료 |

### 예외 (라우트 없음 · 전역)

| 상태 | 띄우는 법 | 컴포넌트 |
|---|---|---|
| 404 | 아무 없는 주소 (`/없는주소`) | `RouteNotFound` |
| 오류 | 컴포넌트에서 일부러 `throw` | `RouteError` |
| 로딩 | 로더가 300ms 초과할 때 | `RoutePending` |
| 오프라인 | 개발자도구 Network → Offline | `OfflineBanner` |

`src/components/route-states.tsx` 에 셋 다 있고 `main.tsx` 에서 **한 번만** 등록한다.
라우트마다 만들지 않는다. 특정 라우트만 다르게 하려면 그 라우트에
`notFoundComponent` / `errorComponent` 를 직접 준다.

---

## 3. 목업 데이터 지도

`src/mocks/data.ts` 하나에 다 있다. **id 를 바꾸면 다른 상태를 볼 수 있다.**

| 데이터 | 쓰는 화면 |
|---|---|
| `MOCK_ROOMS` | 경매방 목록 · 라이브/종료 분기 |
| `MOCK_ROOM_DETAIL` | 라이브 경매방, 물품 상세 (물품 6개: 진행 2 / 준비 1 / 종료 3) |
| `MOCK_ROOM_EVENTS` | 실시간 이벤트 피드 |
| `MOCK_TRADES` | 거래 내역·상세 |
| `MOCK_CANDIDATES` | 낙찰 후보 12명 (1위 실패 → 2위 승계, 7위가 "나") |
| `MOCK_PRODUCTS` | 상품 관리 8개 (미진행 3 / 낙찰 2 / 유찰 2 / 경매 중 1) |

세션(로그인 사용자)만 `src/lib/session.ts` 에 따로 있다.

---

## 4. 로직 붙이는 자리

### 순서

1. 백엔드를 `localhost:8080` 에 띄운다.
2. `pnpm api:gen` → `src/api/generated` 에 TanStack Query 훅·타입이 생긴다.
3. 화면에서 `MOCK_*` import 를 생성된 훅으로 바꾼다.
4. `src/mocks/types.ts` 대신 `@/api/generated/model` 타입을 쓴다.

### `TODO:` 주석이 곧 연동 지점

전부 `// TODO: <METHOD> <경로> 연동 (현재 목업)` 형식으로 달아 뒀다.

```bash
grep -rn "TODO:" src/
```

### 지킬 것

- **서버 응답 전에 성공으로 확정하지 않는다.** 입찰·낙찰·마감은 서버가
  커밋한 뒤에만 성공 토스트를 띄운다. (루트 `CLAUDE.md`)
- 라우트 가드는 **화면 제어일 뿐 권한 검증이 아니다.** 실제 권한은 백엔드가 본다.
- `src/api/generated/**` 와 `src/routeTree.gen.ts` 는 손으로 고치지 않는다.

---

## 5. 공용 부품 — 새로 만들기 전에 여기부터

### 골격

| | 언제 |
|---|---|
| `AppShell` | 로그인 화면 기본 골격. `title` 은 모바일 앱바에 쓰인다 |
| `GuestShell` | 비로그인으로 볼 수 있는 화면 (랜딩·링크 입장·약관) |
| `LiveShell` | 라이브 3열 전용 (`features/live/`) |

### UI

| | 쓸 곳 |
|---|---|
| `Button` | **버튼은 전부 이걸 쓴다.** `brand` / `brandOutline` / `danger` / `dangerOutline`, 크기 `field`(44) `form`(52) `cta`(56) |
| `TextField` `TextAreaField` `SelectField` | **폼 입력.** 라벨·에러·`aria-*` 연결을 컴포넌트가 처리한다 |
| `NumberField` | 숫자 입력. 단위(`원`·`분`)를 **입력창 밖**에 둔다 |
| `ConfirmDialog` | 되돌릴 수 없는 동작 앞 (삭제·확정·탈퇴) |
| `Modal` | 그 외 모달 |
| `toast.success/error/info` | **알림.** 화면 어디서든 부른다 |
| `EmptyState` | 빈 목록 |
| `StatusBadge` `Pager` `PageHeader` | 배지 · 페이지네이션 · 제목 |

```ts
import { toast } from '@/lib/toast'
toast.success('저장했어요')
```

### 색은 토큰으로

`globals.css` 에 다 있다. **화면에서 `bg-[#3182f6]` 처럼 헥스를 직접 쓰지 않는다.**

| 토큰 | 값 |
|---|---|
| `brand-50/100/200/300/400/500/600` | 브랜드 (기본 `#3182f6`) |
| `live` `success` `notice` (+`-surface`) | 상태 |
| `result-won` `result-progress` `result-failed` `result-idle` (+`-surface`) | 거래·경매 결과 배지 |
| `neutral-strong/secondary/tertiary/muted` `border` `fill` `surface-subtle` | 중립 |

**글자 굵기는 크기와 항상 같이 적는다** (`text-[14px] font-bold`).
예전에 토큰이 굵기까지 들고 있어서 전 화면 폰트가 어긋난 적이 있다.

### 모션

- 곡선은 `--ease-out-soft` / `--ease-in-out-soft` **두 개만** 쓴다. 새로 만들지 않는다.
- 화면 전환은 View Transitions API 가 자동 처리 (`main.tsx`).
- 등장 `animate-rise`, 로딩 골격 `animate-skeleton`.
- **timer·listener·subscription 은 반드시 정리한다.** (루트 `CLAUDE.md` 금지 항목)

---

## 6. 폴더

```
src/
├── routes/            파일 = 라우트 (TanStack Router)
├── features/
│   ├── auth/          카카오 로그인 버튼
│   ├── legal/         약관 본문
│   ├── live/          라이브·물품 상세 (가장 큰 덩어리)
│   ├── rooms/         경매방 카드
│   └── seller/        판매자 폼·물품 추가 모달
├── components/
│   ├── layout/        AppShell · GuestShell · 헤더 · 모바일 앱바
│   ├── ui/            Button · Field · Modal · ConfirmDialog · Toaster
│   ├── dev/           개발용 세션 전환 버튼
│   └── route-states · offline-banner · pager · page-header · status-badge
├── lib/               session · toast · route-guards · format · utils
├── mocks/             목업 데이터·타입 (API 붙으면 제거)
├── hooks/             use-countdown
├── api/               generated(자동) · mutator(axios 인스턴스)
└── styles/globals.css 디자인 토큰 + 모션
```

---

## 7. 디자인 원본

**Figma 가 최신본이다.** Notion API 명세서와 충돌하면 Figma 를 따른다.
(예: 명세서는 이메일/비밀번호 로그인, Figma 는 카카오 OAuth + 전화번호 인증)

- `Design` 페이지 → `Design 최종본` 프레임 (`713:5970`)
- 화면별 node id 는 `backend/plans/57-프론트-화면-라우팅/HANDOFF.md` 표에 정리돼 있다
- **`scan_text_nodes` 로 텍스트만 보고 레이아웃을 짐작하면 반드시 틀린다.**
  `get_node_info(nodeId, depth=1)` 로 좌표를 받아 프레임 원점을 빼서 쓴다
