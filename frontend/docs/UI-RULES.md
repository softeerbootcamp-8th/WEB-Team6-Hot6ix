# 화면 작업 규칙

화면을 새로 만들거나 기존 화면을 고칠 때 지키는 규칙입니다.
**작업 전에 이 문서와 [`SCREENS.md`](./SCREENS.md) 를 먼저 읽으세요.**

지금 화면들은 Figma 를 기준으로 한 번에 맞춰 둔 상태입니다. 여기 적힌 것들은
대부분 **이미 한 번 어긋났다가 고친 것들**이라, 모르고 건드리면 같은 문제가
그대로 되살아납니다.

---

## 1. 만들기 전에 — 공용 부품부터 찾는다

같은 걸 다시 만들면 색·간격·모션이 조금씩 달라져 화면마다 따로 놀게 됩니다.

| 필요한 것        | 쓰는 것                                                            |
| ---------------- | ------------------------------------------------------------------ |
| 버튼             | `Button` (`brand` / `brandOutline` / `danger` / `dangerOutline`)    |
| 폼 입력          | `TextField` / `NumberField` / `TextAreaField` / `SelectField`       |
| 선택 목록        | `Dropdown` (네이티브 `<select>` 는 브라우저마다 글자가 달라 안 씀)  |
| 모달             | `Modal` (네이티브 `<dialog>`)                                       |
| 확인 다이얼로그  | `ConfirmDialog`                                                     |
| 알림             | `toast.success/error/info` — 우측 상단, 전역                        |
| 목록 페이지 넘김 | `Pager`                                                             |
| 상품 사진        | `ProductThumbnail` / 프로필은 `ProfilePhoto`                        |
| QR 코드          | `QrCode` (PNG 저장은 `lib/qr` 의 `downloadQrCard`)                  |
| 빈 목록·오류     | `EmptyState`, 라우트 단위는 `main.tsx` 에 전역 등록된 상태 화면     |
| 페이지 골격      | `AppShell`(회원) / `GuestShell`(비로그인) / `LiveShell`(라이브 3열) |
| 등장 애니메이션  | `Reveal`, 목록 이동은 `useListFlip`                                 |

## 2. 스타일 규칙

- **색은 토큰만.** `bg-[#3182f6]`, `text-gray-500` 금지 → `bg-brand-500`,
  `text-neutral-tertiary`. 결과 색은 `--result-won/-progress/-failed/-idle`.
- **글자는 크기와 굵기를 항상 같이** 적는다. `text-[14px] font-bold`.
- 파란 채움 버튼의 글자는 **항상 흰색**(`text-white`). `text-primary-foreground`
  같은 토큰을 쓰면 다크 모드에서 회색으로 보인다.
- 모션 곡선은 `ease-soft`(= `--ease-out-soft`) 하나로 통일. 새 keyframe 이
  필요하면 `globals.css` 에 `@utility` 로 추가하고 이름을 남긴다.

## 3. 레이아웃 불변식 — 깨면 바로 티가 난다

### 라이브 경매방(`LiveShell`)은 페이지가 스크롤되지 않는다

3열 각각이 자기 영역 안에서만 스크롤합니다. `LiveShell` 이 마운트되는 동안
`html`/`body` 의 `overflow` 를 잠그고, 화면을 떠날 때 되돌립니다.
새 섹션을 넣을 때는 `min-h-0` + `flex-1` + `overflow-y-auto` 조합을 쓰세요.

### 모바일은 CSS 로 숨기지 말고 트리를 갈라 그린다

`md:hidden` 은 `display:none` 일 뿐 **컴포넌트는 그대로 살아 있습니다.**
모바일 트리의 `<dialog>` 가 열리면 `showModal()` 이 문서 전체를 `inert` 로
만들어 **데스크톱 화면의 버튼이 전부 죽습니다.** 타이머·구독도 두 벌 돕니다.

→ `useIsDesktop()`(1024px 기준)으로 **하나만** 렌더하세요.

### 물품 상세는 페이지 이동이 아니라 층(overlay)이다

