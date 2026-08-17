import { Search, X } from 'lucide-react'
import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

import {
  getGetSummariesQueryKey,
  useGetDetail1,
  useGetSummaries,
} from '@/api/generated/경매-물품/경매-물품'
import { usePlace } from '@/api/generated/입찰/입찰'
import { GuestNotice, LiveShell } from '@/features/live/components/live-shell'
import { ItemDetailPanel } from '@/features/live/components/item-detail-panel'
import { LiveItemList } from '@/features/live/components/live-item-list'
import { RouteError, RoutePending } from '@/components/route-states'
import {
  emptyItem,
  toAuctionItemDetail,
  toAuctionItems,
} from '@/features/live/adapt-item'
import { toBidErrorMessage } from '@/features/live/bid-error'
import { createBidRequestIdTracker } from '@/features/live/bid-request-id'
import {
  createOwnBidTracker,
  trackOwnBidAttempt,
} from '@/features/live/own-bid-tracker'
import { toAuctionRoomDetail } from '@/features/live/adapt-room'
import {
  getGetRoomByShareCodeQueryKey,
  useGetRoomByShareCode,
} from '@/api/generated/경매방/경매방'
import { MobileItemDetailView } from '@/features/live/components/mobile-item-detail-view'
import { formatClosingLead, formatWon } from '@/lib/format'
import { isClosingSoon, useCountdown } from '@/hooks/use-countdown'
import { toast } from '@/lib/toast'
import { useDevTools } from '@/lib/dev-tools'
import { useCurrentUser } from '@/lib/session'
import { useIsDesktop } from '@/hooks/use-media-query'
import {
  useRealtimeStatus,
  type SseEventPayload,
} from '@/features/live/use-realtime-status'
import type { AuctionItemDetail, RoomEvent } from '@/types/domain'

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

export const Route = createFileRoute('/rooms/$shareCode/items/$itemId')({
  component: AuctionItemPage,
})

