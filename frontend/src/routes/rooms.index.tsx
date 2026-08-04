import { createFileRoute, Link } from '@tanstack/react-router'
import { useMemo, useState } from 'react'
import { Search } from 'lucide-react'

import { AppShell } from '@/components/layout/page-shell'
import { EmptyState } from '@/components/page-header'
import { Input } from '@/components/ui/input'
import { MOCK_ROOMS } from '@/mocks/data'
import {
  RoomCard,
  type RoomCardData,
} from '@/features/rooms/components/room-card'
import { useDebouncedValue } from '@/hooks/use-debounced-value'
import { useMySellerProfile } from '@/features/seller/use-my-seller-profile'
import { useSellerRoomList } from '@/features/rooms/use-seller-room-list'
import type { GetMyRoomsStatus } from '@/api/generated/model'
import type { RoomStatus } from '@/mocks/types'
import { cn } from '@/lib/utils'
import { requireMember } from '@/lib/route-guards'

export const Route = createFileRoute('/rooms/')({
  beforeLoad: requireMember,
  component: MyRoomsPage,
})

type StatusFilter = 'ALL' | 'LIVE' | 'CLOSED'

/** 화면 상태 탭 ↔ 서버 `AuctionRoomStatus`. 전체는 파라미터를 안 보낸다. */
const STATUS_PARAM: Record<StatusFilter, GetMyRoomsStatus | undefined> = {
  ALL: undefined,
  LIVE: 'OPEN',
  CLOSED: 'CLOSED',
}

/** 서버 `AuctionRoomStatus` → 카드가 아는 상태. `BEFORE` 는 "준비 중"이다. */
const CARD_STATUS: Record<string, RoomStatus> = {
  BEFORE: 'READY',
  OPEN: 'LIVE',
  CLOSED: 'CLOSED',
}

