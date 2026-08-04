# upbid frontend — 프로젝트 룰셋

Claude Code / 기여자가 작업 전 반드시 참고하는 룰셋입니다.

## 작업 전에 읽을 문서 (중요)

화면은 Figma 기준으로 한 번에 맞춰 둔 상태입니다. 규칙을 모르고 고치면
이미 해결한 문제가 그대로 되살아납니다. **작업 종류에 맞는 문서를 먼저 읽으세요.**

| 하려는 일                          | 먼저 읽을 문서                                         |
| ---------------------------------- | ------------------------------------------------------ |
| 처음 세팅·명령어·폴더 구조·Orval   | [`docs/DEVELOPMENT.md`](./docs/DEVELOPMENT.md)         |
| 어느 화면인지 찾기, 라우트 확인    | [`docs/SCREENS.md`](./docs/SCREENS.md)                 |
| 화면을 새로 만들거나 고치기        | [`docs/UI-RULES.md`](./docs/UI-RULES.md)               |
| 목업을 실제 API 로 바꾸기          | [`docs/API-INTEGRATION.md`](./docs/API-INTEGRATION.md) |
| 네이밍·import·커밋 등 코드 컨벤션  | [`docs/CONVENTIONS.md`](./docs/CONVENTIONS.md)         |
| S3·CloudFront 배포, 도메인·캐시    | [`docs/DEPLOYMENT.md`](./docs/DEPLOYMENT.md)           |

### 반드시 지킬 것 (요약)

1. **공용 부품을 먼저 찾는다.** Button·Field·Dropdown·Modal·ConfirmDialog·
   toast·Pager·ProductThumbnail·EmptyState 가 이미 있다. 직접 조립하지 않는다.
2. **색은 토큰만, 글자는 크기와 굵기를 같이.** 파란 버튼 글자는 항상 흰색.
3. **모바일은 CSS 로 숨기지 않는다.** `md:hidden` 은 언마운트가 아니다.
   `useIsDesktop()`(1024px)으로 트리를 하나만 그린다. 어기면 `<dialog>` 가
   문서를 `inert` 로 만들어 **다른 화면의 버튼이 전부 죽는다.**
4. **라이브 경매방은 페이지가 스크롤되지 않는다.** 열 안에서만 스크롤한다.
5. **물품 상세는 라우트 이동이 아니라 층(overlay)이다.** 옮기면 실시간
   연결·타이머·쌓인 이벤트가 끊긴다.
6. **서버가 확정하기 전에 성공으로 표시하지 않는다.** 입찰·낙찰·마감 공통.
7. **push 전에 CI 와 같은 명령을 돌린다** —
   `pnpm format:check` + `pnpm lint` + `pnpm build`.
   `format:check` 를 빠뜨려 CI 가 깨지는 일이 잦다 (아래 "명령어" 참고).

자세한 이유와 이미 밟은 지뢰 목록은 [`docs/UI-RULES.md`](./docs/UI-RULES.md)
4장에 있습니다.

## 스택

- **빌드**: Vite 6 + React 19 + TypeScript (strict)
- **라우팅**: TanStack Router (파일 기반, `src/routes/`)
- **서버 상태**: TanStack Query (v5)
- **API 코드 생성**: Orval (OpenAPI → `src/api/generated`)
- **UI**: shadcn/ui (new-york) + Tailwind CSS v4 (CSS-first)
- **패키지 매니저**: pnpm

## 명령어

| 명령                | 설명                                             |
| ------------------- | ------------------------------------------------ |
| `pnpm dev`          | 개발 서버 (라우트 트리 자동 생성)                |
| `pnpm build`        | 타입체크(`tsc -b`) + 프로덕션 빌드               |
| `pnpm preview`      | 빌드 결과 미리보기                               |
| `pnpm lint`         | ESLint                                           |
| `pnpm format`       | Prettier 자동 정렬                               |
| `pnpm format:check` | Prettier 검사만 (**CI 가 이걸 돌린다**)          |
| `pnpm api:gen`      | Orval 로 API 훅/타입 생성 (백엔드 기동 필요)     |

### push 전 체크리스트

`.github/workflows/CICD-FE.yml` 이 돌리는 것과 같다. 하나라도 빠뜨리면
빨간 CI 를 보고 수정 커밋을 하나 더 붙이게 된다.

```bash
pnpm format:check     # 실패하면 pnpm format 으로 정렬하고 다시
pnpm lint
pnpm exec vite build
pnpm exec tsc -b
```

`pnpm build` 는 위 마지막 두 개를 합친 것이라 그걸로 대신해도 된다.
**`format:check` 만 별도다** — 빌드가 통과해도 포맷은 따로 깨진다.
편집 도구가 아니라 스크립트 치환(python·sed 등)으로 파일을 고쳤다면
거의 확실히 포맷이 어긋나므로 `pnpm format` 을 먼저 돌린다.

## 공용 부품 (직접 만들기 전에 확인)