function AuctionItemPage() {
  const { shareCode, itemId } = Route.useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const user = useCurrentUser()
  const isDesktop = useIsDesktop()
  const showDevTools = useDevTools()

  const auctionItemId = Number(itemId)

  const summaries = useGetSummaries(shareCode)
  const detailQuery = useGetDetail1(shareCode, auctionItemId, {
    query: { enabled: Number.isInteger(auctionItemId) },
  })
  const placeBid = usePlace()
  const bidRequestIds = useRef(createBidRequestIdTracker()).current

  /**
   * 내가 접수시킨 입찰의 `물품id:금액`. 실시간 입찰 이벤트가 내 것인지 가린다.
   * 자세한 이유는 경매방 화면(`rooms.$shareCode.index.tsx`)에 적어 두었다.
   */
  const ownBids = useRef(createOwnBidTracker()).current

  /*
   * 방 정보도 서버에서 받는다. 예전에는 제목·판매자명만 쓴다고 목업으로
   * 뒀는데, 헤더가 입찰 단위와 연장 규칙까지 보여주게 되면서 남의 방 숫자가
   * 사실인 것처럼 붙었다. 규칙은 방마다 달라 목업으로 대신할 수 없다.
   *
   * **물품 목록보다 먼저 읽는다.** 목록 응답에 입찰 단위가 없어서 방 값을
   * 넘겨야 하기 때문이다.
   */
  const roomQuery = useGetRoomByShareCode(shareCode)
  const roomBidUnit = roomQuery.data?.data?.bidIncrement ?? 0

  const serverItems = useMemo(
    () => toAuctionItems(summaries.data?.data ?? [], roomBidUnit),
    [summaries.data, roomBidUnit],
  )

  const room = useMemo(
    () => toAuctionRoomDetail(roomQuery.data?.data ?? {}, serverItems),
    [roomQuery.data, serverItems],
  )
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
   * 아직 아무것도 못 받았으면 빈 물품을 자리에 놓는다. 훅 순서를 지키려면 렌더
   * 도중에 빠져나갈 수 없어서다. **이 빈 물품이 화면에 보이는 일은 없다** —
   * 아래에서 로딩·에러를 먼저 걸러내고 돌려보낸다.
   */
  const detailDto = detailQuery.data?.data
  const base = useMemo(() => {
    const listItem =
      serverItems.find((candidate) => candidate.id === auctionItemId) ??
      emptyItem(auctionItemId)
    if (!detailDto || detailDto.auctionItemId !== auctionItemId) return listItem
    return toAuctionItemDetail(detailDto, roomBidUnit)
  }, [serverItems, detailDto, auctionItemId, roomBidUnit])

  const item = override?.id === base.id ? override : base

  const handleSseEvent = useCallback(
    (payload: SseEventPayload) => {
      /*
       * 참여자 수는 물품에 딸린 이벤트가 아니다. 이 화면에는 방 헤더가
       * 없어서 보여줄 자리도 없으니 물품 필터보다 먼저 걸러낸다.
       */
      if (payload.kind === 'ParticipantCount') return

      /*
       * 현재 보고 있는 물품과 관계없는 이벤트는 무시한다.
       *
       * 방 단위 이벤트(`RoomClosed`·`RoomUpdated`·`ItemAdded`·`ItemRemoved`)에는
       * itemId 가 없어서 이 검사를 그냥 지나가고, 아래 switch 가 받는다. **그 분기가
       * 없던 동안에는 방이 끝나도 이 화면만 LIVE 인 채로 멈춰 있었다.**
       */
      if ('itemId' in payload && payload.itemId !== item.id) return

      const eventId = Date.now()

      switch (payload.kind) {
        case 'ItemStarted':
          setExtraEvents((prev) => [
            ...prev,
            {
              id: eventId,
              at: new Date().toISOString(),
              kind: 'START',
              message: '경매가 시작됐어요',
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
              message: `마감 ${formatClosingLead(payload.remainingSeconds)} 전`,
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
              emphasized: true,
            },
          ])
          setOverride((prev) => {
            const base = prev ?? item
            return {
              ...base,
              currentPrice: payload.bidPrice,
              topBidderNickname: payload.bidderNickname,
              leaderboard: [
                {
                  rank: 1,
                  nickname: payload.bidderNickname,
                  amount: payload.bidPrice,
                  isMe: ownBids.has(payload.itemId, payload.bidPrice),
                },
                ...base.leaderboard.filter(
                  (entry) => entry.nickname !== payload.bidderNickname,
                ),
              ]
                // 서버 리더보드도 상위 3명이다. 더 들고 있으면 서버 값이 다시
                // 올 때 줄 수가 줄어들어 화면이 들썩인다.
                .slice(0, 3)
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
              message: `마감 직전 입찰 발생 · 마감 +${payload.extendSeconds <= 60 ? `${payload.extendSeconds}초` : `${Math.floor(payload.extendSeconds / 60)}분`} 자동 연장`,
              emphasized: true,
            },
          ])
          // 서버가 준 마감 시각을 그대로 쓴다. 직접 더하면 이벤트가 두 번 오거나
          // 유실됐을 때 화면 카운트다운만 서버와 어긋난 채로 남는다.
          setOverride((prev) => ({
            ...(prev ?? item),
            endsAt: payload.endedTime,
          }))
          break

        /*
         * 판매자가 마감을 앞당겼다. **이 분기가 없어서 이 화면에 있던 사람만
         * 카운트다운이 안 바뀌었다.** 이벤트는 도착하는데 받는 곳이 없었다.
         * 연장과 같은 이유로 서버가 준 마감 시각을 그대로 쓴다.
         */
        case 'ItemCloseAdvanced':
          setExtraEvents((prev) => [
            ...prev,
            {
              id: eventId,
              at: new Date().toISOString(),
              // 연장과 같은 자리다. 둘 다 마감 시각이 바뀐 사건이다.
              kind: 'EXTEND',
              message: `판매자가 마감 앞당김 · ${formatClosingLead(payload.remainingSeconds)} 뒤 마감`,
              emphasized: true,
            },
          ])
          setOverride((prev) => ({
            ...(prev ?? item),
            endsAt: payload.endedTime,
          }))
          break

        /*
         * 방이 통째로 끝났다. 이 화면은 진행 중인 방 전용이라 결과 화면으로 옮긴다.
         * 분기가 없을 때는 방이 끝나도 여기 있던 사람만 LIVE 인 채로 멈춰 있었다.
         */
        case 'RoomClosed':
          void navigate({
            to: '/rooms/$shareCode/result',
            params: { shareCode },
            replace: true,
          })
          break

        /*
         * 판매자가 물품을 넣거나 뺐다. 왼쪽 목록이 이 응답에서 나오므로 다시 읽는다.
         * 이벤트 피드에는 쌓지 않는다 — 편성 변경은 경매 진행 사건이 아니다.
         */
        case 'ItemAdded':
        case 'ItemRemoved':
          void queryClient.invalidateQueries({
            queryKey: getGetSummariesQueryKey(shareCode),
          })
          break

        /* 판매자가 방 설정을 바꿨다. 제목·입찰 단위가 방 정보에서 나온다. */
        case 'RoomUpdated':
          void queryClient.invalidateQueries({
            queryKey: getGetRoomByShareCodeQueryKey(shareCode),
          })
          break

        case 'ItemEnded':
          setExtraEvents((prev) => [
            ...prev,
            {
              id: eventId,
              at: new Date().toISOString(),
              // 낙찰가·낙찰자를 보조 줄로 빼지 않고 입찰 문구와 같은 결로 한 줄에 담는다.
              kind: payload.winnerNickname ? 'WIN' : 'CLOSE',
              message:
                payload.winnerNickname && payload.finalPrice !== null
                  ? `${payload.winnerNickname}님이 ${formatWon(payload.finalPrice)}에 낙찰`
                  : '경매 종료 · 낙찰자 없음',
              emphasized: true,
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
    [item, shareCode, navigate, queryClient, ownBids],
  )

  const { status } = useRealtimeStatus(shareCode, handleSseEvent)

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
  // 시작 전 물품은 마감 시각이 없어 남은 시간이 늘 0 이다. 임박에서 걸러낸다.
  const urgent = !closed && !ready && isClosingSoon(remaining)

  const minimum = item.currentPrice + item.bidUnit
  const [amount, setAmount] = useState(minimum)

  /*
   * 물품이 정해지거나 상세가 도착하면 최소 입찰가로 맞춘다. 자리값이 남아
   * 있으면 안 된다. 입찰 단위는 상세 응답에만 있고 남의 입찰로는 바뀌지 않아서,
   * 이 값이 바뀌었다는 건 상세가 방금 도착했다는 뜻이다.
   */
  useEffect(() => {
    setAmount(item.currentPrice + item.bidUnit)
    // 물품이 바뀌거나 입찰 단위가 확정될 때만 초기화한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [item.id, item.bidUnit])

  /** 입찰 확인 화면에 들어가 있는지. 그동안 자동 상향을 멈춘다. */
  const [confirming, setConfirming] = useState(false)

  /*
   * 남이 입찰해서 최소가가 올라가면 입력 금액도 끌어올린다. 최소가 이상을 직접
   * 적어둔 경우는 건드리지 않는다 — 올려 적은 금액을 남의 입찰 때문에 되돌리면
   * 안 된다. 예전에는 현재가가 바뀔 때마다 최소가로 덮어써서, 5만 원을 적어둔
   * 사이에 남이 입찰하면 입력값이 지워졌다.
   *
   * **확인 화면이 떠 있는 동안에는 건드리지 않는다.** 확정하려는 금액이 눈앞에서
   * 바뀌면 무엇을 확정하는 건지 알 수 없다 (`QuickBidOverlay` 와 같은 규칙).
   */
  useEffect(() => {
    if (confirming) return
    setAmount((prev) => (prev < minimum ? minimum : prev))
  }, [minimum, confirming])

  const visibleItems = useMemo(() => {
    const trimmed = keyword.trim()
    if (!trimmed) return room.items
    return room.items.filter((candidate) => candidate.name.includes(trimmed))
  }, [room.items, keyword])

  /*
   * 실시간으로 받은 이벤트만 보여준다. 처음 들어오면 비어 있다.
   *
   * 이 화면의 `extraEvents` 는 이미 이 물품 것만 담긴다 — SSE 핸들러가
   * `payload.itemId !== item.id` 를 먼저 걸러낸다.
   */
  const itemEvents = extraEvents

  /**
   * 개발용 데모 입찰. 경매방 화면의 같은 버튼과 짝을 맞춘다.
   * `shuffle` 이면 새 최고가가 들어와 리더보드 순위가 바뀐다.
   */
  const addDemoBid = (shuffle: boolean) => {
    const nickname = `데모입찰러${item.leaderboard.length + 1}`
    const next = item.currentPrice + item.bidUnit

    setExtraEvents((prev) => [
      ...prev,
      {
        id: DEMO_EVENT_BASE + prev.length,
        at: new Date().toISOString(),
        kind: 'BID' as const,
        message: `${nickname}님이 ${formatWon(next)} 입찰`,
      },
    ])

    if (!shuffle) return
    setOverride({
      ...item,
      currentPrice: next,
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
      const requestId = bidRequestIds.acquire(item.id, amount)
      await trackOwnBidAttempt(ownBids, item.id, amount, () =>
        placeBid.mutateAsync({
          auctionItemId: item.id,
          data: { amount },
          headers: { 'Idempotency-Key': requestId },
        }),
      )
      bidRequestIds.complete(requestId)

      // 서버가 접수한 뒤에만 성공으로 알린다 (루트 CLAUDE.md).
      setFeedback({
        tone: 'success',
        message: `${formatWon(amount)} 입찰이 등록됐어요.`,
      })
      toast.success('입찰이 등록됐어요', {
        description: `${item.name} · ${formatWon(amount)}`,
      })
      // MySQL 반영은 비동기다. 즉시 재조회해 먼저 온 SSE 상태를 되돌리지 않는다.
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
   * 라이브 경매방(`/rooms/$shareCode`)과 달리 이 라우트는 링크로 바로 들어오는
   * 단독 페이지라, 상세를 못 받으면 보여줄 게 없다. 실시간 연결을 지킬 이유도
   * 없으므로 전역 상태 화면을 그대로 쓴다.
   */
  if (detailQuery.isPending) return <RoutePending />
  if (detailQuery.isError) {
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
        softCloseTriggerSeconds={room.softCloseTriggerSeconds}
        softCloseSeconds={room.softCloseSeconds}
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
          void navigate({ to: '/rooms/$shareCode', params: { shareCode } })
        }
        onBid={submitBid}
        onConfirmingChange={setConfirming}
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
                to: '/rooms/$shareCode/items/$itemId',
                params: { shareCode, itemId: String(candidate.id) },
              })
            }
          />
        </div>
      }
      centerLabel={
        <>
          <span>
            선택한 물품 상세 · {closed ? '종료' : ready ? '시작 전' : '라이브'}
          </span>
          <Link
            to="/rooms/$shareCode"
            params={{ shareCode }}
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
            <GuestNotice redirectTo={`/rooms/${shareCode}/items/${itemId}`} />
          )}

          <ItemDetailPanel
            item={item}
            shareCode={shareCode}
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
            onConfirmingChange={setConfirming}
          />
        </>
      }
    />
  )
}
