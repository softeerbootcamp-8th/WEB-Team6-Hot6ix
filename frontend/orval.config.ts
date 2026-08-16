import { defineConfig } from 'orval'

/**
 * OpenAPI(springdoc) → TanStack Query 훅 + 타입 코드 생성.
 *
 * 실행: `pnpm api:gen` (백엔드가 http://localhost:8080 에서 기동 중이어야 함)
 * 생성물: src/api/generated/** (커밋 대상)
 */
export default defineConfig({
  upbid: {
    input: {
      target: 'http://localhost:8080/v3/api-docs',
    },
    output: {
      mode: 'tags-split',
      target: 'src/api/generated',
      schemas: 'src/api/generated/model',
      client: 'react-query',
      httpClient: 'axios',
      headers: true,
      clean: true,
      prettier: true,
      override: {
        mutator: {
          path: 'src/api/mutator/custom-instance.ts',
          name: 'customInstance',
        },
        query: {
          useQuery: true,
          useInfinite: false,
        },
      },
    },
  },
})