라우트를 옮기면 경매방이 언마운트되면서 실시간 연결·타이머·쌓아둔 이벤트가
끊깁니다. 데스크톱은 `LiveShell` 의 `overlay` 슬롯(가운데+오른쪽 열만 덮음),
모바일은 `fixed inset-0` 층으로 띄웁니다.
`/rooms/$roomId/items/$itemId` 라우트는 링크로 바로 들어오는 경우를 위해
남겨둔 것이고, 같은 `ItemDetailPanel` 을 씁니다.

### 모바일 화면에는 스크롤 여유가 있다

`AppShell`/`GuestShell` 이 모바일에서만 64px 여백을 답니다. 화면에 딱 맞는
페이지가 손가락에 아무 반응이 없으면 굳어 보이기 때문입니다. 이 여백을 없애면
"안 움직이는 화면" 으로 되돌아갑니다.

## 4. 이미 밟은 지뢰 (같은 실수 반복 금지)

| 증상                                   | 원인                                                                                | 해결                                             |
| -------------------------------------- | ----------------------------------------------------------------------------------- | ------------------------------------------------ |
| 버튼이 하나도 안 눌림                  | `md:hidden` 안의 `<dialog>` 가 `showModal()` → 문서 전체 `inert`                     | `useIsDesktop()` 로 트리 하나만 렌더              |
| 페이지가 한참 아래로 스크롤됨          | 닫힌 `<dialog>` 에 `flex` 유틸이 얹혀 `dialog:not([open]){display:none}` 를 이김     | `Modal` 의 `[&:not([open])]:hidden` 유지          |
| 리더보드 순위가 글자만 바뀜            | FLIP 을 **등수**로 기억 → "1등 칸" 좌표는 늘 같아 이동 거리 0                        | 닉네임(정체성) 기준으로 위치 기억                |
| 새 이벤트 배경이 직사각형              | 줄에 음수 마진 → 스크롤 컨테이너가 좌우를 잘라 모서리가 깎임                         | 음수 마진 대신 안쪽 여백으로 배경을 만든다       |
| 방금 들어온 이벤트가 순간 사라짐       | "새 항목" 판정이 리렌더마다 뒤집혀 애니메이션이 처음부터 다시 돎                     | `useEventEntrance` 로 id 당 한 번만 판정         |
| 목록의 역할·상태가 항상 같은 값        | 요약 객체를 펼칠 때 필드 하나를 빠뜨림(`role` 등)                                    | 스프레드 후 덮어쓸 필드를 전부 확인              |
| 리더보드가 오른쪽에서 잘림             | `grid` 트랙이 `auto` 라 가장 넓은 패널이 트랙을 밀어냄                               | `grid-cols-[minmax(0,1fr)]` + `min-w-0`          |
| 서랍이 하단 바 아래에 깔림             | `sticky` + `z-index` 가 쌓임 맥락을 만들어 자식 `z-50` 이 갇힘                       | 상위 헤더 `z-30` 이상으로 올림                   |
| 모바일에서 폼 쓰고 나면 화면이 확대됨  | iOS 는 16px 미만 입력칸에 포커스하면 확대하고 되돌리지 않음                          | 모바일 입력 글자 16px (globals base layer)       |
| 768~1023px 에서 페이지가 통째로 스크롤 | 레이아웃은 `lg`, 트리 분기는 `md` 로 서로 다른 기준을 씀                             | `useIsDesktop()` 을 `lg`(1024)로 통일            |

## 5. 접근성·정리

- 모달·서랍은 열려 있는 동안 배경 스크롤을 잠그고, 닫을 때 반드시 되돌린다.
- 타이머·`addEventListener`·`IntersectionObserver` 는 `useEffect` 정리 함수에서 해제한다.
- 아이콘만 있는 버튼에는 `aria-label`, 상태 표시에는 `aria-live` 를 붙인다.
- 애니메이션은 `prefers-reduced-motion` 을 존중한다(`Reveal`·FLIP 훅은 이미 처리).

## 6. 끝내기 전에

```bash
pnpm build      # tsc -b + vite build
pnpm lint
pnpm format
```

`button.tsx` 의 react-refresh 경고 1건은 기존부터 있던 것이라 그대로 둡니다.
그 외 경고가 새로 생기면 고치고 커밋하세요.
