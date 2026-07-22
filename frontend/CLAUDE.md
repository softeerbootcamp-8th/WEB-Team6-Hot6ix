# upbid frontend — 프로젝트 룰셋

Claude Code / 기여자가 작업 전 반드시 참고하는 룰셋입니다. 상세 규칙은
[`docs/CONVENTIONS.md`](./docs/CONVENTIONS.md) 참고.

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
├── components/ui/     # shadcn/ui 컴포넌트
├── lib/               # cn(), queryClient 등 공용 유틸
├── routes/            # 파일 = 라우트 (TanStack Router)
└── styles/globals.css # Tailwind import + 디자인 토큰
```

- `routeTree.gen.ts` 는 **자동 생성물** (gitignore). 직접 수정하지 않는다.
- import 는 항상 `@/` 별칭 사용 (`@/lib/utils` 등).

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
