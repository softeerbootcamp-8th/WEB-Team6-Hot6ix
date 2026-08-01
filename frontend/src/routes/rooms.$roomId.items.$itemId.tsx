import { Search, X } from 'lucide-react'
import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useMemo, useState } from 'react'

import { ConnectionBanner } from '@/features/live/components/connection-banner'
import { GuestNotice, LiveShell } from '@/features/live/components/live-shell'
import { ItemDetailPanel } from '@/features/live/components/item-detail-panel'
import { LiveItemList } from '@/features/live/components/live-item-list'
import {
  MOCK_ROOM_DETAIL,
  MOCK_ROOM_EVENTS,
  themedRoomItems,
} from '@/mocks/data'
import { MobileItemDetailView } from '@/features/live/components/mobile-item-detail-view'
import { formatWon } from '@/lib/format'
import { isClosingSoon, useCountdown } from '@/hooks/use-countdown'
import { toast } from '@/lib/toast'
import { useDevTools } from '@/lib/dev-tools'
import { useCurrentUser } from '@/lib/session'
import { useIsDesktop } from '@/hooks/use-media-query'
import { useRealtimeStatus } from '@/features/live/use-realtime-status'
import type { AuctionItemDetail, RoomEvent } from '@/mocks/types'

/**
 * 물품 상세 (Figma `WEB-13 · 구매자 · 물품 상세 (LIVE)`).
 *
 * 왼쪽 물품 목록은 그대로 두고, 가운데부터 오른쪽 끝까지 하나의 상세 패널
 * (896×590)이 차지한다. 패널 안은 다시 두 열로 나뉜다.
 * - 왼쪽 360: 상품 이미지·제목·현재 최고가·링크·설명
 * - 오른쪽 476: 실시간 리더보드 카드 → 물품 이벤트 카드 → 퀵입찰 행
 */
/** 데모 이벤트 id 시작값. 목업 이벤트와 겹치지 않게 띄운다. */
const DEMO_EVENT_BASE = 80_000

export const Route = createFileRoute('/rooms/$roomId/items/$itemId')({
  component: AuctionItemPage,
})

