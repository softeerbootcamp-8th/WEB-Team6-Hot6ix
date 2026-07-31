import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useMemo, useState, type ReactNode } from 'react'
import { Search } from 'lucide-react'

import { BidConfirmPanel } from '@/features/live/components/bid-confirm-panel'
import {
  BidResultToast,
  type BidResult,
} from '@/features/live/components/bid-result-toast'
import { ClosedRoomView } from '@/features/live/components/closed-room-view'
import { ConnectionBanner } from '@/features/live/components/connection-banner'
import { EventFeed } from '@/features/live/components/event-feed'
import { GuestNotice, LiveShell } from '@/features/live/components/live-shell'
import { ItemLeaderboard } from '@/features/live/components/leaderboard'
import { LiveItemCard } from '@/features/live/components/live-item-card'
import { MOCK_ROOM_DETAIL, MOCK_ROOM_EVENTS, MOCK_ROOMS } from '@/mocks/data'
import { QuickBidOverlay } from '@/features/live/components/quick-bid-overlay'
import { SharePanel } from '@/features/live/components/share-panel'
import { cn } from '@/lib/utils'
import { useCurrentUser } from '@/lib/session'
import { useRealtimeStatus } from '@/features/live/use-realtime-status'
import type { AuctionItemDetail } from '@/mocks/types'

/**
 * 라이브 경매방 (Figma `WEB-09 · 구매자 · 라이브`).
 *
 * 왼쪽 물품 목록, 가운데 실시간 이벤트 + 입찰 CTA, 오른쪽 열.
 * 오른쪽 열은 상황에 따라 리더보드 / 빠른 입찰 / 입찰 확인 / 공유로 바뀐다.
 */
export const Route = createFileRoute('/rooms/$roomId/')({
  component: LiveRoomPage,
})

/** 오른쪽 열에 무엇을 띄울지 */
type RightPanel = 'leaderboard' | 'quickBid' | 'confirm' | 'share'

const PANEL_LABEL: Record<RightPanel, string> = {
  leaderboard: '리더보드 · 물품별',
  quickBid: '빠른 입찰',
  confirm: '입찰 확인',
  share: '경매방 공유',
}

