# 개발 환경 · 명령어 · 폴더 구조

프론트를 처음 받는 사람이 읽는 문서입니다.
화면 규칙은 [`UI-RULES.md`](./UI-RULES.md), 연동 절차는
[`API-INTEGRATION.md`](./API-INTEGRATION.md), 배포는
[`DEPLOYMENT.md`](./DEPLOYMENT.md) 에 있습니다.

---

## 1. 처음 한 번

```bash
# Node 20+ / pnpm 필요 (npm, yarn 섞어 쓰지 않는다 — lockfile 이 깨진다)
corepack enable            # pnpm 이 없다면
cd frontend
pnpm install
cp .env.example .env.local
pnpm dev                   # http://localhost:15173
```

백엔드 없이도 화면은 전부 돕니다(목업 데이터). API 를 붙일 때만 백엔드가 필요합니다.

## 2. 명령어

| 명령             | 언제 쓰나                                                       |
| ---------------- | --------------------------------------------------------------- |
| `pnpm dev`       | 개발 서버. 라우트 파일을 저장하면 `routeTree.gen.ts` 자동 갱신    |
| `pnpm build`     | **타입 검사(tsc -b) + 프로덕션 빌드.** 커밋 전 필수              |
| `pnpm preview`   | 빌드 결과를 실제 배포처럼 확인 (`http://localhost:4173`)          |
| `pnpm lint`      | ESLint                                                           |
| `pnpm format`    | Prettier 자동 정렬                                               |
| `pnpm format:check` | 정렬만 확인 (CI 용)                                           |
| `pnpm api:gen`   | 백엔드 OpenAPI → 훅·타입 생성 (백엔드 8080 기동 필요)            |

### 커밋 전 체크

```bash
pnpm format && pnpm lint && pnpm build
```

`button.tsx` 의 react-refresh 경고 1건은 기존부터 있는 것이라 그대로 둡니다.

> **자동화된 테스트는 아직 없습니다.** 단위/E2E 도구를 넣으려면 새 의존성이라
> 팀 합의가 필요합니다(루트 `CLAUDE.md`). 그전까지는 아래 수동 확인으로 대신합니다.

### 화면 수동 확인 순서

1. `pnpm dev` → 우하단 **DEV 패널**에서 세션을 바꿔 가며 확인
   - 게스트: `/`, `/join/abc123`
   - 회원(구매자): `/rooms`, `/rooms/1`, `/trades`
   - 판매자: `/rooms/2`(물품 추가·빼기·경매방 종료), `/seller`
2. 브라우저 개발자도구에서 **모바일 폭(390px)** 으로도 같은 화면을 본다
   - 데스크톱/모바일은 CSS 가 아니라 **다른 트리**라 반드시 둘 다 봐야 한다
3. API 를 붙였다면 DEV 패널에서 **지연 2초 / 실패 항상** 을 켜고 로딩·에러 확인
4. `pnpm build` 로 타입까지 통과하는지 확인

## 3. Orval — 백엔드 API 를 코드로 가져오기

### 흐름

```
백엔드(springdoc)  →  http://localhost:18000/v3/api-docs (OpenAPI JSON)
        ↓ pnpm api:gen
src/api/generated/<tag>/<tag>.ts   TanStack Query 훅
src/api/generated/model/           요청·응답 타입
        ↓ 모든 요청이 통과
src/api/mutator/custom-instance.ts  baseURL·인증·개발용 지연/실패
```

### 절차

```bash
# 1. 백엔드를 8080 으로 띄운다 (springdoc 활성 상태여야 함)
curl http://localhost:18000/v3/api-docs | head   # 200 이면 준비 완료

# 2. 생성
pnpm api:gen

# 3. 생성물 확인 후 커밋 (생성물도 저장소에 포함한다)
git status src/api/generated
```

### 설정 (`orval.config.ts`)

| 설정                | 값                        | 뜻                                          |
| ------------------- | ------------------------- | ------------------------------------------- |
| `input.target`      | `.../v3/api-docs`         | 스펙을 읽어올 곳                            |
| `mode`              | `tags-split`              | 컨트롤러(tag)별로 파일 분리                 |
| `client`            | `react-query`             | TanStack Query 훅으로 생성                  |
| `override.mutator`  | `customInstance`          | 모든 요청이 우리 axios 인스턴스를 통과      |
| `clean`             | `true`                    | **생성 폴더를 비우고 다시 만든다**          |

`clean: true` 라서 `src/api/generated/**` 에 손으로 쓴 코드는 다음 생성 때
사라집니다. 공통 로직이 필요하면 `src/api/mutator/` 나 `src/lib/` 에 두세요.

### 생성된 훅 쓰기

```tsx
import { useTest } from '@/api/generated/test/test'

const { data, isPending, isError, refetch } = useTest()
```

쿼리 키도 Orval 이 만들어 줍니다. 뮤테이션 후에는 관련 쿼리를
`queryClient.invalidateQueries({ queryKey: getTestQueryKey() })` 로 무효화하세요.

### 잘 나는 오류