function AuctionItemPage() {
  const { roomId, itemId } = Route.useParams()
  const navigate = useNavigate()
  const user = useCurrentUser()
  const { status, retry } = useRealtimeStatus()
  const isDesktop = useIsDesktop()
  const showDevTools = useDevTools()

  const room = {
    ...MOCK_ROOM_DETAIL,
    items: themedRoomItems(Number(roomId), MOCK_ROOM_DETAIL.items),
  }
  const isGuest = user === null

  const [keyword, setKeyword] = useState('')
  const [pending, setPending] = useState(false)
  const [feedback, setFeedback] = useState<{
    tone: 'success' | 'error'
    message: string
  } | null>(null)

  /** 데모 입찰이 반영된 물품. 실시간 연동 전까지만 쓴다. */
  const [override, setOverride] = useState<AuctionItemDetail | null>(null)
  const [extraEvents, setExtraEvents] = useState<RoomEvent[]>([])

  const base =
    room.items.find((candidate) => String(candidate.id) === itemId) ??
    room.items[0]!
  const item = override?.id === base.id ? override : base

  const remaining = useCountdown(item.endsAt)
  const closed = item.status === 'CLOSED'
  const ready = item.status === 'READY'
  const urgent = !closed && isClosingSoon(remaining)

  const minimum = item.currentPrice + item.bidUnit
  const [amount, setAmount] = useState(minimum)

  const visibleItems = useMemo(() => {
    const trimmed = keyword.trim()
    if (!trimmed) return room.items
    return room.items.filter((candidate) => candidate.name.includes(trimmed))
  }, [room.items, keyword])

  const itemEvents = useMemo(
    () => [
      ...MOCK_ROOM_EVENTS.filter(
        (event) =>
          event.subtitle === item.name || event.message.includes(item.name),
      ),
      ...extraEvents,
    ],
    [item.name, extraEvents],
  )

  /**
   * 개발용 데모 입찰. 경매방 화면의 같은 버튼과 짝을 맞춘다.
   * `shuffle` 이면 새 최고가가 들어와 리더보드 순위가 바뀐다.
   */
  const addDemoBid = (shuffle: boolean) => {
    const nickname = `데모입찰러${item.bidCount + 1}`
    const next = item.currentPrice + item.bidUnit

    setExtraEvents((prev) => [
      ...prev,
      {
        id: DEMO_EVENT_BASE + prev.length,
        at: new Date().toISOString(),
        kind: 'BID' as const,
        message: `${nickname}님이 ${formatWon(next)} 입찰`,
        subtitle: item.name,
      },
    ])

    if (!shuffle) return
    setOverride({
      ...item,
      currentPrice: next,
      bidCount: item.bidCount + 1,
      topBidderNickname: nickname,
      leaderboard: [
        { rank: 1, nickname, amount: next, isMe: false },
        ...item.leaderboard.map((entry) => ({
          ...entry,
          rank: entry.rank + 1,
        })),
      ].slice(0, 5),
    })
  }

  const bidBlocked = closed || ready || amount < minimum

  const submitBid = () => {
    setPending(true)
    setFeedback(null)

    // TODO: POST /api/v1/auction-items/{id}/bids 연동 (현재 목업)
    window.setTimeout(() => {
      setPending(false)
      if (amount <= item.currentPrice) {
        const message = '이미 더 높은 입찰이 있어요'
        setFeedback({
          tone: 'error',
          message: `${message}. 최신 현재가로 다시 시도해주세요.`,
        })
        toast.error(message, {
          description: `현재가 ${formatWon(item.currentPrice)} · 그 위로 다시 입찰해 주세요.`,
        })
      } else {
        setFeedback({
          tone: 'success',
          message: `${formatWon(amount)} 입찰이 등록됐어요.`,
        })
        toast.success('입찰이 등록됐어요', {
          description: `${item.name} · ${formatWon(amount)}`,
        })
      }
    }, 1200)
  }

  /*
   * 모바일은 웹 상세 패널(896×590)을 접은 게 아니라 별도 스크롤 페이지다
   * (Figma `MOB-05`). CSS 로 감추면 타이머가 두 벌 도니 JS 로 하나만 그린다.
   */
  if (!isDesktop) {
    return (
      <MobileItemDetailView
        item={item}
        sellerName={room.sellerName}
        events={itemEvents}
        remaining={remaining}
        closed={closed}
        ready={ready}
        urgent={urgent}
        amount={amount}
        minimum={minimum}
        onAmountChange={setAmount}
        pending={pending}
        bidBlocked={bidBlocked || isGuest}
        feedback={feedback}
        onBack={() =>
          void navigate({ to: '/rooms/$roomId', params: { roomId } })
        }
        onBid={submitBid}
      />
    )
  }

  return (
    <LiveShell
      room={room}
      isGuest={isGuest}
      headerActions={
        import.meta.env.DEV && showDevTools ? (
          <span className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => addDemoBid(false)}
              className="ease-soft flex h-8 items-center rounded-[10px] border border-border-strong bg-card px-2.5 text-[11px] font-bold text-neutral-tertiary transition-all duration-150 hover:bg-fill active:scale-95"
            >
              이벤트
            </button>
            <button
              type="button"
              onClick={() => addDemoBid(true)}
              className="ease-soft flex h-8 items-center rounded-[10px] border border-border-strong bg-card px-2.5 text-[11px] font-bold text-neutral-tertiary transition-all duration-150 hover:bg-fill active:scale-95"
            >
              순위 변동
            </button>
          </span>
        ) : undefined
      }
      leftLabel={`물품 목록 (${room.items.length})`}
      left={
        <div className="flex h-full flex-col">
          <div className="relative shrink-0">
            <Search
              aria-hidden
              className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-neutral-muted"
            />
            <input
              type="search"
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="물품 검색"
              aria-label="물품 검색"
              className="h-10 w-full rounded-xl border bg-card pr-3 pl-9 text-[13px] font-normal outline-none placeholder:text-neutral-muted focus-visible:border-ring"
            />
          </div>

          <LiveItemList
            items={visibleItems}
            canStart={room.role === 'SELLER'}
            isSelected={(candidate) => candidate.id === item.id}
            onSelect={(candidate) =>
              void navigate({
                to: '/rooms/$roomId/items/$itemId',
                params: { roomId, itemId: String(candidate.id) },
              })
            }
          />
        </div>
      }
      centerLabel={
        <>
          <span>선택한 물품 상세 · 라이브</span>
          <Link
            to="/rooms/$roomId"
            params={{ roomId }}
            aria-label="상세 닫고 라이브로 돌아가기"
            className="ease-soft ml-auto flex size-7 items-center justify-center rounded-full border border-[#c2c9d6] bg-card text-neutral-secondary transition-all duration-150 hover:bg-fill active:scale-95"
          >
            <X aria-hidden className="size-3.5" />
          </Link>
        </>
      }
      center={
        <>
          <ConnectionBanner status={status} onRetry={retry} />
          {isGuest && (
            <GuestNotice redirectTo={`/rooms/${roomId}/items/${itemId}`} />
          )}

          <ItemDetailPanel
            item={item}
            roomId={roomId}
            itemId={itemId}
            events={itemEvents}
            isGuest={isGuest}
            closed={closed}
            ready={ready}
            urgent={urgent}
            remaining={remaining}
            amount={amount}
            minimum={minimum}
            pending={pending}
            onAmountChange={setAmount}
            onBid={submitBid}
          />
        </>
      }
    />
  )
}
