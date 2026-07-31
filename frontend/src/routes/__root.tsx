import { createRootRouteWithContext, Outlet } from '@tanstack/react-router'
import type { QueryClient } from '@tanstack/react-query'
import { lazy, Suspense } from 'react'

import { DevSessionSwitcher } from '@/components/dev/session-switcher'

interface RouterContext {
  queryClient: QueryClient
}

// devtools 는 프로덕션 번들에서 제외되도록 lazy 로딩한다.
const TanStackRouterDevtools = import.meta.env.PROD
  ? () => null
  : lazy(() =>
      import('@tanstack/router-devtools').then((m) => ({
        default: m.TanStackRouterDevtools,
      })),
    )

export const Route = createRootRouteWithContext<RouterContext>()({
  component: RootLayout,
})

/**
 * 상단 크롬은 화면마다 다르므로(로그인·게스트·라이브) 루트가 아니라
 * `AppShell` / `GuestShell` 에서 그린다. 루트는 Outlet 과 개발 도구만 둔다.
 */
function RootLayout() {
  return (
    <>
      <Outlet />
      <DevSessionSwitcher />
      <Suspense>
        <TanStackRouterDevtools />
      </Suspense>
    </>
  )
}
