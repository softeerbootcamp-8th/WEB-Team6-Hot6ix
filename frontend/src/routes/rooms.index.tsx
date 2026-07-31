import { createFileRoute, Link } from '@tanstack/react-router'
import { useMemo, useState } from 'react'
import { Search } from 'lucide-react'

import { AppShell } from '@/components/layout/page-shell'
import { EmptyState, PageHeader } from '@/components/page-header'
import { Input } from '@/components/ui/input'
import { MOCK_ROOMS } from '@/mocks/data'
import { RoomCard } from '@/features/rooms/components/room-card'
import { cn } from '@/lib/utils'
import { requireMember } from '@/lib/route-guards'

export const Route = createFileRoute('/rooms/')({
  beforeLoad: requireMember,
  component: MyRoomsPage,
})

type StatusFilter = 'ALL' | 'LIVE' | 'CLOSED'

function MyRoomsPage() {
  const [filter, setFilter] = useState<StatusFilter>('ALL')
  const [keyword, setKeyword] = useState('')

  const rooms = MOCK_ROOMS
  const liveRooms = rooms.filter((room) => room.status === 'LIVE')
  const closedRooms = rooms.filter((room) => room.status === 'CLOSED')

  const visible = useMemo(() => {
    const byStatus =
      filter === 'ALL'
        ? rooms
        : rooms.filter((room) =>
            filter === 'LIVE' ? room.status === 'LIVE' : room.status !== 'LIVE',
          )

    const trimmed = keyword.trim()
    if (!trimmed) return byStatus
    return byStatus.filter((room) => room.title.includes(trimmed))
  }, [rooms, filter, keyword])

  const visibleLive = visible.filter((room) => room.status === 'LIVE')
  const visibleClosed = visible.filter((room) => room.status !== 'LIVE')

  const FILTERS: { key: StatusFilter; label: string; count: number }[] = [
    { key: 'ALL', label: '전체', count: rooms.length },
    { key: 'LIVE', label: 'LIVE', count: liveRooms.length },
    { key: 'CLOSED', label: '종료', count: closedRooms.length },
  ]

  return (
    <AppShell title="내 경매방">
      <PageHeader
        title="참여 경매방"
        description={
          rooms.length > 0
            ? '참여한 경매방의 진행 상태와 결과를 확인할 수 있어요'
            : '참여하거나 만든 경매방이 이곳에 표시됩니다.'
        }
        actions={
          <>
            <Link
              to="/join/$shareCode"
              params={{ shareCode: 'abc123' }}
              className="flex h-9 items-center rounded-[10px] border px-4 text-[13px] font-semibold text-neutral-secondary transition-colors hover:border-border-strong"
            >
              링크로 참여
            </Link>
            <Link
              to="/seller/rooms/new"
              className="flex h-9 items-center rounded-[10px] bg-primary px-4 text-[13px] font-bold text-primary-foreground transition-opacity hover:opacity-90"
            >
              + 경매방 만들기
            </Link>
          </>
        }
      />

      <p className="mt-8 text-[13px] font-bold text-neutral-secondary">
        참여 경매방 ({rooms.length})
      </p>

      {rooms.length === 0 ? (
        <div className="mt-4">
          <EmptyState
            icon="0"
            title="아직 참여한 경매방이 없어요"
            description="초대 링크로 참여하거나 직접 경매방을 만들어 보세요."
            hint="상단의 ‘링크로 참여’ 또는 ‘경매방 만들기’를 이용할 수 있어요."
          />
        </div>
      ) : (
        <>
          <div className="mt-3 flex flex-wrap items-center gap-2 rounded-2xl border bg-card p-3">
            <div role="tablist" aria-label="상태 필터" className="flex gap-1">
              {FILTERS.map((item) => (
                <button
                  key={item.key}
                  type="button"
                  role="tab"
                  aria-selected={filter === item.key}
                  onClick={() => setFilter(item.key)}
                  className={cn(
                    'h-9 rounded-[10px] px-3.5 text-[13px] transition-colors',
                    filter === item.key
                      ? 'bg-brand-50 font-semibold text-brand-600'
                      : 'font-medium text-neutral-tertiary hover:bg-fill',
                  )}
                >
                  {item.label} {item.count}
                </button>
              ))}
            </div>

            <div className="relative ml-auto w-full sm:w-[240px]">
              <Search
                aria-hidden
                className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-neutral-muted"
              />
              <Input
                type="search"
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
                placeholder="경매방 이름 검색"
                aria-label="경매방 이름 검색"
                className="h-10 pl-9 text-[13px] font-normal"
              />
            </div>
          </div>

          {visible.length === 0 ? (
            <div className="mt-4">
              <EmptyState
                title="조건에 맞는 경매방이 없어요"
                description="필터나 검색어를 바꿔보세요."
              />
            </div>
          ) : (
            <div className="mt-8 space-y-10">
              {visibleLive.length > 0 && (
                <section>
                  <div className="flex items-baseline gap-3">
                    <h2 className="text-[13px] font-bold text-foreground">
                      LIVE 경매방 ({visibleLive.length})
                    </h2>
                    <p className="text-[12px] font-medium text-neutral-muted">
                      진행 중인 방이 먼저 표시됩니다
                    </p>
                  </div>
                  <ul className="mt-3 grid gap-4 xl:grid-cols-2">
                    {visibleLive.map((room) => (
                      <RoomCard key={room.id} room={room} />
                    ))}
                  </ul>
                </section>
              )}

              {visibleClosed.length > 0 && (
                <section>
                  <h2 className="text-[13px] font-bold text-foreground">
                    종료 경매방 ({visibleClosed.length})
                  </h2>
                  <ul className="mt-3 grid gap-4 xl:grid-cols-2">
                    {visibleClosed.map((room) => (
                      <RoomCard key={room.id} room={room} />
                    ))}
                  </ul>
                </section>
              )}
            </div>
          )}
        </>
      )}
    </AppShell>
  )
}
