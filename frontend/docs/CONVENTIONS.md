# upbid Frontend 컨벤션

이 문서는 프론트엔드 코드 작성 규칙을 정의합니다. 요약은 루트
[`CLAUDE.md`](../CLAUDE.md) 를 참고하세요.

## 1. 폴더 구조

실제 구조와 "새 파일을 어디에 두는지" 는
[`DEVELOPMENT.md`](./DEVELOPMENT.md) 4장에 정리돼 있습니다.

## 2. 네이밍

| 대상                | 규칙            | 예                         |
| ------------------- | --------------- | -------------------------- |
| 라우트 파일         | kebab-case      | `routes/auction-detail.tsx`|
| 일반 컴포넌트 파일  | kebab-case      | `components/bid-card.tsx`  |
| 컴포넌트 이름       | PascalCase      | `BidCard`                  |
| 훅                  | `use` + camel   | `useAuctionList`           |
| 상수                | UPPER_SNAKE     | `MAX_BID_COUNT`            |

## 3. Import 규칙

- 항상 `@/` 별칭 사용. 상대경로(`../../lib`) 지양.
  ```ts
  import { cn } from '@/lib/utils'
  import { Button } from '@/components/ui/button'
  ```
- `import type { ... }` 로 타입 전용 import 를 구분한다 (`verbatimModuleSyntax`).

## 4. 라우팅 (TanStack Router · 파일 기반)

- `src/routes/` 안의 파일이 곧 라우트다. 파일 저장 시 `routeTree.gen.ts` 자동 갱신.
- 각 라우트 파일은 `export const Route = createFileRoute('<path>')({ ... })` 를 내보낸다.
- 경로 규칙: `index.tsx` → `/`, `about.tsx` → `/about`, `posts.$id.tsx` → `/posts/:id`,
  중첩 레이아웃은 `posts.tsx` + `posts.index.tsx` 형태.
- 데이터 프리로드는 라우트 `loader` + Query 를 조합한다.

## 5. 서버 상태 (TanStack Query + Orval)

- **직접 axios/fetch 호출 금지.** Orval 이 생성한 훅을 사용한다.
- 백엔드 API 변경 → `pnpm api:gen` 재실행 → 생성된 훅 사용.
- 쿼리 키는 Orval 이 생성·관리하므로 임의로 만들지 않는다. 커스텀 쿼리가 필요하면
  `['도메인', '동작', ...params]` 배열 규칙을 따른다.
- 전역 기본값(staleTime·retry 등)은 `src/lib/query-client.ts` 에서 관리한다.
- 서버 상태 = Query, 화면 로컬 상태 = `useState`/`useReducer` 로 명확히 분리.

## 6. 스타일 (Tailwind v4 + shadcn/ui)

- Tailwind v4 는 CSS-first. 설정은 `src/styles/globals.css` 의 `@theme`/CSS 변수로 관리
  (별도 `tailwind.config.js` 없음).
- 색상은 **디자인 토큰만** 쓴다: `bg-brand-500`, `text-neutral-tertiary`,
  `bg-result-won-surface` 등. 헥스(`#3182f6`)나 기본 팔레트(`text-gray-500`) 금지.
- 글자는 크기와 굵기를 항상 함께 적는다: `text-[14px] font-bold`.
- 클래스 조합은 `cn()` 사용.
- 새 컴포넌트는 `pnpm dlx shadcn@latest add <name>` 로 추가하고 `components/ui/` 에 둔다.
- 화면을 만들 때 지켜야 하는 레이아웃·모션 규칙은
  [`UI-RULES.md`](./UI-RULES.md) 에 따로 있다.

## 7. 코드 품질

- 커밋 전 `pnpm lint` + `pnpm build` 통과 확인.
- 포맷은 Prettier(`pnpm format`). 설정: `.prettierrc.json` (세미콜론 없음, 작은따옴표).
- `any` 지양, strict 타입 준수.

## 8. 커밋 컨벤션

- 형식: `[FE] type: 요약` — `feat`, `fix`, `docs`, `chore`, `refactor`, `style`, `test`.
  모노레포라 영역 태그(`[FE]`/`[BE]`/`[ALL]`)를 앞에 붙인다.
- 본문에는 무엇을 왜 바꿨는지 목록으로 남긴다.

```text
[FE] fix: 모바일에서 버튼이 눌리지 않던 문제 수정

- md:hidden 안의 dialog 가 showModal 되어 문서 전체가 inert 가 되던 원인 제거
- useIsDesktop 으로 트리를 하나만 렌더하도록 변경
```

- 커밋·푸시는 사람이 확인한 뒤에 한다.
