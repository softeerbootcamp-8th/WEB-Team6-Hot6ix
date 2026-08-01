import { createRootRouteWithContext, Outlet } from '@tanstack/react-router'
import type { QueryClient } from '@tanstack/react-query'
import { lazy, Suspense, useEffect } from 'react'

import { DevSessionSwitcher } from '@/components/dev/session-switcher'
import { OfflineBanner } from '@/components/offline-banner'
import { Toaster } from '@/components/ui/toaster'
import { devToolsStore, useDevTools } from '@/lib/dev-tools'

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
  const showDevTools = useDevTools()

  /*
   * 개발용 UI 토글. 화면을 있는 그대로 보고 싶을 때 쓴다.
   * 프로덕션에서는 `devToolsStore.toggle()` 이 아무 일도 하지 않는다.
   */
  useEffect(() => {
    if (!import.meta.env.DEV) return

    const onKeyDown = (event: KeyboardEvent) => {
      if (!event.shiftKey || !(event.metaKey || event.ctrlKey)) return
      if (event.key.toLowerCase() !== 'd') return
      event.preventDefault()
      devToolsStore.toggle()
    }

    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [])

  return (
    <>
      {/* 기기 네트워크가 끊기면 화면 맨 위에 깔린다. */}
      <OfflineBanner />
      <Outlet />
      {/* 전역 알림. 화면 어디서든 `toast.*` 로 띄운다. */}
      <Toaster />

      {import.meta.env.DEV && showDevTools && (
        <>
          <DevSessionSwitcher />
          <Suspense>
            <TanStackRouterDevtools />
          </Suspense>
        </>
      )}
    </>
  )
}