| 증상                                   | 원인·해결                                                              |
| -------------------------------------- | ---------------------------------------------------------------------- |
| `ECONNREFUSED 8080`                    | 백엔드가 안 떠 있음. `pnpm api:gen` 은 스펙을 **HTTP 로** 읽는다        |
| 생성은 됐는데 훅이 안 보임             | 컨트롤러에 `@Tag` 가 없으면 파일 이름이 달라진다. `src/api/generated` 확인 |
| 타입이 `unknown`/`any` 로 나옴         | 백엔드 DTO 에 스키마 주석이 없음. 백엔드와 함께 고친다                  |
| 요청은 가는데 401                      | 세션 쿠키 방식이면 `withCredentials` 필요 → `custom-instance.ts` 한 곳만 |
| CORS 오류                              | `VITE_API_BASE_URL` 을 비워 vite 프록시(`/api` → 8080)를 태운다         |

## 4. 폴더 구조 (실제 상태)

```
frontend/
├── docs/                         # 팀 문서 (이 폴더)
├── public/
├── src/
│   ├── api/
│   │   ├── generated/            # Orval 생성물 — 손대지 않는다
│   │   └── mutator/custom-instance.ts
│   ├── components/
│   │   ├── ui/                   # 범용 부품 (Button·Field·Modal·Dropdown·Toaster…)
│   │   ├── layout/               # AppShell·GuestShell·헤더·모바일 앱바·서랍
│   │   ├── dev/                  # 개발용 패널 — 연동 끝나면 삭제
│   │   ├── product-thumbnail.tsx # 도메인 공용 (여러 화면이 함께 쓰는 것)
│   │   ├── profile-photo.tsx
│   │   ├── pager.tsx
│   │   ├── reveal.tsx
│   │   └── route-states.tsx      # 404·오류·로딩 (main.tsx 에 전역 등록)
│   ├── features/                 # 도메인별 화면 조각
│   │   ├── auth/                 # 카카오 로그인 버튼 등
│   │   ├── live/                 # 라이브 경매방 (가장 큼)
│   │   │   ├── components/       # LiveShell·ItemDetailPanel·QuickBid…
│   │   │   ├── use-list-flip.ts  # 도메인 훅은 features 안에 둔다
│   │   │   └── use-event-entrance.ts
│   │   ├── rooms/ · seller/ · legal/
│   ├── hooks/                    # 화면과 무관한 공용 훅 (use-countdown, use-media-query)
│   ├── lib/                      # 순수 로직 (toast·session·format·route-guards·dev-flags)
│   ├── mocks/                    # 목업 데이터·타입·이미지 — API 붙으면 삭제
│   ├── routes/                   # 파일 = 라우트 (TanStack Router)
│   ├── styles/globals.css        # 디자인 토큰 + 모션 유틸
│   ├── main.tsx                  # Provider 조립 (Query → Router)
│   └── routeTree.gen.ts          # 자동 생성 (gitignore)
├── orval.config.ts
├── vite.config.ts                # 별칭 @/ · /api 프록시 · 라우터 플러그인
└── .env.example
```

### 새 파일은 어디에?

| 만드는 것                             | 위치                                  |
| ------------------------------------- | ------------------------------------- |
| 새 화면(URL 이 생김)                  | `src/routes/` — 파일명이 곧 경로      |
| 한 화면에서만 쓰는 조각               | 그 화면의 `features/<도메인>/components/` |
| 두 화면 이상이 쓰는 도메인 부품       | `src/components/`                     |
| 도메인과 무관한 범용 부품             | `src/components/ui/`                  |
| 한 도메인에서만 쓰는 훅               | `features/<도메인>/use-*.ts`          |
| 어디서나 쓰는 훅                      | `src/hooks/`                          |
| 계산·포맷·스토어 같은 순수 로직       | `src/lib/`                            |

### 라우트 파일 이름 규칙 (TanStack Router)

| 파일                                | URL                          |
| ----------------------------------- | ---------------------------- |
| `index.tsx`                         | `/`                          |
| `rooms.index.tsx`                   | `/rooms`                     |
| `rooms.$shareCode.index.tsx`        | `/rooms/:shareCode`          |
| `rooms.$shareCode.items.$itemId.tsx` | `/rooms/:shareCode/items/:itemId` |
| `seller.products.$productId.edit.tsx` | `/seller/products/:productId/edit` |

파일을 만들면 `routeTree.gen.ts` 가 자동으로 갱신됩니다(개발 서버 실행 중).
이 파일은 **직접 수정하지 않습니다.**

## 5. 환경 변수

`.env.local` (gitignore 대상, 각자 로컬에만)

| 변수                | 값                                                        |
| ------------------- | --------------------------------------------------------- |
| `VITE_API_BASE_URL` | 비우면 상대경로 → vite 프록시가 8080 으로 전달(CORS 없음) |
| `VITE_DEV_TOOLS`    | `off` 면 개발용 UI 를 처음부터 숨김                        |

새 변수를 추가하면 `.env.example` 과 `src/vite-env.d.ts` 에도 함께 적어 주세요.

## 6. Git

- 브랜치: `fe/feat/<이슈번호>-<요약>`
- 커밋: `[FE] feat: 요약` + 빈 줄 + 변경 목록 (무엇을 왜 바꿨는지)
- `src/api/generated/**` 는 커밋 대상, `routeTree.gen.ts` 는 gitignore
- 커밋·푸시는 사람이 확인한 뒤에 한다 (AI 가 임의로 하지 않는다)
