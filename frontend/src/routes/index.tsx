import { createFileRoute } from '@tanstack/react-router'

import { Button } from '@/components/ui/button'
import { useTest } from '@/api/generated/test/test'

export const Route = createFileRoute('/')({
  component: HomePage,
})

function HomePage() {
  // enabled: false → 자동 호출하지 않고 버튼 클릭 시 refetch 로만 호출
  const { data, error, isFetching, refetch, isSuccess } = useTest({
    query: { enabled: false },
  })

  return (
    <section className="flex flex-col items-start gap-6">
      <div className="space-y-2">
        <h1 className="text-3xl font-bold tracking-tight">upbid frontend</h1>
        <p className="text-muted-foreground">
          Vite + React + TypeScript · TanStack Router/Query · Orval · shadcn/ui ·
          Tailwind v4
        </p>
      </div>

      <div className="flex flex-col items-start gap-3">
        <Button onClick={() => refetch()} disabled={isFetching}>
          {isFetching ? '호출 중…' : 'GET /test 호출'}
        </Button>

        {isSuccess && (
          <p className="text-sm">
            <span className="text-muted-foreground">응답: </span>
            <code className="rounded bg-muted px-1.5 py-0.5">
              {data?.name ?? '(name 없음)'}
            </code>
          </p>
        )}

        {error && (
          <p className="text-sm text-destructive">에러: {error.message}</p>
        )}
      </div>
    </section>
  )
}
