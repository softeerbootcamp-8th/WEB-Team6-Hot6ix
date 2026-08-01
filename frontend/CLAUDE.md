# upbid frontend — 프로젝트 룰셋

Claude Code / 기여자가 작업 전 반드시 참고하는 룰셋입니다.

> **처음 보는 사람은 [`docs/SCREENS.md`](./docs/SCREENS.md) 를 먼저 읽으세요.**
> 화면별 접근 경로(`/rooms/3` 은 종료된 방, `/trades/5` 는 유찰 …),
> 로그인 상태 바꾸는 법, 로직을 어디에 끼우면 되는지가 정리돼 있습니다.

- 화면 지도·라우트·목업: [`docs/SCREENS.md`](./docs/SCREENS.md)
- 코드 상세 규칙: [`docs/CONVENTIONS.md`](./docs/CONVENTIONS.md)

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
| `pnpm api:gen`      | Orval 로 API 훅/타입 생성 (백엔드 기동 필요)     |

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
4. 모든 요청은 `src/api/mutator/custom-instance.ts` 를 통과한다 (baseURL·인증).

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