function MyRoomsPage() {
  const [filter, setFilter] = useState<StatusFilter>('ALL')
  const [keyword, setKeyword] = useState('')
  // 상태(전체/LIVE/종료)와 직교하는 축이라 탭이 아니라 별도 토글이다.
  // 같은 줄에 4번째 탭으로 넣으면 "내가 만든 방 중 LIVE"를 고를 수 없다.
  const [mineOnly, setMineOnly] = useState(false)

  const debouncedKeyword = useDebouncedValue(keyword.trim())
  const { profile } = useMySellerProfile()

  const sellerRooms = useSellerRoomList(
    {
      keyword: debouncedKeyword || undefined,
      status: STATUS_PARAM[filter],
    },
    mineOnly,
  )

  // 참여 경매방 목록 API는 아직 없어(이슈 #118) 토글이 꺼져 있을 때만 목업을 쓴다.
  const mockRooms: RoomCardData[] = MOCK_ROOMS
  const apiRooms: RoomCardData[] = useMemo(
    () =>
      sellerRooms.rooms.map((room) => ({
        id: room.auctionRoomId ?? 0,
        title: room.name ?? '',
        sellerName: profile?.storeName ?? '내 가게',
        status: CARD_STATUS[room.status ?? 'BEFORE'] ?? 'READY',
        role: 'SELLER',
        itemCount: room.itemCount ?? 0,
        // 서버가 못 채우는 값이라 카드에서 아예 뺀다.
        participantCount: room.participantCount ?? null,
        imageUrl: room.coverImageUrl,
      })),
    [sellerRooms.rooms, profile?.storeName],
  )

  const rooms = mineOnly ? apiRooms : mockRooms
  const liveRooms = rooms.filter((room) => room.status === 'LIVE')
  const closedRooms = rooms.filter((room) => room.status === 'CLOSED')

  const visible = useMemo(() => {
    // 내가 만든 방은 서버가 이미 상태·검색어로 걸러 준다.
    if (mineOnly) return rooms

    const byStatus =
      filter === 'ALL' ? rooms : rooms.filter((room) => room.status === filter)

    const trimmed = keyword.trim()
    if (!trimmed) return byStatus
    return byStatus.filter((room) => room.title.includes(trimmed))
  }, [rooms, filter, keyword, mineOnly])

  const visibleLive = visible.filter((room) => room.status === 'LIVE')
  // 시작 전인 방을 종료로 묶으면 방금 만든 방이 "종료"로 보인다.
  const visibleReady = visible.filter((room) => room.status === 'READY')
  const visibleClosed = visible.filter((room) => room.status === 'CLOSED')

  const FILTERS: { key: StatusFilter; label: string; count: number }[] = [
    { key: 'ALL', label: '전체', count: rooms.length },
    { key: 'LIVE', label: 'LIVE', count: liveRooms.length },
    { key: 'CLOSED', label: '종료', count: closedRooms.length },
  ]

  return (
    <AppShell title="내 경매방">
      {/* 모바일은 앱바가 제목을 들고 있어 큰 제목을 다시 쓰지 않는다. */}
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="hidden md:block">
          <h1 className="text-[20px] font-bold text-foreground">참여 경매방</h1>
          <p className="mt-2 text-[13px] font-medium text-neutral-tertiary">
            {rooms.length > 0
              ? '참여한 경매방의 진행 상태와 결과를 확인할 수 있어요'
              : '참여하거나 만든 경매방이 이곳에 표시됩니다.'}
          </p>
        </div>

        <div className="flex w-full gap-2 md:w-auto">
          <Link
            to="/join/$shareCode"
            params={{ shareCode: 'abc123' }}
            className="ease-soft flex h-10 flex-1 items-center justify-center rounded-[10px] border bg-card px-4 text-[13px] font-semibold text-neutral-secondary transition-all duration-150 hover:border-border-strong active:scale-95 md:h-9 md:flex-none"
          >
            링크로 참여
          </Link>
          <Link
            to="/seller/rooms/new"
            className="ease-soft flex h-10 flex-1 items-center justify-center rounded-[10px] bg-brand-500 px-4 text-[13px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-95 md:h-9 md:flex-none"
          >
            + 경매방 만들기
          </Link>
        </div>
      </div>

      <p className="mt-6 text-[13px] font-bold text-neutral-secondary md:mt-8">
        {mineOnly ? '내가 만든 경매방' : '참여 경매방'} ({rooms.length}
        {mineOnly && sellerRooms.hasNextPage ? '+' : ''})
      </p>

      {/*
        필터 바는 목록이 비어도 항상 그린다. 토글이 켜진 채로 결과가 0개일 때
        바가 사라지면 토글을 다시 끌 방법이 없어진다.
      */}
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

        {/* 상태와 직교하는 축이라 탭 줄에 끼우지 않는다. */}
        <button
          type="button"
          aria-pressed={mineOnly}
          onClick={() => setMineOnly((prev) => !prev)}
          className={cn(
            'ease-soft h-9 rounded-[10px] border px-3.5 text-[13px] transition-all duration-150 active:scale-95',
            mineOnly
              ? 'border-brand-300 bg-brand-50 font-semibold text-brand-600'
              : 'font-medium text-neutral-tertiary hover:border-border-strong',
          )}
        >
          내가 만든 방
        </button>

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

      {mineOnly && sellerRooms.isPending ? (
        <ul aria-hidden className="mt-6 grid gap-4 md:mt-8 xl:grid-cols-2">
          {Array.from({ length: 4 }).map((_, index) => (
            <li
              key={index}
              className="animate-skeleton h-[168px] rounded-2xl bg-fill"
            />
          ))}
        </ul>
      ) : mineOnly && sellerRooms.isError ? (
        <div className="mt-4">
          <EmptyState
            title="경매방을 불러오지 못했어요"
            description="잠시 뒤에 다시 시도해 주세요."
            action={
              <button
                type="button"
                onClick={() => void sellerRooms.refetch()}
                className="ease-soft rounded-[14px] bg-brand-500 px-6 py-3.5 text-[15px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-95"
              >
                다시 시도
              </button>
            }
          />
        </div>
      ) : (
        <>
          {visible.length === 0 ? (
            <div className="mt-4">
              {mineOnly ? (
                <EmptyState
                  icon="0"
                  title={
                    debouncedKeyword || filter !== 'ALL'
                      ? '조건에 맞는 경매방이 없어요'
                      : '아직 만든 경매방이 없어요'
                  }
                  description={
                    debouncedKeyword || filter !== 'ALL'
                      ? '필터나 검색어를 바꿔보세요.'
                      : '경매방을 만들면 여기에서 다시 찾을 수 있어요.'
                  }
                />
              ) : rooms.length === 0 ? (
                <EmptyState
                  icon="0"
                  title="아직 참여한 경매방이 없어요"
                  description="초대 링크로 참여하거나 직접 경매방을 만들어 보세요."
                  hint="상단의 ‘링크로 참여’ 또는 ‘경매방 만들기’를 이용할 수 있어요."
                />
              ) : (
                <EmptyState
                  title="조건에 맞는 경매방이 없어요"
                  description="필터나 검색어를 바꿔보세요."
                />
              )}
            </div>
          ) : (
            <div className="mt-6 space-y-8 md:mt-8 md:space-y-10">
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

              {visibleReady.length > 0 && (
                <section>
                  <div className="flex items-baseline gap-3">
                    <h2 className="text-[13px] font-bold text-foreground">
                      준비 중인 경매방 ({visibleReady.length})
                    </h2>
                    <p className="text-[12px] font-medium text-neutral-muted">
                      물품을 편성하면 시작할 수 있어요
                    </p>
                  </div>
                  <ul className="mt-3 grid gap-4 xl:grid-cols-2">
                    {visibleReady.map((room) => (
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

              {mineOnly && sellerRooms.hasNextPage && (
                <button
                  type="button"
                  disabled={sellerRooms.isFetchingNextPage}
                  onClick={() => void sellerRooms.fetchNextPage()}
                  className="ease-soft h-12 w-full rounded-[14px] border bg-card text-[13px] font-bold text-neutral-secondary transition-all duration-150 hover:border-border-strong active:scale-[0.99] disabled:opacity-50"
                >
                  {sellerRooms.isFetchingNextPage ? '불러오는 중…' : '더 보기'}
                </button>
              )}
            </div>
          )}
        </>
      )}
    </AppShell>
  )
}
