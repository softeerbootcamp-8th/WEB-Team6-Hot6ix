import { createFileRoute, Link } from '@tanstack/react-router'
import { useState } from 'react'
import { Search } from 'lucide-react'

import { AppShell } from '@/components/layout/page-shell'
import { EmptyState } from '@/components/page-header'
import { Input } from '@/components/ui/input'
import { JoinByLinkModal } from '@/features/rooms/components/join-by-link-modal'
import {
  RoomCard,
  type RoomCardData,
} from '@/features/rooms/components/room-card'
import { useDebouncedValue } from '@/hooks/use-debounced-value'
import { useMyRoomList } from '@/features/rooms/use-my-room-list'
import { useMyRoomCounts } from '@/features/rooms/use-my-room-counts'
import type { GetMyRoomsStatus } from '@/api/generated/model'
import type { RoomStatus } from '@/types/domain'
import { cn } from '@/lib/utils'
import { requireMember } from '@/lib/route-guards'

export const Route = createFileRoute('/rooms/')({
  beforeLoad: requireMember,
  component: MyRoomsPage,
})

type StatusFilter = 'ALL' | 'LIVE' | 'READY' | 'CLOSED'

/** 화면 상태 탭 ↔ 서버 `AuctionRoomStatus`. 전체는 파라미터를 안 보낸다. */
const STATUS_PARAM: Record<StatusFilter, GetMyRoomsStatus | undefined> = {
  ALL: undefined,
  LIVE: 'OPEN',
  READY: 'BEFORE',
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
  // 상태 탭과 직교하는 축이라 탭이 아니라 별도 토글이다.
  // 같은 줄에 4번째 탭으로 넣으면 "내가 만든 방 중 LIVE"를 고를 수 없다.
  const [mineOnly, setMineOnly] = useState(false)
  const [joiningByLink, setJoiningByLink] = useState(false)

  const debouncedKeyword = useDebouncedValue(keyword.trim())

  const listQuery = useMyRoomList({
    keyword: debouncedKeyword || undefined,
    status: STATUS_PARAM[filter],
    role: mineOnly ? 'SELLER' : undefined,
  })

  // 상태를 안 보낸다. 탭을 바꿔도 숫자가 흔들리면 안 된다.
  const countsQuery = useMyRoomCounts({
    keyword: debouncedKeyword || undefined,
    role: mineOnly ? 'SELLER' : undefined,
  })

  const before = countsQuery.counts?.before ?? 0
  const open = countsQuery.counts?.open ?? 0
  const closed = countsQuery.counts?.closed ?? 0
  const total = before + open + closed

  const rooms: RoomCardData[] = listQuery.rooms.map((room) => ({
    id: room.auctionRoomId ?? 0,
    shareCode: room.shareCode ?? '',
    title: room.name ?? '',
    sellerName: room.storeName ?? '',
    status: CARD_STATUS[room.status ?? 'BEFORE'] ?? 'READY',
    role: room.role === 'BUYER' ? 'BUYER' : 'SELLER',
    itemCount: room.itemCount ?? 0,
    // 방송 중인 방만 서버가 채운다. 없으면 카드가 그 줄을 안 그린다.
    participantCount: room.participantCount ?? null,
    closedAt: room.closedAt ?? null,
    imageUrl: room.coverImageUrl,
  }))

  // 서버가 이미 상태·역할·검색어로 걸러 준다. 여기서 다시 거르지 않는다.
  const visibleLive = rooms.filter((room) => room.status === 'LIVE')
  // 시작 전인 방을 종료로 묶으면 방금 만든 방이 "종료"로 보인다.
  const visibleReady = rooms.filter((room) => room.status === 'READY')
  const visibleClosed = rooms.filter((room) => room.status === 'CLOSED')

  const FILTERS: { key: StatusFilter; label: string; count: number }[] = [
    { key: 'ALL', label: '전체', count: total },
    { key: 'LIVE', label: 'LIVE', count: open },
    // 시작 전인 방은 판매자가 물품을 채우러 다시 들어오는 곳이라 따로 고를 수 있어야 한다.
    { key: 'READY', label: '준비 중', count: before },
    { key: 'CLOSED', label: '종료', count: closed },
  ]

  return (
    <AppShell title="내 경매방">
      {/* 모바일은 앱바가 제목을 들고 있어 큰 제목을 다시 쓰지 않는다. */}
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="hidden md:block">
          <h1 className="text-[20px] font-bold text-foreground">참여 경매방</h1>
          <p className="mt-2 text-[13px] font-medium text-neutral-tertiary">
            {total > 0
              ? '참여한 경매방의 진행 상태와 결과를 확인할 수 있어요'
              : '참여하거나 만든 경매방이 이곳에 표시됩니다.'}
          </p>
        </div>

        <div className="flex w-full gap-2 md:w-auto">
          <button
            type="button"
            onClick={() => setJoiningByLink(true)}
            className="ease-soft flex h-10 flex-1 items-center justify-center rounded-[10px] border bg-card px-4 text-[13px] font-semibold text-neutral-secondary transition-all duration-150 hover:border-border-strong active:scale-95 md:h-9 md:flex-none"
          >
            링크로 참여
          </button>
          <Link
            to="/seller/rooms/new"
            className="ease-soft flex h-10 flex-1 items-center justify-center rounded-[10px] bg-brand-500 px-4 text-[13px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-95 md:h-9 md:flex-none"
          >
            + 경매방 만들기
          </Link>
        </div>
      </div>

      <div className="mt-6 flex items-center justify-between gap-3 md:mt-8">
        {/* 개수는 목록이 아니라 counts 응답에서 온다. "더 보기"를 눌러도 안 변한다. */}
        <p className="min-w-0 truncate text-[13px] font-bold text-neutral-secondary">
          {mineOnly ? '내가 만든 경매방' : '내 경매방'} ({total})
        </p>

        {/*
          상태와 직교하는 축이라 상태 탭 줄에 끼우지 않는다. 그렇다고 필터 바에
          같이 두면 폰 폭에서 탭 네 개가 첫 줄을, 검색창이 다음 줄을 다 써서
          이것만 가운데 줄에 혼자 남는다. 오른쪽이 비어 있는 이 줄로 올린다.
        */}
        <label
          className={cn(
            'flex shrink-0 cursor-pointer items-center gap-2 text-[13px] transition-colors',
            mineOnly
              ? 'font-semibold text-brand-600'
              : 'font-medium text-neutral-tertiary',
          )}
        >
          <input
            type="checkbox"
            checked={mineOnly}
            onChange={(event) => setMineOnly(event.target.checked)}
            className="size-4 shrink-0 accent-brand-500"
          />
          내가 만든 방만
        </label>
      </div>

      {/*
        필터 바는 목록이 비어도 항상 그린다. 상태를 걸어서 결과가 0개일 때
        바가 사라지면 그 상태를 다시 풀 방법이 없어진다.
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

      {listQuery.isPending ? (
        <ul aria-hidden className="mt-6 grid gap-4 md:mt-8 xl:grid-cols-2">
          {Array.from({ length: 4 }).map((_, index) => (
            <li
              key={index}
              className="animate-skeleton h-[168px] rounded-2xl bg-fill"
            />
          ))}
        </ul>
      ) : listQuery.isError ? (
        <div className="mt-4">
          <EmptyState
            title="경매방을 불러오지 못했어요"
            description="잠시 뒤에 다시 시도해 주세요."
            action={
              <button
                type="button"
                onClick={() => void listQuery.refetch()}
                className="ease-soft rounded-[14px] bg-brand-500 px-6 py-3.5 text-[15px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-95"
              >
                다시 시도
              </button>
            }
          />
        </div>
      ) : (
        <>
          {rooms.length === 0 ? (
            <div className="mt-4">
              {debouncedKeyword || filter !== 'ALL' ? (
                <EmptyState
                  title="조건에 맞는 경매방이 없어요"
                  description="필터나 검색어를 바꿔보세요."
                />
              ) : mineOnly ? (
                <EmptyState
                  icon="0"
                  title="아직 만든 경매방이 없어요"
                  description="경매방을 만들면 여기에서 다시 찾을 수 있어요."
                />
              ) : (
                <EmptyState
                  icon="0"
                  title="아직 참여한 경매방이 없어요"
                  description="초대 링크로 참여하거나 직접 경매방을 만들어 보세요."
                  hint="상단의 ‘링크로 참여’ 또는 ‘경매방 만들기’를 이용할 수 있어요."
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

              {listQuery.hasNextPage && (
                <button
                  type="button"
                  disabled={listQuery.isFetchingNextPage}
                  onClick={() => void listQuery.fetchNextPage()}
                  className="ease-soft h-12 w-full rounded-[14px] border bg-card text-[13px] font-bold text-neutral-secondary transition-all duration-150 hover:border-border-strong active:scale-[0.99] disabled:opacity-50"
                >
                  {listQuery.isFetchingNextPage ? '불러오는 중…' : '더 보기'}
                </button>
              )}
            </div>
          )}
        </>
      )}

      <JoinByLinkModal
        open={joiningByLink}
        onClose={() => setJoiningByLink(false)}
      />
    </AppShell>
  )
}
