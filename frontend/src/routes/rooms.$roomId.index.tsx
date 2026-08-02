import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Minus, Plus, Search, X } from 'lucide-react'

import {
  getGetDetail1QueryKey,
  getGetSummariesQueryKey,
  useGetDetail1,
  useGetSummaries,
} from '@/api/generated/경매-물품-조회/경매-물품-조회'
import { usePlace } from '@/api/generated/입찰/입찰'
import { toAuctionItemDetail, toAuctionItems } from '@/features/live/adapt-item'
import { toBidErrorMessage } from '@/features/live/bid-error'

import { BidConfirmPanel } from '@/features/live/components/bid-confirm-panel'
import { ClosedRoomView } from '@/features/live/components/closed-room-view'
import { ConnectionBanner } from '@/features/live/components/connection-banner'
import { EventFeed } from '@/features/live/components/event-feed'
import { GuestNotice, LiveShell } from '@/features/live/components/live-shell'
import { ItemLeaderboard } from '@/features/live/components/leaderboard'
import { ItemDetailPanel } from '@/features/live/components/item-detail-panel'
import { LiveItemList } from '@/features/live/components/live-item-list'
import { useListFlip } from '@/features/live/use-list-flip'
import {
  MOCK_EMPTY_ROOM,
  MOCK_PRODUCTS,
  MOCK_ROOM_DETAIL,
  themedRoomItems,
  MOCK_ROOMS,
} from '@/mocks/data'
import { ItemPickerModal } from '@/features/seller/components/item-picker-modal'
import { MobileItemDetailView } from '@/features/live/components/mobile-item-detail-view'
import { MobileLiveView } from '@/features/live/components/mobile-live-view'
import { Button } from '@/components/ui/button'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { Modal } from '@/components/ui/modal'
import { QuickBidOverlay } from '@/features/live/components/quick-bid-overlay'
import { SharePanel } from '@/features/live/components/share-panel'
import { cn } from '@/lib/utils'
import { useCountdown } from '@/hooks/use-countdown'
import { formatWon } from '@/lib/format'
import { toast } from '@/lib/toast'
import { useDevTools } from '@/lib/dev-tools'
import { useCurrentUser } from '@/lib/session'
import { useIsDesktop } from '@/hooks/use-media-query'
import {
  useRealtimeStatus,
  type SseEventPayload,
} from '@/features/live/use-realtime-status'
import type { AuctionItemDetail, Product, RoomEvent } from '@/mocks/types'

/**
 * 라이브 경매방 (Figma `WEB-09 · 구매자 · 라이브`).
 *
 * 왼쪽 물품 목록, 가운데 실시간 이벤트 + 입찰 CTA, 오른쪽 열.
 * 오른쪽 열은 상황에 따라 리더보드 / 빠른 입찰 / 입찰 확인 / 공유로 바뀐다.
 */
