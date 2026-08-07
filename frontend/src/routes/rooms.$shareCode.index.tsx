import { createFileRoute, useNavigate } from '@tanstack/react-router'
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Minus, Plus, Search, X } from 'lucide-react'

import {
  getGetDetail1QueryKey,
  getGetSummariesQueryKey,
  useGetDetail1,
  useAddAll,
  useGetSummaries,
  useRemove,
  useStart,
} from '@/api/generated/경매-물품/경매-물품'
import { getGetListQueryKey } from '@/api/generated/상품/상품'
import type { AuctionItemAddRequestDto } from '@/api/generated/model'
import {
  getGetRoomByShareCodeQueryKey,
  useClose,
  useGetResults,
  useGetRoomByShareCode,
} from '@/api/generated/경매방/경매방'
import { usePlace } from '@/api/generated/입찰/입찰'
import { mergeItemDetail, toAuctionItems } from '@/features/live/adapt-item'
import { toRoomResult } from '@/features/live/adapt-result'
import { toAuctionRoomDetail } from '@/features/live/adapt-room'
import { retryOnNetworkError } from '@/features/live/api-error'
import {
  AUCTION_START_FLASH_MS,
  type AuctionStartFlashState,
} from '@/features/live/components/auction-start-flash'
import { preloadLiveMotion } from '@/features/live/preload-motion'
import {
  SOFT_CLOSE_FLASH_MS,
  type SoftCloseFlash,
} from '@/features/live/soft-close-flash'
import { toBidErrorMessage } from '@/features/live/bid-error'
import {
  sellerActionMessageByCode,
  toSellerActionErrorMessage,
} from '@/features/live/seller-action-error'
import type { PickedItem } from '@/features/seller/components/item-picker-modal'

import { BidConfirmPanel } from '@/features/live/components/bid-confirm-panel'
import { ClosedRoomView } from '@/features/live/components/closed-room-view'
import { EventFeed } from '@/features/live/components/event-feed'
import { GuestNotice, LiveShell } from '@/features/live/components/live-shell'
import { ItemLeaderboard } from '@/features/live/components/leaderboard'
import { ItemDetailPanel } from '@/features/live/components/item-detail-panel'
import { LiveItemList } from '@/features/live/components/live-item-list'
import { useListFlip } from '@/features/live/use-list-flip'
import { ItemPickerModal } from '@/features/seller/components/item-picker-modal'
import { RoomSettingsModal } from '@/features/live/components/room-settings-modal'
import { MobileItemDetailView } from '@/features/live/components/mobile-item-detail-view'
import { MobileLiveView } from '@/features/live/components/mobile-live-view'
import { Button } from '@/components/ui/button'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { EmptyState } from '@/components/page-header'
import { GuestShell } from '@/components/layout/page-shell'
import { Modal } from '@/components/ui/modal'
import { QuickBidOverlay } from '@/features/live/components/quick-bid-overlay'
import { RouteError, RoutePending } from '@/components/route-states'
import { SharePanel } from '@/features/live/components/share-panel'
import { cn } from '@/lib/utils'
import { useCountdown } from '@/hooks/use-countdown'
import { formatClosingLead, formatWon } from '@/lib/format'
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
 * 라이브 경매방 (Figma `WEB-09 · 구매자 · 라이브`).
 *
 * 왼쪽 물품 목록, 가운데 실시간 이벤트 + 입찰 CTA, 오른쪽 열.
 * 오른쪽 열은 상황에 따라 리더보드 / 빠른 입찰 / 입찰 확인 / 공유로 바뀐다.
 */
export const Route = createFileRoute('/rooms/$shareCode/')({
  component: LiveRoomPage,
})

/** 오른쪽 열에 무엇을 띄울지 */
type RightPanel = 'leaderboard' | 'quickBid' | 'confirm' | 'share'

/**
 * 이벤트 피드 항목의 id 를 만든다. `Date.now()` 만 쓰면 같은 밀리초에 도착한 이벤트끼리
 * id 가 겹쳐 React key 가 충돌한다 — 경매방을 종료하면 물품 마감 이벤트가 한꺼번에 온다.
 */
let eventSeq = 0
function nextEventId(): number {
  eventSeq += 1
  return Date.now() * 1000 + (eventSeq % 1000)
}

const PANEL_LABEL: Record<RightPanel, string> = {
  leaderboard: '리더보드 · 물품별',
  quickBid: '빠른 입찰',
  confirm: '입찰 확인',
  share: '경매방 공유',
}