| 위치                                    | 무엇                                                  |
| --------------------------------------- | ----------------------------------------------------- |
| `components/ui/`                        | Button · Field · Dropdown · Modal · ConfirmDialog · Toaster |
| `components/`                           | Pager · EmptyState · ProductThumbnail · ProfilePhoto · Reveal · StatusBadge · QrCode |
| `components/layout/`                    | AppShell · GuestShell · AppHeader · MobileAppBar · MobileNavDrawer |
| `features/live/components/`             | LiveShell · LiveItemList · ItemDetailPanel · QuickBidOverlay · LeaderboardRows · EventFeed |
| `hooks/` · `features/live/`             | useIsDesktop · useCountdown · useListFlip · useEventEntrance |
| `lib/`                                  | toast · session · route-guards · format(금액·날짜·전화번호) · dev-flags · qr |

## 개발용 도구

- 화면 우하단 **DEV 패널** — 세션(게스트/회원/판매자) 전환, API 응답 지연,
  요청 강제 실패. API 연동 중 로딩·에러 화면을 백엔드 없이 확인할 때 쓴다.
- `⌘/Ctrl + Shift + D` — 개발용 UI(패널·라우터 devtools·경매방 DEV 버튼) 숨기기.
  `.env.local` 에 `VITE_DEV_TOOLS=off` 를 두면 처음부터 꺼진 채 시작한다.
- 프로덕션 빌드에는 이 코드들이 **포함되지 않는다.**

## 폴더 규칙 (요약)

```
src/
├── api/generated/     # Orval 생성물 — 수정 금지 (재생성됨)
├── api/mutator/       # 공용 axios 인스턴스
├── components/layout/ # AppShell · GuestShell · 헤더 · 모바일 앱바
├── components/ui/     # shadcn/ui + Button · Field · Modal · Toaster
├── features/          # 도메인별 화면 조각 (auth · live · rooms · seller · legal)
├── hooks/             # use-countdown 등
├── lib/               # session · toast · route-guards · format · cn()
├── mocks/             # 목업 데이터·타입 (API 붙으면 제거)
├── routes/            # 파일 = 라우트 (TanStack Router)
└── styles/globals.css # Tailwind import + 디자인 토큰
```

- `routeTree.gen.ts` 는 **자동 생성물** (gitignore). 직접 수정하지 않는다.
- import 는 항상 `@/` 별칭 사용 (`@/lib/utils` 등).

## 새 화면·기능을 만들기 전에

**만들기 전에 [`docs/SCREENS.md`](./docs/SCREENS.md) 의 "공용 부품"을 먼저 본다.**
같은 걸 다시 만들면 색·간격이 조금씩 어긋나서 화면마다 달라 보인다.

- 버튼은 `Button` 변형(`brand` / `brandOutline` / `danger` / `dangerOutline`)
- 폼 입력은 `TextField` / `TextAreaField` / `SelectField` (에러·`aria-*` 자동 연결)
- 확인 다이얼로그는 `ConfirmDialog`, 알림은 `toast.success(...)`
- 색은 **토큰만** 쓴다. `bg-[#3182f6]` 처럼 헥스를 직접 쓰지 않는다
- 글자는 **크기와 굵기를 항상 같이** 적는다 (`text-[14px] font-bold`)
- 404·오류·로딩은 `main.tsx` 에 전역 등록돼 있다. 라우트마다 만들지 않는다

## API 흐름

1. 백엔드(springdoc)를 `http://localhost:8080` 에서 기동한다.
2. `pnpm api:gen` → `http://localhost:8080/v3/api-docs` 를 읽어 `src/api/generated`
   에 TanStack Query 훅과 타입 생성.
3. 컴포넌트에서 생성된 훅을 그대로 사용 (직접 axios 호출 금지).
4. 모든 요청은 `src/api/mutator/custom-instance.ts` 를 통과한다
   (baseURL·인증·개발용 지연/실패 주입).

**화면은 아직 목업(`src/mocks/`)으로 돌아갑니다.** 목업을 API 로 바꾸는 순서,
화면↔목업 대응표, 로딩·에러·빈 상태 처리 규칙은
[`docs/API-INTEGRATION.md`](./docs/API-INTEGRATION.md) 를 따르세요.

## 커밋 / PR

- 커밋 메시지 양식: `[FE] feat: 기능 A`

```text
git commit -m "[FE] fix: 로그인 안되는 문제 수정중
(빈 행)
- 수정사항 1 
- 수정사항 2
- 수정사항 3"
```
- 커밋/푸시는 사용자가 요청할 때만 수행한다.

## 작업 시 주의

- `src/api/generated/**` 와 `src/routeTree.gen.ts` 는 절대 손으로 수정하지 않는다.
- 새 UI 컴포넌트는 `pnpm dlx shadcn@latest add <name>` 로 추가한다.
- 서버 상태는 TanStack Query, 클라이언트 로컬 상태는 React state 로 분리한다.