/** 자동 마감으로 생기는 이벤트 id 시작값. 목업 이벤트와 겹치지 않게 띄운다. */
const CLOSE_EVENT_BASE = 90_000

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
  const isDesktop = useIsDesktop()

  const isGuest = user === null

  // 목록의 방 요약으로 제목·상태를 맞춘다. 상세 물품은 목업 하나를 공유한다.
  const summary = MOCK_ROOMS.find(
    (candidate) => String(candidate.id) === roomId,
  )
  const roomClosed = summary?.status === 'CLOSED'
  // 아직 아무것도 없는 방은 빈 상태를 그대로 보여준다.
  const base =
    summary?.id === MOCK_EMPTY_ROOM.id ? MOCK_EMPTY_ROOM : MOCK_ROOM_DETAIL
  const room = {
    ...base,
    id: summary?.id ?? base.id,
    title: summary?.title ?? base.title,
    sellerName: summary?.sellerName ?? base.sellerName,
    participantCount: summary?.participantCount ?? base.participantCount,
    status: summary?.status ?? base.status,
    // 방 제목과 물품이 따로 놀지 않도록 테마를 맞춘다 (목업 한정).
    items: themedRoomItems(summary?.id ?? base.id, base.items),
    // 이걸 빼먹어서 목록의 역할이 무시되고 항상 BUYER 로 굳어 있었다.
    role: summary?.role ?? base.role,
  }

  const [keyword, setKeyword] = useState('')
  const [panel, setPanel] = useState<RightPanel>('leaderboard')
  const [pendingBid, setPendingBid] = useState<{
    item: AuctionItemDetail
    amount: number
  } | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [picking, setPicking] = useState(false)
  /*
   * 빼기는 "빼기 → 고르기 → 확인" 3단계다.
   * 버튼 한 번에 바로 빠지면 어느 물품이 없어지는지 알 수 없다.
   */
  const [removeMode, setRemoveMode] = useState(false)
  const [selectedForRemoval, setSelectedForRemoval] = useState<number[]>([])
  const [confirmingRemoval, setConfirmingRemoval] = useState(false)
  /**
   * 화면에서만 바꾼 편성. 물품 추가·삭제·마감이 아직 목업이라 서버 목록 위에
   * 덮어쓴다. 입찰이 성공하면 `null` 로 되돌려 서버 값이 다시 이기게 한다.
   */
  const [items, setItems] = useState<AuctionItemDetail[] | null>(null)
  /** 실시간 연동 전까지 새 이벤트 애니메이션을 눈으로 보려고 쌓아 둔다. */
  const [extraEvents, setExtraEvents] = useState<RoomEvent[]>([])

  const auctionRoomId = Number(roomId)
  const summaries = useGetSummaries(auctionRoomId, {
    // `/rooms/abc` 처럼 숫자가 아닌 주소로 들어오면 서버를 부르지 않는다.
    query: { enabled: Number.isInteger(auctionRoomId) },
  })
  const serverItems = useMemo(
    () => toAuctionItems(summaries.data?.data ?? []),
    [summaries.data],
  )

  // 편성을 바꾸기 전까지는 서버가 준 목록을 그대로 쓴다.
  const roomItems = items ?? serverItems
  const roomItemsRef = useRef(roomItems)
  useEffect(() => {
    roomItemsRef.current = roomItems
  })

  /**
   * SSE 이벤트 수신 핸들러.
   *
   * 이벤트 피드(extraEvents)와 물품 상태(items)를 동시에 갱신한다.
   */
  const handleSseEvent = useCallback(
    (payload: SseEventPayload) => {
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
          setItems(
            roomItems.map((item) =>
              item.id === payload.itemId
                ? { ...item, status: 'ACTIVE' as const, endsAt: payload.endedTime }
                : item,
            ),
          )
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
          setItems(
            roomItems.map((item) =>
              item.id === payload.itemId
                ? {
                    ...item,
                    currentPrice: payload.bidPrice,
                    topBidderNickname: payload.bidderNickname,
                    bidCount: item.bidCount + 1,
                    leaderboard: [
                      { rank: 1, nickname: payload.bidderNickname, amount: payload.bidPrice, isMe: false },
                      ...item.leaderboard.filter((entry) => entry.nickname !== payload.bidderNickname),
                    ]
                      .slice(0, 5)
                      .map((entry, index) => ({ ...entry, rank: index + 1 })),
                  }
                : item,
            ),
          )
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
          setItems(
            roomItems.map((item) =>
              item.id === payload.itemId
                ? {
                    ...item,
                    endsAt: new Date(
                      new Date(item.endsAt).getTime() + payload.extendSeconds * 1000,
                    ).toISOString(),
                  }
                : item,
            ),
          )
          break
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [roomItems],
  )

  const { status, retry } = useRealtimeStatus(roomId, handleSseEvent)

  const visibleItems = useMemo(() => {
    const trimmed = keyword.trim()
    if (!trimmed) return roomItems
    return roomItems.filter((item) => item.name.includes(trimmed))
  }, [roomItems, keyword])

  /*
   * 목록을 아직 못 받았을 때 왼쪽 열에 대신 넣을 것. 정상이면 `null`.
   *
   * 라우트 전체를 `RouteError` 로 날리지 않는다. 라이브 화면이 통째로
   * 언마운트되면 실시간 연결·타이머·쌓아둔 이벤트가 같이 끊긴다.
   */
  const itemsPlaceholder = summaries.isPending ? (
    <div
      className="mt-2.5 space-y-3"
      aria-busy
      aria-label="물품 목록 불러오는 중"
    >
      {[0, 1, 2].map((row) => (
        <div key={row} className="h-[76px] animate-skeleton rounded-2xl" />
      ))}
    </div>
  ) : summaries.isError ? (
    <div className="mt-2.5 rounded-2xl border bg-card px-4 py-8 text-center">
      <p className="text-[13px] font-medium text-neutral-muted">
        물품 목록을 불러오지 못했어요.
      </p>
      <Button
        variant="brandOutline"
        size="field"
        className="mt-3"
        onClick={() => void summaries.refetch()}
      >
        다시 시도
      </Button>
    </div>
  ) : null

  const roomEvents = extraEvents

  // 방을 만든 사람만 물품을 넣고 뺄 수 있다.
  /*
   * 개발 중에는 구매자 방에서도 판매자 화면을 확인할 수 있어야 한다.
   * 실제 권한은 서버가 검증하므로 이 토글은 DEV 빌드에만 노출한다.
   */
  /*
   * `null` 이면 실제 역할을 그대로 쓴다. 눌러서 정한 값이 있으면 그 값이
   * 이긴다. 예전에는 실제 역할과 OR 로 묶여 있어서, 판매자 방에서
   * "구매자 시점"을 골라도 계속 판매자로 남았다.
   */
  const [devSeller, setDevSeller] = useState<boolean | null>(null)
  // ⌘/Ctrl + Shift + D 로 개발용 UI 를 통째로 숨길 수 있다.
  const showDevTools = useDevTools()
  /** 방금 마감돼서 도장이 찍혀 있는 물품 */
  const [justClosedId, setJustClosedId] = useState<number | null>(null)
  /** 판매자가 방 전체를 끝낼 때 한 번 더 확인받는다. */
  const [closingRoom, setClosingRoom] = useState(false)
  /** 화면 위에 얹어 보여줄 물품 상세 */
  const [detailItemId, setDetailItemId] = useState<number | null>(null)
  const [detailAmount, setDetailAmount] = useState(0)
  const [detailPending, setDetailPending] = useState(false)
  const [detailFeedback, setDetailFeedback] = useState<{
    tone: 'success' | 'error'
    message: string
  } | null>(null)
  const isOwner =
    import.meta.env.DEV && devSeller !== null
      ? devSeller
      : room.role === 'SELLER'

  const liveItems = roomItems.filter((item) => item.status === 'ACTIVE')

  /*
   * 층으로 띄운 물품 상세.
   *
   * 목록 응답에는 설명·입찰 단위가 없다. 목업 입찰 단위로 금액을 계산하면
   * 서버가 단위 불일치(7005)로 거절하므로, 물품을 열었을 때만 상세를 부르고
   * 그 값으로 덮는다.
   */
  const listItem = roomItems.find((item) => item.id === detailItemId) ?? null
  const detailQuery = useGetDetail1(detailItemId ?? 0, {
    query: { enabled: detailItemId !== null },
  })
  const detailItem = useMemo(() => {
    if (!listItem) return null
    const dto = detailQuery.data?.data
    // 물품을 갈아탄 직후에는 이전 물품의 상세가 남아 있다. 그때는 목록 값을 쓴다.
    if (!dto || dto.auctionItemId !== listItem.id) return listItem
    return toAuctionItemDetail(dto, listItem)
  }, [listItem, detailQuery.data])

  const detailMinimum = detailItem
    ? detailItem.currentPrice + detailItem.bidUnit
    : 0
  // 상세가 닫혀 있으면 지금 시각을 넣어 0 초가 되게 한다(훅은 늘 같은 순서로).
  const detailRemaining = useCountdown(
    detailItem?.endsAt ?? new Date().toISOString(),
  )

  // 상세를 열 때마다 최소 입찰가로 맞춘다.
  useEffect(() => {
    if (!detailItem) return
    setDetailAmount(detailItem.currentPrice + detailItem.bidUnit)
    setDetailFeedback(null)
    // 물품이 바뀔 때만 초기화한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [detailItemId])

  const queryClient = useQueryClient()
  const placeBid = usePlace()

  /**
   * 입찰이 접수된 뒤 서버 값을 다시 읽어온다.
   *
   * 화면에서 덮어쓴 편성(`items`)을 비워야 새로 받은 현재가가 보인다.
   */
  const refreshAfterBid = (auctionItemId: number) => {
    setItems(null)
    void queryClient.invalidateQueries({
      queryKey: getGetSummariesQueryKey(auctionRoomId),
    })
    void queryClient.invalidateQueries({
      queryKey: getGetDetail1QueryKey(auctionItemId),
    })
  }

  const submitDetailBid = async () => {
    if (!detailItem) return
    setDetailPending(true)
    setDetailFeedback(null)

    try {
      await placeBid.mutateAsync({
        auctionItemId: detailItem.id,
        data: { amount: detailAmount },
      })

      // 서버가 접수한 뒤에만 성공으로 알린다 (루트 CLAUDE.md).
      setDetailFeedback({
        tone: 'success',
        message: `${formatWon(detailAmount)} 입찰이 등록됐어요.`,
      })
      toast.success('입찰이 등록됐어요', {
        description: `${detailItem.name} · ${formatWon(detailAmount)}`,
      })
      refreshAfterBid(detailItem.id)
    } catch (error) {
      const { title, description } = toBidErrorMessage(error)
      setDetailFeedback({ tone: 'error', message: `${title}. ${description}` })
      toast.error(title, { description })
    } finally {
      setDetailPending(false)
    }
  }

  /**
   * 마감 시각이 지난 물품을 종료로 넘긴다.
   *
   * 실시간 연동 전이라 클라이언트가 대신 본다. **서버가 확정한 상태가
   * 오면 그 값이 우선**이고, 이건 화면이 멈춰 보이지 않게 하는 임시 처리다.
   */
  useEffect(() => {
    const timer = window.setInterval(() => {
      const current = roomItemsRef.current
      const now = Date.now()
      const expired = current.filter(
        (item) =>
          item.status === 'ACTIVE' &&
          new Date(item.endsAt).getTime() <= now,
      )
      if (expired.length === 0) return

      const ids = new Set(expired.map((item) => item.id))
      setItems(
        current.map((item) =>
          ids.has(item.id) ? { ...item, status: 'CLOSED' as const } : item,
        ),
      )
      setJustClosedId(expired[0].id)
      setExtraEvents((prev) => [
        ...prev,
        ...expired.map((item, index) => ({
          id: CLOSE_EVENT_BASE + prev.length + index,
          at: new Date().toISOString(),
          kind: 'CLOSE' as const,
          message: `${item.name} 경매가 종료됐어요`,
          subtitle: item.topBidderNickname
            ? `${item.topBidderNickname} 낙찰 · ${formatWon(item.currentPrice)}`
            : undefined,
          emphasized: true,
        })),
      ])
    }, 1000)

    return () => window.clearInterval(timer)
    // roomItemsRef 를 통해 항상 최신값을 읽으므로 deps 없이 한 번만 등록한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // 도장은 한 번만 보여주고 지운다.
  useEffect(() => {
    if (justClosedId === null) return
    const timer = window.setTimeout(() => setJustClosedId(null), 2200)
    return () => window.clearTimeout(timer)
  }, [justClosedId])

  /**
   * 개발용 데모 입찰.
   *
   * 실시간 연동 전이라 이벤트·순위 애니메이션을 눈으로 볼 방법이 없다.
   * `shuffle` 이면 리더보드 순위까지 바꾼다.
   */
  const addDemoBid = (shuffle: boolean) => {
    const target = liveItems[0] ?? rankedItems[0]
    setExtraEvents((prev) => [
      ...prev,
      makeDemoEvent(prev.length, target?.name),
    ])
    if (shuffle && target) {
      setItems(
        roomItems.map((item) =>
          item.id === target.id ? bumpTopBid(item) : item,
        ),
      )
    }
  }

  // 시작 전 물품만 빼고 리더보드를 보여준다. 종료된 물품은 최종 순위표가 된다.
  const rankedItems = [
    ...liveItems,
    ...roomItems.filter((item) => item.status === 'CLOSED'),
  ]

  // 마감되어 순서가 바뀐 카드가 아래로 내려가는 걸 보여준다.
  const leaderboardRows = useListFlip<HTMLLIElement>(
    rankedItems.map((item) => `${item.id}:${item.status}`).join('|'),
    { duration: 520 },
  )

  /**
   * 아직 시작하지 않은 물품만 뺄 수 있다.
   * 진행 중이면 입찰이 이미 들어와 있고, 종료된 건 결과가 확정돼서 못 지운다.
   */
  const removable = roomItems.filter((item) => item.status === 'READY')

  // 이 방에 아직 없는 상품만 고를 수 있게 한다.
  const availableProducts = MOCK_PRODUCTS.filter(
    (product) => !roomItems.some((item) => item.name === product.name),
  )

  /*
   * 물품 상세는 **화면을 갈아끼우지 않고 위에 얹는다.**
   *
   * 라우트를 옮기면 경매방이 언마운트되면서 실시간 연결·타이머·쌓아둔
   * 이벤트가 전부 끊긴다. 데스크톱은 모달 층, 모바일은 전체를 덮는 층으로
   * 띄우고 그 아래에서 경매방은 계속 돌아가게 둔다.
   * (`/rooms/x/items/y` 라우트는 링크로 바로 들어오는 경우를 위해 남겨둔다.)
   */
  const openItem = (itemId: number) => setDetailItemId(itemId)

  /**
   * 종료된 방의 물품은 전부 끝난 것으로 본다.
   *
   * 목업 물품은 마감 시각이 미래라, 방이 종료됐는데도 카운트다운이
   * 계속 흘렀다. 상태와 마감 시각을 함께 과거로 맞춘다.
   */
  const closedRoom = {
    ...room,
    items: room.items.map((item) =>
      item.status === 'CLOSED'
        ? item
        : {
            ...item,
            status: 'CLOSED' as const,
            endsAt: summary?.closedAt
              ? new Date(summary.closedAt).toISOString()
              : new Date(Date.now() - 60_000).toISOString(),
          },
    ),
  }

  const confirmBid = async () => {
    if (!pendingBid) return
    setSubmitting(true)

    /*
     * 확인을 누르는 사이에 남이 먼저 올렸을 수 있다. 그 판정은 전부 서버가 한다.
     * 예전에는 여기서 현재가와 비교해 흉내를 냈지만, 이제는 응답만 보고 나눈다.
     */
    try {
      await placeBid.mutateAsync({
        auctionItemId: pendingBid.item.id,
        data: { amount: pendingBid.amount },
      })

      // 서버가 확정해 준 뒤에만 성공으로 알린다 (루트 CLAUDE.md).
      toast.success('입찰이 등록됐어요', {
        description: `${pendingBid.item.name} · ${formatWon(pendingBid.amount)}`,
      })
      refreshAfterBid(pendingBid.item.id)
      setPendingBid(null)
      setPanel('leaderboard')
    } catch (error) {
      const { title, description, retryable } = toBidErrorMessage(error)
      toast.error(title, { description })

      // 금액을 고쳐 다시 해볼 만한 실패면 빠른 입찰로 되돌린다.
      if (retryable) {
        setPanel('quickBid')
      } else {
        setPendingBid(null)
        setPanel('leaderboard')
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (roomClosed) {
    return (
      <ClosedRoomView
        room={closedRoom}
        isGuest={isGuest}
        closedAt={summary?.closedAt ?? ''}
      />
    )
  }

  /*
   * 모바일은 웹 3열을 접은 게 아니라 별도 구성이다 (Figma `MOB-04`).
   *
   * **CSS 로 감추면 안 된다.** `md:hidden` 은 display:none 일 뿐이라
   * 모바일 트리의 `<dialog>` 가 열리면 문서 전체가 inert 가 되어
   * 데스크톱 화면의 버튼이 전부 죽는다. 그래서 JS 로 하나만 그린다.
   */
  /*
   * 방 종료 확인. 데스크톱·모바일 분기가 갈리므로 한 번 만들어 양쪽에서 쓴다.
   * 예전에는 데스크톱 분기에만 있어서 모바일에서는 눌러도 아무 일이 없었다.
   */
  const closeRoomDialog = (
    <ConfirmDialog
      open={closingRoom}
      tone="danger"
      title="경매방을 종료할까요?"
      description="진행 중인 물품이 모두 마감되고 낙찰 결과가 확정됩니다. 되돌릴 수 없어요."
      confirmLabel="경매방 종료"
      onCancel={() => setClosingRoom(false)}
      onConfirm={() => {
        // TODO: POST /api/v1/auction-rooms/{id}/close 연동 (현재 목업)
        setItems(
          roomItems.map((item) =>
            item.status === 'CLOSED'
              ? item
              : {
                  ...item,
                  status: 'CLOSED' as const,
                  endsAt: new Date().toISOString(),
                },
          ),
        )
        setClosingRoom(false)
        toast.success('경매방을 종료했어요')
      }}
    />
  )

  if (!isDesktop) {
    return (
      <>
        <MobileLiveView
          room={room}
          status={status}
          isGuest={isGuest}
          events={roomEvents}
          items={roomItems}
          itemsPlaceholder={itemsPlaceholder}
          liveItems={liveItems}
          rankedItems={rankedItems}
          onRetry={retry}
          onShare={() => setPanel('share')}
          onCloseRoom={isOwner ? () => setClosingRoom(true) : undefined}
          onBack={() => void navigate({ to: '/rooms' })}
          onOpenItem={openItem}
          isOwner={isOwner}
          justClosedId={justClosedId}
          devTools={
            import.meta.env.DEV && showDevTools
              ? {
                  sellerView: isOwner,
                  onSellerViewChange: setDevSeller,
                  onDemoBid: addDemoBid,
                }
              : undefined
          }
          onBid={() => setPanel('quickBid')}
        />

        {/*
         * 물품 상세 층. 아래에서 경매방은 그대로 돌아간다.
         * 전체를 덮되 라우트는 그대로라 뒤로가기·연결이 끊기지 않는다.
         */}
        {detailItem && (
          <div className="animate-drawer fixed inset-0 z-40 overflow-y-auto overscroll-contain bg-background">
            <MobileItemDetailView
              item={detailItem}
              sellerName={room.sellerName}
              events={roomEvents.filter(
                (event) =>
                  event.subtitle === detailItem.name ||
                  event.message.includes(detailItem.name),
              )}
              remaining={detailRemaining}
              closed={detailItem.status === 'CLOSED'}
              ready={detailItem.status === 'READY'}
              urgent={false}
              amount={detailAmount}
              minimum={detailMinimum}
              onAmountChange={setDetailAmount}
              pending={detailPending}
              bidBlocked={
                isGuest ||
                isOwner ||
                detailItem.status !== 'ACTIVE' ||
                detailAmount < detailMinimum
              }
              feedback={detailFeedback}
              onBack={() => setDetailItemId(null)}
              onBid={submitDetailBid}
            />
          </div>
        )}

        {/*
         * 모바일에는 오른쪽 열이 없다. 퀵입찰·입찰 확인·공유는 같은 패널
         * 컴포넌트를 모달로 띄워 재사용한다. 리더보드는 세그먼트에 있다.
         */}
        <Modal
          open={panel !== 'leaderboard'}
          onClose={() => {
            setPendingBid(null)
            setPanel('leaderboard')
          }}
          labelledBy="mobile-panel-title"
          dismissible={!submitting}
          // 안쪽 패널이 `h-full` 을 쓰므로 다이얼로그 높이를 확정해야 한다.
          // auto 로 두면 목록이 0 높이로 접혀 화면이 깨진다.
          // 안쪽 패널이 다이얼로그 테두리에 딱 붙지 않도록 여백을 조금 준다.
          className="flex h-[min(680px,calc(100svh-3rem))] max-w-[420px] flex-col p-2"
        >
          <h2 id="mobile-panel-title" className="sr-only">
            {PANEL_LABEL[panel]}
          </h2>

          {panel === 'quickBid' && (
            <QuickBidOverlay
              items={liveItems}
              onSubmit={(item, amount) => {
                setPendingBid({ item, amount })
                setPanel('confirm')
              }}
              onClose={() => setPanel('leaderboard')}
            />
          )}

          {panel === 'confirm' && pendingBid && (
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

          {panel === 'share' && (
            <SharePanel
              roomId={room.id}
              roomTitle={room.title}
              onClose={() => setPanel('leaderboard')}
            />
          )}
        </Modal>

        {closeRoomDialog}
      </>
    )
  }

  return (
    <>
      <LiveShell
        room={room}
        isGuest={isGuest}
        onShare={() => setPanel(panel === 'share' ? 'leaderboard' : 'share')}
        onCloseRoom={isOwner ? () => setClosingRoom(true) : undefined}
        overlay={
          detailItem ? (
            <div className="flex min-h-0 flex-1 flex-col rounded-[20px] border bg-card p-4 shadow-xl">
              <div className="mb-3 flex shrink-0 items-center gap-2">
                <h2 className="text-[13px] font-bold text-neutral-tertiary">
                  물품 상세 · {detailItem.name}
                </h2>
                <button
                  type="button"
                  onClick={() => setDetailItemId(null)}
                  aria-label="물품 상세 닫기"
                  className="ease-soft ml-auto flex size-8 items-center justify-center rounded-full border bg-card text-neutral-secondary transition-all duration-150 hover:bg-fill active:scale-95"
                >
                  <X aria-hidden className="size-4" />
                </button>
              </div>

              <div className="flex min-h-0 flex-1 flex-col">
                <ItemDetailPanel
                  item={detailItem}
                  roomId={roomId}
                  itemId={String(detailItem.id)}
                  events={roomEvents.filter(
                    (event) =>
                      event.subtitle === detailItem.name ||
                      event.message.includes(detailItem.name),
                  )}
                  isGuest={isGuest}
                  closed={detailItem.status === 'CLOSED'}
                  ready={detailItem.status === 'READY'}
                  urgent={false}
                  remaining={detailRemaining}
                  amount={detailAmount}
                  minimum={detailMinimum}
                  pending={detailPending}
                  onAmountChange={setDetailAmount}
                  onBid={submitDetailBid}
                />
              </div>
            </div>
          ) : undefined
        }
        headerActions={
          import.meta.env.DEV && showDevTools ? (
            <span className="flex items-center gap-2">
              {/* 시점 전환 — 실제 권한은 서버가 정한다. 확인용 스위치다. */}
              <span className="flex h-8 items-center rounded-[10px] border border-border-strong bg-fill p-0.5">
                {(
                  [
                    { key: false, label: '구매자 시점' },
                    { key: true, label: '판매자 시점' },
                  ] as const
                ).map((option) => (
                  <button
                    key={option.label}
                    type="button"
                    onClick={() => setDevSeller(option.key)}
                    aria-pressed={isOwner === option.key}
                    className={cn(
                      'ease-soft flex h-7 items-center rounded-lg px-2.5 text-[11px] font-bold transition-all duration-150 active:scale-95',
                      isOwner === option.key
                        ? 'bg-card text-foreground shadow-sm'
                        : 'text-neutral-tertiary hover:text-neutral-secondary',
                    )}
                  >
                    {option.label}
                  </button>
                ))}
              </span>

              {roomItems.length > 0 && (
                <button
                  type="button"
                  onClick={() => addDemoBid(false)}
                  className="ease-soft flex h-8 items-center rounded-[10px] border border-border-strong bg-card px-2.5 text-[11px] font-bold text-neutral-tertiary transition-all duration-150 hover:bg-fill active:scale-95"
                >
                  이벤트
                </button>
              )}

              {rankedItems.length > 0 && (
                <button
                  type="button"
                  onClick={() => addDemoBid(true)}
                  className="ease-soft flex h-8 items-center rounded-[10px] border border-border-strong bg-card px-2.5 text-[11px] font-bold text-neutral-tertiary transition-all duration-150 hover:bg-fill active:scale-95"
                >
                  순위 변동
                </button>
              )}
            </span>
          ) : undefined
        }
        leftLabel={
          <span className="flex w-full items-center gap-2">
            물품 목록 ({roomItems.length})
            {/*
             * 방 주인만 편성을 바꿀 수 있다. 화면을 벗어나지 않도록 모달로 띄운다.
             * 진행 중·종료된 물품은 뺄 수 없어서, 뺄 게 없으면 버튼도 잠근다.
             */}
            {isOwner && (
              <span className="ml-auto flex items-center gap-1">
                <button
                  type="button"
                  onClick={() => setPicking(true)}
                  disabled={availableProducts.length === 0}
                  className="ease-soft flex h-7 items-center gap-1 rounded-lg border bg-card px-2.5 text-[12px] font-bold text-brand-500 transition-all duration-150 hover:bg-brand-50 active:scale-95 disabled:cursor-not-allowed disabled:text-neutral-muted disabled:hover:bg-card"
                >
                  <Plus aria-hidden className="size-3.5" />
                  추가
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setRemoveMode((prev) => !prev)
                    setSelectedForRemoval([])
                  }}
                  disabled={!removeMode && removable.length === 0}
                  aria-pressed={removeMode}
                  title={
                    removable.length === 0
                      ? '시작 전 물품만 뺄 수 있어요'
                      : undefined
                  }
                  className={cn(
                    'ease-soft flex h-7 items-center gap-1 rounded-lg border px-2.5 text-[12px] font-bold transition-all duration-150 active:scale-95 disabled:cursor-not-allowed disabled:text-neutral-muted',
                    removeMode
                      ? 'border-live/40 bg-live-surface text-live'
                      : 'bg-card text-live hover:bg-live-surface disabled:hover:bg-card',
                  )}
                >
                  <Minus aria-hidden className="size-3.5" />
                  {removeMode ? '선택 취소' : '빼기'}
                </button>
              </span>
            )}
          </span>
        }
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

            {itemsPlaceholder ??
              (visibleItems.length === 0 ? (
                <p className="mt-3 rounded-2xl border bg-card px-4 py-10 text-center text-[13px] font-medium text-neutral-muted">
                  검색 결과가 없어요.
                </p>
              ) : (
                <LiveItemList
                  items={visibleItems}
                  // 선택 모드에서는 시작 줄을 숨겨 조작이 섞이지 않게 한다.
                  canStart={isOwner && !removeMode}
                  isSelected={(item) =>
                    removeMode && selectedForRemoval.includes(item.id)
                  }
                  isDimmed={(item) => removeMode && item.status !== 'READY'}
                  justClosedId={justClosedId}
                  onSelect={(item) => {
                    if (!removeMode) {
                      openItem(item.id)
                      return
                    }
                    if (item.status !== 'READY') {
                      toast.error('진행 중이거나 끝난 물품은 뺄 수 없어요')
                      return
                    }
                    setSelectedForRemoval((prev) =>
                      prev.includes(item.id)
                        ? prev.filter((id) => id !== item.id)
                        : [...prev, item.id],
                    )
                  }}
                />
              ))}

            {/* 고른 뒤에 한 번 더 확인한다. 되돌릴 수 없는 조작이다. */}
            {removeMode && (
              <div className="animate-rise mt-2.5 flex shrink-0 items-center gap-2 rounded-2xl border border-live/30 bg-live-surface px-3 py-2.5">
                <p className="min-w-0 flex-1 text-[12px] font-semibold text-live">
                  {selectedForRemoval.length === 0
                    ? '뺄 물품을 골라주세요'
                    : `${selectedForRemoval.length}개 선택됨`}
                </p>
                <button
                  type="button"
                  onClick={() => {
                    setRemoveMode(false)
                    setSelectedForRemoval([])
                  }}
                  className="ease-soft flex h-8 items-center rounded-lg border bg-card px-3 text-[12px] font-bold text-neutral-secondary transition-all duration-150 hover:border-border-strong active:scale-95"
                >
                  취소
                </button>
                <button
                  type="button"
                  onClick={() => setConfirmingRemoval(true)}
                  disabled={selectedForRemoval.length === 0}
                  className="ease-soft flex h-8 items-center rounded-lg bg-live px-3 text-[12px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-95 disabled:cursor-not-allowed disabled:opacity-40"
                >
                  빼기
                </button>
              </div>
            )}
          </div>
        }
        centerLabel={
          <span className="flex w-full items-center gap-2">
            경매방 이벤트 · 실시간
          </span>
        }
        center={
          <>
            <ConnectionBanner status={status} onRetry={retry} />
            {isGuest && <GuestNotice redirectTo={`/rooms/${roomId}`} />}

            <EventFeed events={roomEvents} />

            {/* 종료된 방은 위에서 ClosedRoomView 로 빠지므로 여기는 진행 중만 온다 */}
            <button
              type="button"
              onClick={() => setPanel('quickBid')}
              disabled={isGuest || isOwner || liveItems.length === 0}
              title={isOwner ? '내 경매방에는 입찰할 수 없어요' : undefined}
              className="ease-soft mt-4 h-14 shrink-0 rounded-[14px] bg-brand-500 text-[18px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-50 disabled:active:scale-100"
            >
              {/* 판매자는 자기 방에 입찰할 수 없다 (Figma 판매자 라이브) */}
              {isOwner ? '판매자는 입찰할 수 없어요' : '입찰하기'}
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
          //
          // 트랙을 `minmax(0,1fr)` 로 못박는 게 중요하다. 기본값(auto)이면
          // 겹쳐 둔 패널 중 가장 넓은 것(퀵입찰·공유)이 트랙을 밀어내고,
          // 바깥 `overflow-hidden` 이 그만큼 리더보드 오른쪽을 잘라낸다.
          <div className="grid grid-cols-[minmax(0,1fr)] lg:min-h-0 lg:flex-1 lg:overflow-hidden">
            <RightSlot active={panel === 'leaderboard'}>
              {rankedItems.length === 0 ? (
                <p className="m-2 rounded-2xl border bg-card px-4 py-10 text-center text-[13px] font-medium text-neutral-muted">
                  아직 시작한 물품이 없어요.
                </p>
              ) : (
                <ul className="min-h-0 flex-1 space-y-4 overflow-y-auto p-2">
                  {rankedItems.map((item) => (
                    <ItemLeaderboard
                      key={item.id}
                      item={item}
                      justClosed={justClosedId === item.id}
                      // 마감되면 이 카드가 목록 아래로 미끄러진다.
                      rowRef={(element) => {
                        if (element)
                          leaderboardRows.current.set(item.id, element)
                        else leaderboardRows.current.delete(item.id)
                      }}
                    />
                  ))}
                </ul>
              )}
            </RightSlot>

            <RightSlot active={panel === 'quickBid'}>
              <QuickBidOverlay
                items={liveItems}
                onSubmit={(item, amount) => {
                  setPendingBid({ item, amount })
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
                roomId={room.id}
                roomTitle={room.title}
                onClose={() => setPanel('leaderboard')}
              />
            </RightSlot>
          </div>
        }
      />

      {closeRoomDialog}

      <ItemManagement
        picking={picking}
        onPickingChange={setPicking}
        products={availableProducts}
        onAdd={(names) =>
          setItems([
            ...roomItems,
            ...names.map((name, index) => makeDraftItem(room.id, name, index)),
          ])
        }
        removing={
          confirmingRemoval
            ? roomItems.filter((item) => selectedForRemoval.includes(item.id))
            : []
        }
        onRemovingCancel={() => setConfirmingRemoval(false)}
        onRemove={(targets) => {
          const ids = new Set(targets.map((target) => target.id))
          setItems(roomItems.filter((item) => !ids.has(item.id)))
          setConfirmingRemoval(false)
          setRemoveMode(false)
          setSelectedForRemoval([])
        }}
      />
    </>
  )
}

/**
 * 물품 추가·빼기.
 *
 * 추가는 경매방 생성 때와 같은 `ItemPickerModal` 을 쓴다. 라이브 화면을
 * 벗어나지 않고 그 자리에서 고른다.
 */
function ItemManagement({
  picking,
  onPickingChange,
  products,
  onAdd,
  removing,
  onRemovingCancel,
  onRemove,
}: {
  picking: boolean
  onPickingChange: (open: boolean) => void
  products: Product[]
  onAdd: (names: string[]) => void
  /** 확인을 기다리는 물품들. 비어 있으면 다이얼로그를 닫는다. */
  removing: AuctionItemDetail[]
  onRemovingCancel: () => void
  onRemove: (items: AuctionItemDetail[]) => void
}) {
  return (
    <>
      <ItemPickerModal
        open={picking}
        onClose={() => onPickingChange(false)}
        products={products}
        onConfirm={(productIds) => {
          // TODO: POST /api/v1/auction-rooms/{id}/items 연동 (현재 목업)
          const names = productIds
            .map((id) => products.find((product) => product.id === id)?.name)
            .filter((name): name is string => name !== undefined)
          onAdd(names)
          toast.success(`물품 ${names.length}개를 추가했어요`)
        }}
      />

      <ConfirmDialog
        open={removing.length > 0}
        tone="danger"
        title={
          removing.length > 1
            ? `물품 ${removing.length}개를 뺄까요?`
            : '이 물품을 경매방에서 뺄까요?'
        }
        description={
          removing.length > 0
            ? `${removing.map((item) => item.name).join(', ')} 을(를) 뺍니다. 아직 시작하지 않은 물품이라 다시 추가할 수 있어요.`
            : undefined
        }
        confirmLabel="빼기"
        onCancel={onRemovingCancel}
        onConfirm={() => {
          if (removing.length === 0) return
          // TODO: DELETE /api/v1/auction-rooms/{id}/items/{itemId} 연동 (현재 목업)
          onRemove(removing)
          toast.success(`물품 ${removing.length}개를 뺐어요`)
        }}
      />
    </>
  )
}

/**
 * 데모 입찰 반영.
 *
 * 새 입찰자가 최고가를 넘겨 1위가 되도록 리더보드를 다시 매긴다.
 * 순위가 실제로 바뀌어야 FLIP 애니메이션을 확인할 수 있다.
 */
function bumpTopBid(item: AuctionItemDetail): AuctionItemDetail {
  const amount = item.currentPrice + item.bidUnit
  const nickname = `데모입찰러${item.bidCount + 1}`

  const leaderboard = [
    { rank: 1, nickname, amount, isMe: false },
    ...item.leaderboard.map((entry) => ({ ...entry, rank: entry.rank + 1 })),
  ].slice(0, 5)

  return {
    ...item,
    currentPrice: amount,
    bidCount: item.bidCount + 1,
    topBidderNickname: nickname,
    leaderboard,
  }
}

/**
 * 개발용 데모 이벤트.
 *
 * 실시간(SSE/WebSocket)이 붙기 전까지 **새 이벤트가 들어올 때의 움직임**을
 * 눈으로 확인하려고 둔다. `import.meta.env.DEV` 안에서만 쓰인다.
 */
function makeDemoEvent(index: number, itemName?: string): RoomEvent {
  const amount = 86000 + index * 1000
  return {
    id: 10_000 + index,
    at: new Date().toISOString(),
    kind: 'BID',
    message: `데모입찰러님이 ${amount.toLocaleString('ko-KR')}원 입찰`,
    subtitle: itemName,
    emphasized: true,
  }
}

/** 목업용 신규 물품. API 를 붙이면 서버가 만들어 준 물품을 그대로 쓴다. */
function makeDraftItem(
  roomId: number,
  name: string,
  index: number,
): AuctionItemDetail {
  return {
    id: Date.now() + index,
    roomId,
    name,
    category: '기타',
    description: '',
    productUrl: null,
    status: 'READY',
    startPrice: 10000,
    currentPrice: 10000,
    bidUnit: 1000,
    endsAt: new Date(Date.now() + 30 * 60 * 1000).toISOString(),
    bidCount: 0,
    topBidderNickname: null,
    leaderboard: [],
    history: [],
    extended: false,
  }
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
        // `min-w-0` 이 없으면 안쪽 내용이 칸을 넓혀 옆 열을 밀어낸다.
        'ease-soft flex min-h-0 min-w-0 flex-col [grid-area:1/1] transition-all duration-300',
        active
          ? 'translate-y-0 scale-100 opacity-100'
          : // `inert` 만으로는 부족하다. 투명한 패널이 위에 겹쳐 있으면
            // 아래 패널의 버튼 클릭을 그대로 가로챈다.
            'pointer-events-none translate-y-2 scale-[0.98] opacity-0',
      )}
    >
      {children}
    </div>
  )
}
