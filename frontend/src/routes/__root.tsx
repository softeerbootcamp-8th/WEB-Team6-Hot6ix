import {
  createRootRouteWithContext,
  Link,
  Outlet,
} from '@tanstack/react-router'
import type { QueryClient } from '@tanstack/react-query'
import { lazy, Suspense } from 'react'

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

function RootLayout() {
  return (
    <div className="min-h-screen bg-background text-foreground">
      <header className="border-b">
        <nav className="mx-auto flex max-w-5xl items-center gap-4 px-6 py-4">
          <Link to="/" className="font-semibold [&.active]:text-primary">
            upbid
          </Link>
        </nav>
      </header>
      <main className="mx-auto max-w-5xl px-6 py-10">
        <Outlet />
      </main>
      <Suspense>
        <TanStackRouterDevtools />
      </Suspense>
    </div>
  )
}
