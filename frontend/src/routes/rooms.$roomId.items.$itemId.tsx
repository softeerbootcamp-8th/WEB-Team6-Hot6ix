import { Search, X } from 'lucide-react'
import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

import {
  useGetDetail1,
  useGetSummaries,
} from '@/api/generated/경매-물품/경매-물품'
import { usePlace } from '@/api/generated/입찰/입찰'
import { GuestNotice, LiveShell } from '@/features/live/components/live-shell'
import { ItemDetailPanel } from '@/features/live/components/item-detail-panel'
import { LiveItemList } from '@/features/live/components/live-item-list'
import { RouteError, RoutePending } from '@/components/route-states'
import {
  fallbackItem,
  toAuctionItemDetail,
  toAuctionItems,
} from '@/features/live/adapt-item'
import { toBidErrorMessage } from '@/features/live/bid-error'
import { findMockItem, findMockRoom, MOCK_ROOM_DETAIL } from '@/mocks/data'
import { MobileItemDetailView } from '@/features/live/components/mobile-item-detail-view'
import { formatWon } from '@/lib/format'
import { isClosingSoon, useCountdown } from '@/hooks/use-countdown'
import { toast } from '@/lib/toast'
import { useDevTools } from '@/lib/dev-tools'
import { useCurrentUser } from '@/lib/session'
import { useIsDesktop } from '@/hooks/use-media-query'
import {
  useRealtimeStatus,
  type SseEventPayload,
} from '@/features/live/use-realtime-status'
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
  const isDesktop = useIsDesktop()
  const showDevTools = useDevTools()

  const auctionRoomId = Number(roomId)
  const auctionItemId = Number(itemId)

  const summaries = useGetSummaries(auctionRoomId, {
    query: { enabled: Number.isInteger(auctionRoomId) },
  })
  const detailQuery = useGetDetail1(auctionItemId, {
    query: { enabled: Number.isInteger(auctionItemId) },
  })
  const placeBid = usePlace()

  const serverItems = useMemo(
    () => toAuctionItems(summaries.data?.data ?? [], user?.nickname ?? null),
    [summaries.data, user?.nickname],
  )

  /*
   * 방 제목·판매자명은 아직 목업이다. 물품 배열만 서버 값으로 바꾼다.
   * 서버가 아무것도 안 주면 그 방의 목업 물품으로 채운다 (시연용 임시 조치).
   */
  const mockRoom = findMockRoom(auctionRoomId) ?? MOCK_ROOM_DETAIL
  const mockItem = findMockItem(auctionItemId)
  const room = {
    ...mockRoom,
    items: serverItems.length > 0 ? serverItems : mockRoom.items,
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

  /*
   * 상세 API 가 원본이고, 목록은 왼쪽 열용이다.
   *
   * 아직 아무것도 못 받았으면 목업 하나를 자리에 놓는다. 훅 순서를 지키려면
   * 렌더 도중에 빠져나갈 수 없어서다. 실제로 목업이 보이는 일은 없다 —
   * 아래에서 로딩·에러를 먼저 걸러낸다.
   */
  const detailDto = detailQuery.data?.data
  const base = useMemo(() => {
    const listItem =
      serverItems.find((candidate) => candidate.id === auctionItemId) ??
      mockItem ??
      fallbackItem(0)
    if (!detailDto || detailDto.auctionItemId !== auctionItemId) return listItem
    return toAuctionItemDetail(detailDto, listItem, user?.nickname ?? null)
  }, [serverItems, detailDto, auctionItemId, mockItem, user?.nickname])

  const item = override?.id === base.id ? override : base

  const handleSseEvent = useCallback(
    (payload: SseEventPayload) => {
      // 현재 보고 있는 물품과 관계없는 이벤트는 무시한다.
      if (payload.itemId !== item.id) return

      const eventId = Date.now()

      switch (payload.kind) {
        case 'ItemStarted':
          setExtraEvents((prev) => [
            ...prev,
            {
              id: eventId,
              at: new Date().toISOString(),
              kind: 'START',
              message: `${payload.itemName} 경매가 시작됐어요`,
            },
          ])
          setOverride((prev) => ({
            ...(prev ?? item),
            status: 'ACTIVE' as const,
            endsAt: payload.endedTime,
          }))
          break

        case 'ItemClosingSoon':
          setExtraEvents((prev) => [
            ...prev,
            {
              id: eventId,
              at: new Date().toISOString(),
              kind: 'CLOSE',
              message: `${payload.itemName} 마감 1분 전`,
              emphasized: true,
            },
          ])
          break

        case 'BidPlaced':
          setExtraEvents((prev) => [
            ...prev,
            {
              id: eventId,
              at: new Date().toISOString(),
              kind: 'BID',
              message: `${payload.bidderNickname}님이 ${formatWon(payload.bidPrice)} 입찰`,
              subtitle: payload.itemName,
              emphasized: true,
            },
          ])
          setOverride((prev) => {
            const base = prev ?? item
            return {
              ...base,
              currentPrice: payload.bidPrice,
              topBidderNickname: payload.bidderNickname,
              bidCount: base.bidCount + 1,
              leaderboard: [
                {
                  rank: 1,
                  nickname: payload.bidderNickname,
                  amount: payload.bidPrice,
                  isMe: false,
                },
                ...base.leaderboard.filter(
                  (entry) => entry.nickname !== payload.bidderNickname,
                ),
              ]
                .slice(0, 5)
                .map((entry, index) => ({ ...entry, rank: index + 1 })),
            }
          })
          break

        case 'SoftCloseExtended':
          setExtraEvents((prev) => [
            ...prev,
            {
              id: eventId,
              at: new Date().toISOString(),
              kind: 'EXTEND',
              message: `마감 1분 전 입찰 발생 · 마감 +${payload.extendSeconds <= 60 ? `${payload.extendSeconds}초` : `${Math.floor(payload.extendSeconds / 60)}분`} 자동 연장`,
              subtitle: payload.itemName,
              emphasized: true,
            },
          ])
          setOverride((prev) => {
            const base = prev ?? item
            return {
              ...base,
              endsAt: new Date(
                new Date(base.endsAt).getTime() + payload.extendSeconds * 1000,
              ).toISOString(),
            }
          })
          break

        case 'ItemEnded':
          setExtraEvents((prev) => [
            ...prev,
            {
              id: eventId,
              at: new Date().toISOString(),
              kind: 'CLOSE',
              message: payload.winnerNickname
                ? `${payload.itemName} 낙찰 확정`
                : `${payload.itemName} 경매 종료 · 낙찰자 없음`,
              // 유찰이면 둘 다 null 이라 낙찰 줄을 붙이지 않는다.
              ...(payload.winnerNickname &&
                payload.finalPrice !== null && {
                  subtitle: `${formatWon(payload.finalPrice)} · ${payload.winnerNickname}님`,
                  emphasized: true,
                }),
            },
          ])
          setOverride((prev) => ({
            ...(prev ?? item),
            status: 'CLOSED' as const,
            // 낙찰자가 실렸으면 낙찰, 비었으면 유찰이다.
            sold: payload.winnerNickname !== null,
          }))
          break
      }
    },
    [item],
  )

  const { status } = useRealtimeStatus(roomId, handleSseEvent)

  const disconnectNotifiedRef = useRef(false)
  useEffect(() => {
    if (status === 'reconnecting' || status === 'failed') {
      if (!disconnectNotifiedRef.current) {
        disconnectNotifiedRef.current = true
        toast.error(
          '실시간 연결이 끊겼어요. 표시된 금액이 최신이 아닐 수 있어요.',
        )
      }
    } else if (status === 'connected') {
      disconnectNotifiedRef.current = false
    }
  }, [status])

  const remaining = useCountdown(item.endsAt)
  const closed = item.status === 'CLOSED'
  const ready = item.status === 'READY'
  const urgent = !closed && isClosingSoon(remaining)

  const minimum = item.currentPrice + item.bidUnit
  const [amount, setAmount] = useState(minimum)

  // 상세가 도착하면 최소 입찰가로 다시 맞춘다. 자리값이 남아 있으면 안 된다.
  useEffect(() => {
    setAmount(item.currentPrice + item.bidUnit)
  }, [item.currentPrice, item.bidUnit])

  const visibleItems = useMemo(() => {
    const trimmed = keyword.trim()
    if (!trimmed) return room.items
    return room.items.filter((candidate) => candidate.name.includes(trimmed))
  }, [room.items, keyword])

  // 실시간으로 받은 이벤트만 보여준다. 처음 들어오면 비어 있다.
  const itemEvents = extraEvents

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

  const submitBid = async () => {
    setPending(true)
    setFeedback(null)

    try {
      await placeBid.mutateAsync({
        auctionItemId: item.id,
        data: { amount },
      })

      // 서버가 접수한 뒤에만 성공으로 알린다 (루트 CLAUDE.md).
      setFeedback({
        tone: 'success',
        message: `${formatWon(amount)} 입찰이 등록됐어요.`,
      })
      toast.success('입찰이 등록됐어요', {
        description: `${item.name} · ${formatWon(amount)}`,
      })
      // 데모 입찰로 덮어쓴 값을 비워야 서버가 준 현재가가 보인다.
      setOverride(null)
      void detailQuery.refetch()
      void summaries.refetch()
    } catch (error) {
      const { title, description } = toBidErrorMessage(error)
      setFeedback({ tone: 'error', message: `${title}. ${description}` })
      toast.error(title, { description })
    } finally {
      setPending(false)
    }
  }

  /*
   * 여기부터는 화면을 통째로 갈아끼운다. 훅은 위에서 전부 부른 뒤다.
   *
   * 라이브 경매방(`/rooms/$roomId`)과 달리 이 라우트는 링크로 바로 들어오는
   * 단독 페이지라, 상세를 못 받으면 보여줄 게 없다. 실시간 연결을 지킬 이유도
   * 없으므로 전역 상태 화면을 그대로 쓴다.
   */
  // 목업 물품으로 대신 채웠으면 로딩·에러 화면으로 덮지 않는다.
  if (detailQuery.isPending && !mockItem) return <RoutePending />
  if (detailQuery.isError && !mockItem) {
    return (
      <RouteError
        error={detailQuery.error as Error}
        reset={() => void detailQuery.refetch()}
      />
    )
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
