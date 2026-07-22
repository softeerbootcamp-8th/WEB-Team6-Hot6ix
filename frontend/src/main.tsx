import { StrictMode, lazy, Suspense } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClientProvider } from '@tanstack/react-query'
import { createRouter, RouterProvider } from '@tanstack/react-router'

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