function LiveRoomPage() {
  const { shareCode } = Route.useParams()
  const navigate = useNavigate()
  const user = useCurrentUser()
  const isDesktop = useIsDesktop()

  const isGuest = user === null
  /** 리더보드에서 내 줄을 찾는 기준. 서버가 `isMe` 를 안 줘서 닉네임으로 맞춘다. */
  const myNickname = user?.nickname ?? null

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
  /**
   * 서버가 세는 실시간 참여자 수. 아직 못 받았으면 `null`.
   *
   * 방 상세 응답의 `participantCount` 는 아직 채워지지 않아서 SSE 가 유일한
   * 출처다. 연결이 끊기면 마지막 값이 남고, 다시 구독하면 서버가 곧 새로 보낸다.
   */
  const [participantCount, setParticipantCount] = useState<number | null>(null)

  const roomQuery = useGetRoomByShareCode(shareCode)

  /*
   * 로그인 유저가 약관 동의 없이 직접 접근하면 입장 페이지로 보낸다.
   *
   * 방 주인은 제외한다 — 판매자는 참여자가 아니라 동의 기록이 없고, 서버가 `false` 를
   * 주더라도 자기 방에서 튕겨나가면 안 된다.
   */
  useEffect(() => {
    const dto = roomQuery.data?.data
    if (!isGuest && !dto?.isOwner && dto?.agreedToTerms === false) {
      void navigate({ to: '/join/$shareCode', params: { shareCode } })
    }
  }, [isGuest, roomQuery.data, shareCode, navigate])

  const queryClient = useQueryClient()
  const summaries = useGetSummaries(shareCode)
  const serverItems = useMemo(
    () => toAuctionItems(summaries.data?.data ?? [], myNickname),
    [summaries.data, myNickname],
  )

  /*
   * 물품은 서버 값만 쓴다.
   *
   * 예전에는 서버가 빈 목록을 주면 목업 물품으로 갈아탔다. 화면은 채워지지만
   * 리더보드·현재가가 실제 입찰과 무관한 가짜였고, 화면상 구분이 되지 않아
   * 백엔드가 값을 안 주고 있다는 사실 자체가 가려졌다. 비어 보이는 게 낫다.
   */
  // 편성을 바꾸기 전까지는 서버가 준 목록을 그대로 쓴다.
  const roomItems = items ?? serverItems

  /*
   * 방 정보는 서버가 준다. 목록·상세와 달리 목업으로 되돌아가지 않는다 —
   * 판매자 조작을 띄울 근거(`isOwner`)가 여기서 나오므로 목업으로 대신할 수 없다.
   */
  const room = useMemo(
    () => toAuctionRoomDetail(roomQuery.data?.data ?? {}, roomItems),
    [roomQuery.data, roomItems],
  )
  const roomClosed = room.status === 'CLOSED'

  /*
   * 판매자 조작(방 종료·물품 추가/제외·설정 수정)은 여전히 숫자 ID 를 받는다. 인증과 소유
   * 검증이 있어 열거 위험이 없고, 이번 식별자 교체는 익명으로 열리는 공개 경로만 대상이라서다.
   * 그래서 이 화면은 URL 의 shareCode 와 응답의 숫자 ID 를 함께 들고 있는다.
   * 방을 아직 못 읽었으면 0 이지만, 판매자 조작 UI 자체가 방을 읽은 뒤에야 나타난다.
   */
  const auctionRoomId = room.id

  // 진행 중인 방에서는 결과를 볼 일이 없다. 방이 닫혔을 때만 요청한다.
  const resultsQuery = useGetResults(shareCode, {
    query: { enabled: roomClosed },
  })

  /**
   * SSE 이벤트 수신 핸들러.
   *
   * 이벤트 피드(extraEvents)와 물품 상태(items)를 동시에 갱신한다.
   *
   * **물품 갱신은 반드시 함수형(`setItems((prev) => …)`)이어야 한다.** 경매방 종료처럼
   * 이벤트가 한꺼번에 오면 렌더가 끼어들 틈이 없어서, 두 번째 핸들러가 첫 번째의 결과를
   * 못 보고 낡은 목록 위에 덮어쓴다. 실제로 물품 2개가 동시에 마감됐을 때 나중 것만
   * 닫히고 앞의 낙찰 물품이 진행 중으로 남는 버그가 있었다.
   */
  const handleSseEvent = useCallback(
    (payload: SseEventPayload) => {
      // 같은 밀리초에 두 이벤트가 오면 id 가 겹쳐 피드의 React key 가 충돌한다.
      const eventId = nextEventId()

      switch (payload.kind) {
        case 'ItemStarted':
          setExtraEvents((prev) => [
            ...prev,
            {
              id: eventId,
              at: new Date().toISOString(),
              kind: 'START',
              itemId: payload.itemId,
              itemName: payload.itemName,
              message: '경매가 시작됐어요',
            },
          ])
          setItems((prev) =>
            (prev ?? roomItems).map((item) =>
              item.id === payload.itemId
                ? {
                    ...item,
                    status: 'ACTIVE' as const,
                    endsAt: payload.endedTime,
                  }
                : item,
            ),
          )
          /*
           * 시작 알림. 뒤이어 또 시작되면 마지막 것만 남는다. 겹쳐 띄우면
           * 화면 가운데에서 그림이 서로 가린다.
           */
          setJustStarted({
            itemId: payload.itemId,
            startedAt: payload.endedTime,
          })
          /*
           * 첫 물품이 시작되면 방도 BEFORE → OPEN 이 된다. 이 창이 시작을 요청한
           * 당사자가 아니어도 상태 표시(LIVE 배지)와 설정 잠금이 따라와야 한다.
           */
          void queryClient.invalidateQueries({
            queryKey: getGetRoomByShareCodeQueryKey(shareCode),
          })
          break

        case 'ItemClosingSoon':
          setExtraEvents((prev) => [
            ...prev,
            {
              id: eventId,
              at: new Date().toISOString(),
              kind: 'CLOSE',
              itemId: payload.itemId,
              itemName: payload.itemName,
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
              itemId: payload.itemId,
              itemName: payload.itemName,
              message: `${payload.bidderNickname}님이 ${formatWon(payload.bidPrice)} 입찰`,
              emphasized: true,
            },
          ])
          setItems((prev) =>
            (prev ?? roomItems).map((item) =>
              item.id === payload.itemId
                ? {
                    ...item,
                    currentPrice: payload.bidPrice,
                    topBidderNickname: payload.bidderNickname,
                    bidCount: item.bidCount + 1,
                    leaderboard: [
                      {
                        rank: 1,
                        nickname: payload.bidderNickname,
                        amount: payload.bidPrice,
                        isMe: payload.bidderNickname === myNickname,
                      },
                      ...item.leaderboard.filter(
                        (entry) => entry.nickname !== payload.bidderNickname,
                      ),
                    ]
                      // 서버 리더보드도 상위 3명이다. 더 들고 있으면 서버 값이
                      // 다시 올 때 줄 수가 줄어들어 화면이 들썩인다.
                      .slice(0, 3)
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
              itemId: payload.itemId,
              itemName: payload.itemName,
              message: `마감 직전 입찰 발생 · 마감 +${payload.extendSeconds <= 60 ? `${payload.extendSeconds}초` : `${Math.floor(payload.extendSeconds / 60)}분`} 자동 연장`,
              emphasized: true,
            },
          ])
          // 서버가 준 마감 시각을 그대로 쓴다. 직접 더하면 이벤트가 두 번 오거나
          // 유실됐을 때 화면 카운트다운만 서버와 어긋난 채로 남는다.
          setItems((prev) =>
            (prev ?? roomItems).map((item) =>
              item.id === payload.itemId
                ? { ...item, endsAt: payload.endedTime }
                : item,
            ),
          )
          /*
           * 연장 연출. 같은 물품이 또 연장되면 count 가 올라가 APNG 가 처음부터
           * 다시 재생된다(`replayKey`).
           */
          setJustExtended((prev) => ({
            itemId: payload.itemId,
            seconds: payload.extendSeconds,
            count: prev?.itemId === payload.itemId ? prev.count + 1 : 0,
          }))
          break

        case 'ItemEnded':
          setExtraEvents((prev) => [
            ...prev,
            {
              id: eventId,
              at: new Date().toISOString(),
              // 낙찰가·낙찰자를 보조 줄로 빼지 않고 입찰 문구와 같은 결로 한 줄에 담는다.
              kind: payload.winnerNickname ? 'WIN' : 'CLOSE',
              itemId: payload.itemId,
              itemName: payload.itemName,
              message:
                payload.winnerNickname && payload.finalPrice !== null
                  ? `${payload.winnerNickname}님이 ${formatWon(payload.finalPrice)}에 낙찰`
                  : '경매 종료 · 낙찰자 없음',
              emphasized: true,
            },
          ])
          setItems((prev) =>
            (prev ?? roomItems).map((item) =>
              item.id === payload.itemId
                ? {
                    ...item,
                    status: 'CLOSED' as const,
                    // 낙찰자가 실렸으면 낙찰, 비었으면 유찰이다.
                    sold: payload.winnerNickname !== null,
                  }
                : item,
            ),
          )
          // "경매 종료" 도장. 서버가 마감을 확정했을 때만 띄운다.
          setJustClosedId(payload.itemId)
          // 유찰이면 이 시점부터 재등록 가능 상품이 된다. 목록을 다시 읽지 않으면
          // 재등록 모달의 캐시(최대 1분)가 그대로 남아 방금 유찰된 물품이 안 보인다.
          if (payload.winnerNickname === null) {
            void queryClient.invalidateQueries({
              queryKey: getGetListQueryKey(),
            })
          }
          break

        case 'RoomClosed':
          setExtraEvents((prev) => [
            ...prev,
            {
              id: eventId,
              at: new Date().toISOString(),
              kind: 'CLOSE',
              message: '판매자가 경매방을 종료했어요',
              emphasized: true,
            },
          ])
          /*
           * 방 상태를 화면에서 직접 CLOSED 로 바꾸지 않고 다시 읽어온다.
           * 종료 화면은 closedAt·낙찰 결과까지 그리는데 이 이벤트에는 그 값이 없다.
           */
          setItems(null)
          void queryClient.invalidateQueries({
            queryKey: getGetRoomByShareCodeQueryKey(shareCode),
          })
          void queryClient.invalidateQueries({
            queryKey: getGetSummariesQueryKey(shareCode),
          })
          break

        /*
         * 참여자 수는 헤더 숫자만 조용히 바꾼다.
         *
         * 이벤트 피드에 쌓으면 사람이 들락날락할 때마다 "N명 참여" 가
         * 도배되어 입찰·마감 같은 실제 사건이 묻힌다.
         */
        case 'ParticipantCount':
          setParticipantCount(payload.participantCount)
          break

        /*
         * 판매자가 물품을 넣거나 뺐다. 목록만 다시 읽고 이벤트 피드에는 쌓지 않는다
         * — 편성 변경은 입찰·마감 같은 경매 진행 사건이 아니고, 벌크로 20개를 넣으면
         * 피드가 그것만으로 가득 찬다.
         *
         * 판매자 본인 화면도 `handleAdd`·`handleRemove` 에서 같은 방식으로 목록을
         * 다시 읽는다. 양쪽이 같은 경로라 이벤트와 응답이 겹쳐도 물품이 두 번
         * 들어가지 않는다 — `setItems(null)` 이 화면 편성분을 버리고 서버 목록으로
         * 되돌리기 때문이다.
         */
        case 'ItemAdded':
        case 'ItemRemoved':
          setItems(null)
          void queryClient.invalidateQueries({
            queryKey: getGetSummariesQueryKey(shareCode),
          })
          break

        /*
         * 판매자가 방 설정을 바꿨다. 제목·소개는 방 정보에서 나오므로 그것만 다시
         * 읽는다. 이게 없으면 이미 들어와 있던 사람은 새로고침 전까지 옛 이름을 본다.
         */
        case 'RoomUpdated':
          void queryClient.invalidateQueries({
            queryKey: getGetRoomByShareCodeQueryKey(shareCode),
          })
          break
      }
    },
    [roomItems, myNickname, shareCode, queryClient],
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
  ) : // 편성을 바꿔서 보여줄 물품이 있으면 에러를 띄우지 않는다.
  summaries.isError && roomItems.length === 0 ? (
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
  ) : roomItems.length === 0 ? (
    /*
     * 서버가 빈 목록을 준 경우. 목업으로 채우지 않으므로 이 자리가 실제로
     * 보인다. 열이 통째로 비면 로딩이 멈춘 것처럼 보여서 한 줄이라도 남긴다.
     */
    <div className="mt-2.5 rounded-2xl border bg-card px-4 py-8 text-center">
      <p className="text-[13px] font-medium text-neutral-muted">
        아직 등록된 물품이 없어요.
      </p>
    </div>
  ) : null

  /*
   * 실시간으로 받은 이벤트만 보여준다. 방에 들어온 뒤 실제로 일어난 일만 쌓이므로
   * 처음에는 비어 있고, 지난 이벤트를 돌려받는 API 가 생기기 전까지는 그대로다.
   */
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
  /** 방금 소프트클로즈로 연장돼서 연출이 떠 있는 물품 */
  const [justExtended, setJustExtended] = useState<SoftCloseFlash | null>(null)
  /** 방금 시작돼서 화면 가운데에 알림이 떠 있는 물품 */
  const [justStarted, setJustStarted] = useState<AuctionStartFlashState | null>(
    null,
  )
  /** 시작 요청을 서버가 처리 중인 물품. 그 카드의 조작 줄만 잠근다. */
  const [startingItemId, setStartingItemId] = useState<number | null>(null)
  /** 판매자가 방 전체를 끝낼 때 한 번 더 확인받는다. */
  const [closingRoom, setClosingRoom] = useState(false)
  /** 판매자가 방 설정을 고치는 중. 라우트를 옮기지 않아 실시간 연결이 유지된다. */
  const [editingSettings, setEditingSettings] = useState(false)
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
  const detailQuery = useGetDetail1(shareCode, detailItemId ?? 0, {
    query: { enabled: detailItemId !== null },
  })
  const detailItem = useMemo(() => {
    if (!listItem) return null
    const dto = detailQuery.data?.data
    // 물품을 갈아탄 직후에는 이전 물품의 상세가 남아 있다. 그때는 목록 값을 쓴다.
    if (!dto || dto.auctionItemId !== listItem.id) return listItem
    /*
     * 상세에만 있는 필드만 얹는다. 현재가·리더보드는 `listItem` 이 원본이다 —
     * SSE 가 갱신하는 쪽이고, 상세 응답은 남의 입찰로 다시 불리지 않는다.
     */
    return mergeItemDetail(dto, listItem)
  }, [listItem, detailQuery.data])

  const detailMinimum = detailItem
    ? detailItem.currentPrice + detailItem.bidUnit
    : 0
  // 상세가 닫혀 있거나 시작 전 물품이면 null 이라 0 초다(훅은 늘 같은 순서로).
  const detailRemaining = useCountdown(detailItem?.endsAt ?? null)

  // 상세를 열 때마다 최소 입찰가로 맞춘다.
  useEffect(() => {
    if (!detailItem) return
    setDetailAmount(detailItem.currentPrice + detailItem.bidUnit)
    setDetailFeedback(null)
    // 물품이 바뀔 때만 초기화한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [detailItemId])

  /*
   * 남이 입찰해서 최소가가 올라가면 입력 금액도 끌어올린다.
   *
   * 이게 없으면 버튼에 낡은 금액이 남고 `amount < minimum` 이라 입찰이 잠긴다.
   * 최소가 이상을 직접 적어둔 경우는 건드리지 않는다 — 올려 적은 금액을
   * 남의 입찰 때문에 되돌리면 안 된다.
   */
  useEffect(() => {
    setDetailAmount((prev) => (prev < detailMinimum ? detailMinimum : prev))
  }, [detailMinimum])

  const placeBid = usePlace()
  const startItem = useStart()
  const addItems = useAddAll()
  const removeItem = useRemove()
  const closeRoom = useClose()

  /**
   * 서버가 조작을 접수한 뒤 물품 값을 다시 읽어온다.
   *
   * 화면에서 덮어쓴 편성(`items`)을 비워야 새로 받은 값이 보인다.
   * `auctionItemId` 를 주면 그 물품의 상세까지 같이 무효화한다.
   */
  const refreshItems = (auctionItemId?: number) => {
    setItems(null)
    void queryClient.invalidateQueries({
      queryKey: getGetSummariesQueryKey(shareCode),
    })
    if (auctionItemId !== undefined) {
      void queryClient.invalidateQueries({
        queryKey: getGetDetail1QueryKey(shareCode, auctionItemId),
      })
    }
  }

  /**
   * 추가 모달이 쓰는 등록 가능(UNREGISTERED) 상품 목록을 다시 읽어온다.
   *
   * 물품을 넣고 빼면 이 목록이 달라지는데, `staleTime` 이 1분이라 무효화하지
   * 않으면 그동안 모달을 다시 열었을 때 방금 넣은 상품이 그대로 남아 보인다.
   * 인자 없는 쿼리 키는 검색어·상태가 다른 캐시까지 함께 잡는 접두사다.
   */
  const refreshPickableProducts = () => {
    void queryClient.invalidateQueries({ queryKey: getGetListQueryKey() })
  }

  /**
   * 판매자가 물품 경매를 시작한다.
   *
   * 서버가 마감 시각을 확정해 돌려주므로, 응답을 받은 뒤 목록을 다시 읽어
   * 그 값으로 카운트다운이 흐르게 한다. 화면에서 미리 진행 중으로 바꾸지 않는다.
   */
  const handleStart = async (item: AuctionItemDetail, minutes: number) => {
    setStartingItemId(item.id)

    try {
      await startItem.mutateAsync({
        auctionItemId: item.id,
        data: { durationMinutes: minutes },
      })

      toast.success('경매를 시작했어요', {
        description: `${item.name} · ${minutes}분 진행`,
      })
      refreshItems(item.id)
      /*
       * 첫 물품이 시작되면 서버가 방을 BEFORE → OPEN 으로 바꾼다. 방 정보를 다시
       * 읽지 않으면 화면의 `status` 가 BEFORE 로 남아, 설정 수정 모달이 이미 잠겨야
       * 할 필드를 계속 열어 둔다.
       */
      void queryClient.invalidateQueries({
        queryKey: getGetRoomByShareCodeQueryKey(shareCode),
      })
    } catch (error) {
      const { title, description } = toSellerActionErrorMessage(error, 'start')
      toast.error(title, { description })
    } finally {
      setStartingItemId(null)
    }
  }

  /**
   * 판매자가 방송을 끝내고 경매방을 종료한다. 되돌릴 수 없다.
   *
   * 서버가 진행 중이던 물품까지 함께 마감하므로 **방과 물품을 모두 다시 읽어온다.**
   * 방 상태가 `CLOSED` 로 오면 화면이 종료 화면(`ClosedRoomView`)으로 바뀐다.
   *
   * 서버가 받아주기 전에는 화면을 종료로 그리지 않는다 — 실패하면 방송이 계속되는데
   * 화면만 끝난 것으로 보인다.
   */
  const handleCloseRoom = async () => {
    try {
      await closeRoom.mutateAsync({ roomId: auctionRoomId })

      setClosingRoom(false)
      toast.success('경매방을 종료했어요')
      refreshItems()
      void queryClient.invalidateQueries({
        queryKey: getGetRoomByShareCodeQueryKey(shareCode),
      })
    } catch (error) {
      const { title, description } = toSellerActionErrorMessage(
        error,
        'closeRoom',
      )
      toast.error(title, { description })
      setClosingRoom(false)
    }
  }

  /**
   * 판매자가 고른 상품을 경매방에 물품으로 추가한다.
   *
   * 벌크 응답은 **부분 성공**이다 — 거절된 상품이 있어도 나머지는 들어간다.
   * 거절된 상품은 한 번 더 보내보고, 그래도 막히면 이름과 사유로 알린다.
   * 서버 판정은 저장 전에 끝나므로 대개 두 번째도 같은 결과지만, 그 사이에
   * 다른 화면에서 그 상품을 빼냈다면 이번에는 통과한다.
   */
  const handleAdd = async (picked: PickedItem[]) => {
    const requested: AuctionItemAddRequestDto[] = picked.map((item) => ({
      productId: item.productId,
      startingPrice: item.startingPrice,
    }))

    const send = (items: AuctionItemAddRequestDto[]) =>
      retryOnNetworkError(() =>
        addItems.mutateAsync({ auctionRoomId, data: { items } }),
      )

    try {
      const first = await send(requested)
      let added = first.data?.added?.length ?? 0
      let failed = first.data?.failed ?? []

      const rejected = new Set(failed.map((failure) => failure.productId))
      const retryTargets = requested.filter((item) =>
        rejected.has(item.productId),
      )

      // 되돌려받은 productId 를 하나도 못 알아보면 재전송하지 않는다.
      // 빈 요청을 보내면 실패가 없는 응답이 와서 방금 받은 거절이 지워진다.
      if (retryTargets.length > 0) {
        const retried = await send(retryTargets)
        added += retried.data?.added?.length ?? 0
        failed = retried.data?.failed ?? []
      }

      // 서버가 접수한 뒤에만 목록을 다시 읽는다 (루트 CLAUDE.md).
      refreshItems()
      refreshPickableProducts()

      if (failed.length === 0) {
        toast.success(`물품 ${added}개를 추가했어요`)
        return
      }

      // 어느 상품이 왜 막혔는지 이름으로 말해준다. productId 만으로는 알 수 없다.
      const nameOf = new Map(picked.map((item) => [item.productId, item.name]))
      const reasons = failed.map((failure) => {
        const name = nameOf.get(failure.productId ?? -1) ?? '상품'
        const reason =
          sellerActionMessageByCode(failure.code)?.title ??
          '추가할 수 없는 상품이에요'
        return `${name} · ${reason}`
      })

      toast.error(
        added > 0
          ? `${failed.length}개는 추가하지 못했어요`
          : '물품을 추가하지 못했어요',
        { description: reasons.join(', ') },
      )
    } catch (error) {
      const { title, description } = toSellerActionErrorMessage(error, 'add')
      toast.error(title, { description })
    }
  }

  /**
   * 판매자가 고른 물품을 경매방에서 뺀다.
   *
   * 물품마다 요청이 하나씩 나간다. 서로 독립적이라 동시에 보내고,
   * 일부만 실패해도 나머지 제외는 그대로 살린다.
   */
  const handleRemove = async (targets: AuctionItemDetail[]) => {
    const results = await Promise.allSettled(
      targets.map((target) =>
        retryOnNetworkError(() =>
          removeItem.mutateAsync({
            auctionRoomId,
            auctionItemId: target.id,
          }),
        ),
      ),
    )

    refreshItems()
    refreshPickableProducts()

    const rejected = results.flatMap((result, index) =>
      result.status === 'rejected'
        ? [{ item: targets[index], reason: result.reason as unknown }]
        : [],
    )

    if (rejected.length === 0) {
      toast.success(`물품 ${targets.length}개를 뺐어요`)
      return
    }

    const reasons = rejected.map(({ item, reason }) => {
      const { title } = toSellerActionErrorMessage(reason, 'remove')
      return `${item.name} · ${title}`
    })

    toast.error(
      rejected.length < targets.length
        ? `${rejected.length}개는 빼지 못했어요`
        : '물품을 빼지 못했어요',
      { description: reasons.join(', ') },
    )
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
        motion: 'bidAccepted',
      })
      refreshItems(detailItem.id)
    } catch (error) {
      const { title, description } = toBidErrorMessage(error)
      setDetailFeedback({ tone: 'error', message: `${title}. ${description}` })
      toast.error(title, { description })
    } finally {
      setDetailPending(false)
    }
  }

  /*
   * 도장은 한 번만 보여주고 지운다.
   *
   * 마감 APNG 가 2.83초짜리라 그보다 짧게 잡으면 마지막 타격과 낙찰 도장이
   * 나오기 전에 사라진다. 마감 판정 자체는 서버 이벤트가 하고, 이 값은
   * 연출을 얼마나 띄워둘지만 정한다.
   */
  useEffect(() => {
    if (justClosedId === null) return
    const timer = window.setTimeout(() => setJustClosedId(null), 3050)
    return () => window.clearTimeout(timer)
  }, [justClosedId])

  /*
   * 마감·연장·입찰 연출 이미지를 미리 받아둔다. 마감은 예고 없이 일어나서
   * 그때 받기 시작하면 늦는다.
   */
  useEffect(() => {
    preloadLiveMotion()
  }, [])

  // 시작 알림도 한 번만 보여주고 지운다.
  useEffect(() => {
    if (justStarted === null) return
    const timer = window.setTimeout(
      () => setJustStarted(null),
      AUCTION_START_FLASH_MS,
    )
    return () => window.clearTimeout(timer)
  }, [justStarted])

  // 연장 연출도 한 번만 보여주고 지운다.
  useEffect(() => {
    if (justExtended === null) return
    const timer = window.setTimeout(
      () => setJustExtended(null),
      SOFT_CLOSE_FLASH_MS,
    )
    return () => window.clearTimeout(timer)
  }, [justExtended])

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
      makeDemoEvent(prev.length, target?.name, target?.id),
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
   * 목록에서 물품을 눌렀을 때. 평소에는 상세를 열고, 빼기 모드에서는 고르기만 한다.
   *
   * 데스크톱과 모바일이 같은 규칙을 써야 해서 여기서 한 번만 정의해 양쪽에 넘긴다.
   */
  const handleSelectItem = (item: AuctionItemDetail) => {
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
  }

  /**
   * 판매자 편성 조작 묶음. 방 주인이 아니면 `undefined` 라 화면이 아예 안 그린다.
   *
   * 개별 prop 으로 늘어놓으면 열 개가 넘어가서 `devTools` 처럼 하나로 묶었다.
   */
  const sellerControls = isOwner
    ? {
        removeMode,
        selectedCount: selectedForRemoval.length,
        addDisabled: addItems.isPending,
        removeDisabled: !removeMode && removable.length === 0,
        removeTitle:
          removable.length === 0 ? '시작 전 물품만 뺄 수 있어요' : undefined,
        onPick: () => setPicking(true),
        onToggleRemoveMode: () => {
          setRemoveMode((prev) => !prev)
          setSelectedForRemoval([])
        },
        onCancelRemove: () => {
          setRemoveMode(false)
          setSelectedForRemoval([])
        },
        onConfirmRemove: () => setConfirmingRemoval(true),
      }
    : undefined

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
        motion: 'bidAccepted',
      })
      refreshItems(pendingBid.item.id)
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

  /*
   * 방 정보를 못 받으면 화면을 그릴 수 없다 — 제목도, 판매자 조작을 띄울
   * 근거(`isOwner`)도 여기서 나온다. 없는 공유 코드도 404 로 여기에 걸린다.
   */
  if (roomQuery.isError) {
    return (
      <RouteError
        error={roomQuery.error}
        reset={() => void roomQuery.refetch()}
      />
    )
  }

  if (roomQuery.isPending) return <RoutePending />

  if (roomClosed) {
    if (resultsQuery.isPending) return <RoutePending />
    if (resultsQuery.isError) {
      return (
        <RouteError
          error={resultsQuery.error}
          reset={() => void resultsQuery.refetch()}
        />
      )
    }

    const result = toRoomResult(resultsQuery.data?.data)
    if (!result) {
      return (
        <GuestShell title="종료된 경매방" back>
          <EmptyState
            title="결과를 찾을 수 없어요"
            description="삭제되었거나 존재하지 않는 경매방입니다."
          />
        </GuestShell>
      )
    }

    return (
      <ClosedRoomView result={result} isGuest={isGuest} isOwner={isOwner} />
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
  /*
   * 진행 중인 물품은 마감 시각이 남아 있어도 함께 닫힌다(`AuctionRoomCloseService`).
   * 몇 개가 딸려 닫히는지 세어 보여준다 — 그걸 모르고 누르면 아직 입찰을 받고 있던
   * 물품이 그대로 마감되고, 되돌릴 방법이 없다.
   */
  const closeRoomDialog = (
    <ConfirmDialog
      open={closingRoom}
      tone="danger"
      title="경매방을 종료할까요?"
      description={
        liveItems.length > 0
          ? `진행 중인 물품 ${liveItems.length}개가 마감 시각과 상관없이 지금 마감되고 낙찰 결과가 확정됩니다. 되돌릴 수 없어요.`
          : '경매방이 종료되고 참여자는 더 이상 입장할 수 없어요. 되돌릴 수 없어요.'
      }
      confirmLabel="경매방 종료"
      onCancel={() => setClosingRoom(false)}
      pending={closeRoom.isPending}
      onConfirm={() => void handleCloseRoom()}
    />
  )

  /*
   * 방 설정 수정. 종료 확인과 같은 이유로 한 번 만들어 양쪽 분기에서 쓴다.
   *
   * 어댑터(`toAuctionRoomDetail`)가 `liveUrl`·`softCloseTriggerSeconds` 를
   * 떨구기 때문에 **가공한 `room` 이 아니라 서버 응답을 그대로 넘긴다.**
   */
  const roomDto = roomQuery.data?.data
  const settingsModal = roomDto ? (
    <RoomSettingsModal
      open={editingSettings}
      onClose={() => setEditingSettings(false)}
      room={roomDto}
    />
  ) : null

  /** 판매자에게만 설정 버튼을 띄운다. 실제 권한은 PATCH 가 다시 본다. */
  const openSettings = isOwner ? () => setEditingSettings(true) : undefined

  if (!isDesktop) {
    return (
      <>
        <MobileLiveView
          room={room}
          isGuest={isGuest}
          participantCount={participantCount}
          events={roomEvents}
          items={roomItems}
          itemsPlaceholder={itemsPlaceholder}
          liveItems={liveItems}
          rankedItems={rankedItems}
          onShare={() => setPanel('share')}
          onOpenSettings={openSettings}
          onCloseRoom={isOwner ? () => setClosingRoom(true) : undefined}
          onBack={() => void navigate({ to: '/rooms' })}
          onOpenItem={openItem}
          onSelectItem={handleSelectItem}
          isSelected={(item) =>
            removeMode && selectedForRemoval.includes(item.id)
          }
          isDimmed={(item) => removeMode && item.status !== 'READY'}
          seller={sellerControls}
          onStart={isOwner ? handleStart : undefined}
          isOwner={isOwner}
          justClosedId={justClosedId}
          justExtended={justExtended}
          justStarted={justStarted}
          startingItemId={startingItemId}
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
              softCloseTriggerSeconds={room.softCloseTriggerSeconds}
              softCloseSeconds={room.softCloseSeconds}
              events={roomEvents.filter(
                (event) => event.itemId === detailItem.id,
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
              shareCode={shareCode}
              roomTitle={room.title}
              onClose={() => setPanel('leaderboard')}
            />
          )}
        </Modal>

        {closeRoomDialog}
        {settingsModal}

        {/*
         * 데스크톱과 같은 모달을 쓴다. 트리를 갈라 그리므로 한 번에 한 벌만
         * 살아 있고, `<dialog>` 가 두 벌 겹쳐 문서를 죽이는 일은 없다.
         */}
        <ItemManagement
          picking={picking}
          onPickingChange={setPicking}
          onAdd={handleAdd}
          removing={
            confirmingRemoval
              ? roomItems.filter((item) => selectedForRemoval.includes(item.id))
              : []
          }
          removePending={removeItem.isPending}
          onRemovingCancel={() => setConfirmingRemoval(false)}
          onRemove={async (targets) => {
            await handleRemove(targets)
            setConfirmingRemoval(false)
            setRemoveMode(false)
            setSelectedForRemoval([])
          }}
        />
      </>
    )
  }

  return (
    <>
      <LiveShell
        room={room}
        isGuest={isGuest}
        participantCount={participantCount}
        onShare={() => setPanel(panel === 'share' ? 'leaderboard' : 'share')}
        onOpenSettings={openSettings}
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
                  shareCode={shareCode}
                  itemId={String(detailItem.id)}
                  events={roomEvents.filter(
                    (event) => event.itemId === detailItem.id,
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
                  // 어떤 상품을 고를 수 있는지는 모달이 직접 조회한다.
                  // 여기서는 앞선 추가가 아직 처리 중일 때만 잠근다.
                  disabled={addItems.isPending}
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
                  justExtended={justExtended}
                  justStarted={justStarted}
                  startingItemId={startingItemId}
                  onStart={isOwner ? handleStart : undefined}
                  onSelect={handleSelectItem}
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
            {isGuest && <GuestNotice redirectTo={`/rooms/${shareCode}`} />}

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
                      justExtended={
                        justExtended?.itemId === item.id ? justExtended : null
                      }
                      justStarted={
                        justStarted?.itemId === item.id ? justStarted : null
                      }
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
                shareCode={shareCode}
                roomTitle={room.title}
                onClose={() => setPanel('leaderboard')}
              />
            </RightSlot>
          </div>
        }
      />

      {closeRoomDialog}
      {settingsModal}

      <ItemManagement
        picking={picking}
        onPickingChange={setPicking}
        onAdd={handleAdd}
        removing={
          confirmingRemoval
            ? roomItems.filter((item) => selectedForRemoval.includes(item.id))
            : []
        }
        removePending={removeItem.isPending}
        onRemovingCancel={() => setConfirmingRemoval(false)}
        onRemove={async (targets) => {
          await handleRemove(targets)
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
  onAdd,
  removing,
  removePending,
  onRemovingCancel,
  onRemove,
}: {
  picking: boolean
  onPickingChange: (open: boolean) => void
  onAdd: (picked: PickedItem[]) => void
  /** 확인을 기다리는 물품들. 비어 있으면 다이얼로그를 닫는다. */
  removing: AuctionItemDetail[]
  removePending: boolean
  onRemovingCancel: () => void
  onRemove: (items: AuctionItemDetail[]) => void
}) {
  return (
    <>
      <ItemPickerModal
        open={picking}
        onClose={() => onPickingChange(false)}
        // 모달은 고른 목록만 돌려준다. 서버 호출과 결과 알림은 라우트가 맡는다.
        onConfirm={onAdd}
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
        pending={removePending}
        onCancel={onRemovingCancel}
        onConfirm={() => {
          if (removing.length === 0) return
          onRemove(removing)
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
function makeDemoEvent(
  index: number,
  itemName?: string,
  itemId?: number,
): RoomEvent {
  const amount = 86000 + index * 1000
  return {
    id: 10_000 + index,
    at: new Date().toISOString(),
    kind: 'BID',
    // 물품 상세의 로그도 id 로 골라내므로, 데모 이벤트에도 붙여야 거기 보인다.
    itemId,
    itemName,
    message: `데모입찰러님이 ${amount.toLocaleString('ko-KR')}원 입찰`,
    emphasized: true,
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