function LiveRoomPage() {
  const { roomId } = Route.useParams()
  const navigate = useNavigate()
  const user = useCurrentUser()
  const { status, retry } = useRealtimeStatus()

  const isGuest = user === null

  // 목록의 방 요약으로 제목·상태를 맞춘다. 상세 물품은 목업 하나를 공유한다.
  const summary = MOCK_ROOMS.find(
    (candidate) => String(candidate.id) === roomId,
  )
  const roomClosed = summary?.status === 'CLOSED'
  const room = {
    ...MOCK_ROOM_DETAIL,
    id: summary?.id ?? MOCK_ROOM_DETAIL.id,
    title: summary?.title ?? MOCK_ROOM_DETAIL.title,
    sellerName: summary?.sellerName ?? MOCK_ROOM_DETAIL.sellerName,
    participantCount:
      summary?.participantCount ?? MOCK_ROOM_DETAIL.participantCount,
    status: summary?.status ?? MOCK_ROOM_DETAIL.status,
  }

  const [keyword, setKeyword] = useState('')
  const [panel, setPanel] = useState<RightPanel>('leaderboard')
  const [pendingBid, setPendingBid] = useState<{
    item: AuctionItemDetail
    amount: number
  } | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<BidResult | null>(null)

  const visibleItems = useMemo(() => {
    const trimmed = keyword.trim()
    if (!trimmed) return room.items
    return room.items.filter((item) => item.name.includes(trimmed))
  }, [room.items, keyword])

  const liveItems = room.items.filter((item) => item.status === 'ACTIVE')

  // 시작 전 물품만 빼고 리더보드를 보여준다. 종료된 물품은 최종 순위표가 된다.
  const rankedItems = [
    ...liveItems,
    ...room.items.filter((item) => item.status === 'CLOSED'),
  ]

  const openItem = (itemId: number) =>
    void navigate({
      to: '/rooms/$roomId/items/$itemId',
      params: { roomId, itemId: String(itemId) },
    })

  const confirmBid = () => {
    if (!pendingBid) return
    setSubmitting(true)

    // TODO: POST /api/v1/auction-items/{id}/bids 연동 (현재 목업)
    window.setTimeout(() => {
      setSubmitting(false)
      setResult({
        tone: 'success',
        itemName: pendingBid.item.name,
        amount: pendingBid.amount,
        message: '입찰이 등록됐어요',
      })
      setPendingBid(null)
      setPanel('leaderboard')
    }, 1200)
  }

  if (roomClosed) {
    return (
      <ClosedRoomView
        room={room}
        isGuest={isGuest}
        closedAt={summary?.closedAt ?? ''}
      />
    )
  }

  return (
    <>
      <LiveShell
        room={room}
        status={status}
        isGuest={isGuest}
        onShare={() => setPanel(panel === 'share' ? 'leaderboard' : 'share')}
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

            {visibleItems.length === 0 ? (
              <p className="mt-3 rounded-2xl border bg-card px-4 py-10 text-center text-[13px] font-medium text-neutral-muted">
                검색 결과가 없어요.
              </p>
            ) : (
              <ul className="mt-2.5 max-h-[420px] space-y-2 overflow-y-auto pr-0.5 lg:max-h-none lg:min-h-0 lg:flex-1">
                {visibleItems.map((item) => (
                  <LiveItemCard
                    key={item.id}
                    item={item}
                    onSelect={() => openItem(item.id)}
                  />
                ))}
              </ul>
            )}
          </div>
        }
        centerLabel="경매방 이벤트 · 실시간"
        center={
          <>
            <ConnectionBanner status={status} onRetry={retry} />
            {isGuest && <GuestNotice redirectTo={`/rooms/${roomId}`} />}

            <EventFeed events={MOCK_ROOM_EVENTS} />

            {/* 종료된 방은 위에서 ClosedRoomView 로 빠지므로 여기는 진행 중만 온다 */}
            <button
              type="button"
              onClick={() => setPanel('quickBid')}
              disabled={isGuest || liveItems.length === 0}
              className="ease-soft mt-4 h-14 shrink-0 rounded-[14px] bg-primary text-[18px] font-bold text-primary-foreground transition-all duration-150 hover:opacity-90 active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-50 disabled:active:scale-100"
            >
              입찰하기
            </button>
          </>
        }
        rightLabel={
          <span className="relative block h-4 overflow-hidden">
            {(Object.keys(PANEL_LABEL) as RightPanel[]).map((key) => (
              <span
                key={key}
                className={cn(
                  'ease-soft absolute inset-x-0 top-0 transition-all duration-300',
                  panel === key
                    ? 'translate-y-0 opacity-100'
                    : 'translate-y-4 opacity-0',
                )}
              >
                {PANEL_LABEL[key]}
              </span>
            ))}
          </span>
        }
        right={
          // 패널들을 겹쳐 두고 opacity·위치만 바꿔 부드럽게 교차시킨다.
          // 숨은 쪽은 `inert` 로 포커스와 클릭에서 완전히 빠진다.
          <div className="grid lg:max-h-full lg:min-h-0 lg:overflow-hidden">
            <RightSlot active={panel === 'leaderboard'}>
              {rankedItems.length === 0 ? (
                <p className="rounded-2xl border bg-card px-4 py-10 text-center text-[13px] font-medium text-neutral-muted">
                  아직 시작한 물품이 없어요.
                </p>
              ) : (
                <ul className="min-h-0 flex-1 space-y-4 overflow-y-auto pr-0.5">
                  {rankedItems.map((item) => (
                    <ItemLeaderboard key={item.id} item={item} />
                  ))}
                </ul>
              )}
            </RightSlot>

            <RightSlot active={panel === 'quickBid'}>
              <QuickBidOverlay
                items={liveItems}
                onSubmit={(item, amount) => {
                  setPendingBid({ item, amount })
                  setResult(null)
                  setPanel('confirm')
                }}
                onClose={() => setPanel('leaderboard')}
              />
            </RightSlot>

            <RightSlot active={panel === 'confirm'}>
              {pendingBid && (
                <BidConfirmPanel
                  item={pendingBid.item}
                  amount={pendingBid.amount}
                  pending={submitting}
                  onConfirm={confirmBid}
                  onCancel={() => {
                    setPendingBid(null)
                    setPanel('quickBid')
                  }}
                />
              )}
            </RightSlot>

            <RightSlot active={panel === 'share'}>
              <SharePanel
                roomTitle={room.title}
                shareCode={room.shareCode}
                onClose={() => setPanel('leaderboard')}
              />
            </RightSlot>
          </div>
        }
      />
      <BidResultToast result={result} onClose={() => setResult(null)} />
    </>
  )
}

/** 오른쪽 열에서 서로 교차되는 패널 한 칸. */
function RightSlot({
  active,
  children,
}: {
  active: boolean
  children: ReactNode
}) {
  return (
    <div
      inert={!active}
      className={cn(
        // 같은 그리드 칸에 겹쳐 둔다. absolute 와 달리 높이를 만들어낸다.
        'ease-soft flex flex-col [grid-area:1/1] transition-all duration-300',
        active
          ? 'translate-y-0 scale-100 opacity-100'
          : 'translate-y-2 scale-[0.98] opacity-0',
      )}
    >
      {children}
    </div>
  )
}
