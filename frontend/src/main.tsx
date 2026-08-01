import { StrictMode, lazy, Suspense } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClientProvider } from '@tanstack/react-query'
import { createRouter, RouterProvider } from '@tanstack/react-router'

import {
  RouteError,
  RouteNotFound,
  RoutePending,
} from '@/components/route-states'
import { queryClient } from '@/lib/query-client'
import { routeTree } from './routeTree.gen'
import './styles/globals.css'

const ReactQueryDevtools = import.meta.env.PROD
  ? () => null
  : lazy(() =>
      import('@tanstack/react-query-devtools').then((m) => ({
        default: m.ReactQueryDevtools,
      })),
    )

// 라우터 인스턴스 생성 (context 로 queryClient 전달)
const router = createRouter({
  routeTree,
  context: { queryClient },
  defaultPreload: 'intent',
  /*
   * 화면 전환에 View Transitions API 를 쓴다. 곡선·시간은 globals.css 의
   * `::view-transition-*` 규칙에 있다. 미지원 브라우저는 그냥 즉시 전환된다.
   */
  defaultViewTransition: true,

  /*
   * 예외 상태는 라우트마다 만들지 않고 여기서 한 번만 건다.
   * 특정 라우트만 다르게 보여줘야 하면 그 라우트에서 덮어쓴다.
   */
  defaultNotFoundComponent: RouteNotFound,
  defaultErrorComponent: RouteError,
  defaultPendingComponent: RoutePending,
  // 짧은 요청에서 로딩 화면이 깜빡이지 않도록 여유를 둔다.
  defaultPendingMs: 300,
  defaultPendingMinMs: 400,
})

// 타입 안전성을 위한 라우터 등록
declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}

const rootElement = document.getElementById('root')!

createRoot(rootElement).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
      <Suspense>
        <ReactQueryDevtools initialIsOpen={false} />
      </Suspense>
    </QueryClientProvider>
  </StrictMode>,
)
