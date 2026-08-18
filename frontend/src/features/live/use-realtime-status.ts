import { useCallback, useEffect, useRef, useState } from 'react'

import { eventIdFromLastEventId } from '@/features/live/sse-event-id'
import { toast } from '@/lib/toast'

/**
 * SSE 는 axios 를 타지 않아 `custom-instance.ts` 의 baseURL 이 적용되지 않는다.
 * 그래서 여기서 직접 붙인다.
 *
 * 상대경로로 두면 **운영에서만** 깨진다. 화면은 CloudFront(upbid.store)에서
 * 뜨는데 API 는 다른 호스트(api.upbid.store)라, `/api/...` 요청이 S3 로 가서
 * SPA fallback 인 index.html(text/html)을 받고 EventSource 가 바로 에러를 낸다.
 * 로컬은 vite dev proxy 가 동일 출처로 만들어줘서 이 문제가 드러나지 않는다.
 *
 * 로컬에는 이 환경변수가 없어(`.env` 에 카카오 키만 있다) 빈 문자열이 되고,
 * 그러면 지금까지처럼 상대경로 + dev proxy 로 동작한다.
 */
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

/**
 * 브라우저가 재접속을 포기했을 때(`readyState === CLOSED`) 훅이 대신 다시 여는 횟수.
 *
 * `EventSource` 로는 HTTP 상태를 읽을 수 없어서, 서버 재배포처럼 곧 돌아오는 실패와
 * 없는 방(404)·종료된 방(409)처럼 다시 열어도 소용없는 실패를 구분할 방법이 없다.
 * 그래서 무한히 붙지 않고 횟수를 막아 둔다. 다 소진하면 `failed` 로 두고 멈춘다.
 */
const MAX_RETRIES = 5

/** 재시도 간격. 시도마다 두 배로 늘어 1s → 2s → 4s → 8s → 16s 다. */
const RETRY_BASE_MS = 1000

/**
 * `closed` 는 **정상 종료**다. 방이 끝나서 더 받을 이벤트가 없는 상태이므로
 * `reconnecting`·`failed` 와 달리 "연결이 끊겼다"는 경고를 띄우지 않는다.
 */
export type RealtimeStatus =
  'connecting' | 'connected' | 'reconnecting' | 'failed' | 'closed'

type SseEventData =
  | {
      kind: 'ItemStarted'
      itemId: number
      itemName: string
      endedTime: string
      revision: number
    }
  /**
   * 마감이 임박했다. **이 이벤트가 오는 순간부터가 Soft Close 연장 구간**이다.
   *
   * `remainingSeconds` 는 경매방의 "마감 임박 기준"(Soft Close 트리거)이라 방마다
   * 다르다. 그래서 문구를 `마감 1분 전` 으로 박아 둘 수 없고 이 값으로 만들어야 한다.
   * 실측한 잔여 시간이 아니라 설정값이므로, 서버 스케줄러가 조금 늦게 깨도
   * `59초 전` 같은 문구가 나오지 않는다.
   *
   * 마감이 연장되면 이 이벤트도 새 마감 시각 기준으로 다시 온다. 다만 연장 폭이
   * 트리거보다 작아 연장 구간을 벗어나지 못하면 다시 오지 않는다.
   */
  | {
      kind: 'ItemClosingSoon'
      itemId: number
      itemName: string
      remainingSeconds: number
    }
  | {
      kind: 'BidPlaced'
      itemId: number
      itemName: string
      bidPrice: number
      bidderNickname: string
      bidderKey: string
      endedTime: string
      revision: number
    }
  /**
   * 마감 직전 입찰이 들어와 마감이 뒤로 밀렸다.
   *
   * `endedTime` 은 **연장이 반영된 마감 시각**이다. `extendSeconds` 를 직접
   * 더하지 않고 이 값을 그대로 쓴다. 이벤트가 유실되거나 두 번 오면 더하기는
   * 그만큼 어긋나는데, 절대값은 몇 번을 받아도 같은 시각에 도달한다.
   * `extendSeconds` 는 이벤트 피드 문구("+30초 연장")에만 쓴다.
   */
  | {
      kind: 'SoftCloseExtended'
      itemId: number
      itemName: string
      extendSeconds: number
      endedTime: string
      revision: number
    }
  /**
   * 판매자가 마감을 앞당겼다. **이 이벤트가 오는 순간부터가 Soft Close 연장 구간**이라
   * 마감 임박(`ItemClosingSoon`)과 같은 뜻이고, 대신 마감 시각이 함께 온다.
   *
   * `endedTime` 은 앞당겨진 마감 시각이라 화면이 이 값으로 카운트다운을 다시 맞춘다.
   * `remainingSeconds` 는 그때까지 남은 초로 경매방의 연장 트리거 값과 같다.
   *
   * 앞당긴 뒤에도 연장은 그대로 살아 있어서, 이 구간에 입찰이 들어오면
   * `SoftCloseExtended` 가 이어서 온다.
   */
  | {
      kind: 'ItemCloseAdvanced'
      itemId: number
      itemName: string
      remainingSeconds: number
      endedTime: string
      revision: number
    }
  // 유찰도 이 이벤트로 온다. 입찰이 없었으면 낙찰가·낙찰자가 둘 다 null 이다.
  | {
      kind: 'ItemEnded'
      itemId: number
      itemName: string
      finalPrice: number | null
      winnerNickname: string | null
      winnerBidderKey: string | null
      revision: number
    }
  /**
   * 판매자가 방송을 끝냈다. 물품별 마감 이벤트가 먼저 오고 이게 마지막에 온다.
   *
   * 물품 단위가 아니라 방 단위라 `itemId` 가 없다.
   */
  | { kind: 'RoomClosed'; roomTitle: string; closedTime: string }
  /**
   * 방의 실시간 참여자 수.
   *
   * 서버가 SSE 연결 수를 센다. 누가 들어오거나 나갈 때, 그리고 heartbeat 로
   * 끊긴 연결을 걷어낸 뒤에 다시 보낸다.
   */
  | { kind: 'ParticipantCount'; participantCount: number }
  /*
   * 판매자가 방 편성을 바꿨다 — 물품을 넣었거나 뺐다.
   *
   * 둘 다 **목록을 다시 읽으라는 신호**이고 물품 자체를 담지 않는다. 그래서
   * 이벤트 피드에도 쌓지 않는다(편성 변경은 경매 진행 사건이 아니고, 벌크로
   * 20개를 넣으면 실제 사건이 묻힌다).
   *
   * `addedCount`·`itemId` 는 어떤 변경이었는지 알려줄 뿐, 화면은 이 값으로
   * 목록을 직접 고치지 않는다.
   */
  | { kind: 'ItemAdded'; addedCount: number }
  | { kind: 'ItemRemoved'; itemId: number }
  /*
   * 판매자가 방 설정(이름·소개·라이브 URL·Soft Close)을 바꿨다. 이것도 신호라
   * payload 가 비어 있고, 화면은 방 정보를 통째로 다시 읽는다.
   */
  | { kind: 'RoomUpdated' }
  /**
   * 재연결했지만 **끊긴 사이의 이벤트를 서버가 다 돌려주지 못했다.**
   *
   * 서버는 방마다 최근 이벤트를 정해진 개수(기본 50)만 들고 있다. 끊긴 동안 그보다
   * 많이 쌓였거나 Redis 가 재시작되면 중간이 비는데, 그걸 알려주는 신호다.
   *
   * **놓친 이벤트를 되살릴 방법은 없다.** 서버도 무엇이 사라졌는지 모른다 — 밀려난
   * 이벤트는 이름조차 남지 않는다. 그래서 이벤트를 하나씩 따라잡는 게 아니라
   * **상태를 통째로 다시 읽는다.** 이벤트는 화면을 조금씩 고치는 지시일 뿐이고
   * 정답은 서버에 있으므로, 지시를 몇 개 놓쳤든 정답을 다시 읽으면 화면이 맞는다.
   *
   * 반대로 알림 피드에 안 쌓인 줄은 복구 대상이 아니다. 자리를 비운 사이의 입찰
   * 수십 건을 뒤늦게 쏟아붓는 편이 더 이상하다.
   *
   * `reason` 은 운영에서 유실 경로를 구분하는 값이고 화면 동작은 값과 무관하다.
   */
  | { kind: 'EventsLost'; reason: string }

export type SseEventPayload = SseEventData & { eventId?: number }

/**
 * 실시간 SSE 연결과 상태.
 *
 * 구독 경로는 인증이 필요 없는 공개 경로라 방을 숫자 ID 가 아닌 공유 코드로 지목한다.
 * 숫자 ID 를 받던 시절에는 링크를 못 받은 사람도 1, 2, 3... 을 훑어 남의 방 이벤트를
 * 구독할 수 있었다.
 *
 * shareCode 가 바뀌거나, 자동 재시도가 걸리거나, retry() 를 호출하면 EventSource 를
 * 닫고 다시 연다. 브라우저가 재접속을 포기하면(`readyState === CLOSED`) 훅이 대신
 * `MAX_RETRIES` 번까지 다시 열고, 다 소진해야 `failed` 에서 멈춘다.
 * onEvent 는 매 렌더에서 ref 로 최신값을 유지하므로 바뀌어도 재연결하지 않는다.
 * 언마운트 시 EventSource 를 닫아 구독을 정리한다.
 *
 * 방이 종료되면(`ROOM_CLOSED`) 상태가 `closed` 가 되고 연결을 닫는다. 이유는 아래
 * 리스너 주석 참고 — 닫지 않으면 정상 종료가 연결 실패처럼 보인다.
 */
export function useRealtimeStatus(
  shareCode: string,
  onEvent: (payload: SseEventPayload) => void,
) {
  const [status, setStatus] = useState<RealtimeStatus>('connecting')
  const [retryKey, setRetryKey] = useState(0)
  const onEventRef = useRef(onEvent)
  /** 마지막으로 붙은 뒤 연속으로 실패한 횟수. `onopen` 에서 0 으로 돌아간다. */
  const attemptsRef = useRef(0)

  // 콜백이 바뀌어도 EventSource 를 다시 열지 않는다.
  useEffect(() => {
    onEventRef.current = onEvent
  })

  // 다른 방으로 옮기면 이전 방의 실패 횟수를 들고 가지 않는다.
  useEffect(() => {
    attemptsRef.current = 0
  }, [shareCode])

  useEffect(() => {
    setStatus('connecting')

    let retryTimer: ReturnType<typeof setTimeout> | undefined

    const es = new EventSource(
      `${API_BASE_URL}/api/v1/auction-rooms/share/${shareCode}/subscribe`,
      { withCredentials: true },
    )

    es.onopen = () => {
      console.log('[SSE] connected')
      setStatus('connected')
      attemptsRef.current = 0
    }
    /**
     * `readyState` 로 영구 실패와 일시 단절을 가른다.
     *
     * `CONNECTING` 이면 브라우저가 잠시 뒤 스스로 다시 붙으므로 상태만 바꾸고 둔다.
     *
     * `CLOSED` 면 브라우저가 재접속을 포기한 것이다 — 서버가 200 이 아닌 응답을 준
     * 경우(없는 방 404, 종료된 방 409, 재배포 중 502)가 여기다. **이때 가만히 두면
     * 화면이 영구히 굳는다.** 그래서 훅이 대신 EventSource 를 다시 만든다.
     *
     * 재시도가 무한이 아닌 이유는 위 `MAX_RETRIES` 주석 참고. 다 소진하면 `failed`
     * 에서 멈추고, 그다음은 `retry()` 를 부르는 쪽이 살린다.
     *
     * 다시 붙은 뒤의 재동기화는 호출부가 한다. 화면이 `connected` 가 될 때마다
     * Redis Live Snapshot 을 다시 읽어 `revision` 으로 병합하는 흐름(#387)이다.
     */
    es.onerror = (e) => {
      console.error('[SSE] error', e)

      if (es.readyState !== EventSource.CLOSED) {
        setStatus('reconnecting')
        return
      }

      if (attemptsRef.current >= MAX_RETRIES) {
        setStatus('failed')
        return
      }

      const delay = RETRY_BASE_MS * 2 ** attemptsRef.current
      attemptsRef.current += 1
      setStatus('reconnecting')
      retryTimer = setTimeout(() => setRetryKey((k) => k + 1), delay)
    }

    function makeHandler(kind: SseEventPayload['kind']) {
      return (e: MessageEvent) => {
        console.log('[SSE] received', kind, e.data)
        try {
          const data = JSON.parse(e.data as string)
          const eventId = eventIdFromLastEventId(kind, e.lastEventId)
          onEventRef.current({
            kind,
            ...data,
            ...(eventId === undefined ? {} : { eventId }),
          } as SseEventPayload)
        } catch (err) {
          console.error('[SSE] parse error', kind, e.data, err)
        }
      }
    }

    es.addEventListener('ITEM_STARTED', makeHandler('ItemStarted'))
    es.addEventListener('ITEM_CLOSING_SOON', makeHandler('ItemClosingSoon'))
    es.addEventListener('BID_PLACED', makeHandler('BidPlaced'))
    es.addEventListener('SOFT_CLOSE_EXTENDED', makeHandler('SoftCloseExtended'))
    es.addEventListener('ITEM_CLOSE_ADVANCED', makeHandler('ItemCloseAdvanced'))
    es.addEventListener('ITEM_ENDED', makeHandler('ItemEnded'))
    /**
     * 방 종료는 이 연결의 마지막 이벤트다. **받은 뒤 직접 닫는다.**
     *
     * SSE 에는 "서버가 끝냈다"는 신호가 없어서, 서버가 스트림을 끝내도 브라우저는
     * 그것을 네트워크 단절과 구분하지 못하고 3초 뒤 자동으로 다시 붙는다. 서버는 그
     * 재구독을 409 로 거절하고(종료된 방), 그러면 `onerror` 가 두 번 울려 정상적으로
     * 끝난 경매에도 "실시간 연결이 끊겼어요" 경고가 뜬다.
     *
     * 여기서 닫으면 그 재접속 자체가 생기지 않는다 — 명시적 `close()` 는 재접속을
     * 유발하지 않는다.
     */
    const handleRoomClosed = makeHandler('RoomClosed')
    es.addEventListener('ROOM_CLOSED', (e: MessageEvent) => {
      handleRoomClosed(e)
      es.close()
      setStatus('closed')
    })
    es.addEventListener('ITEM_ADDED', makeHandler('ItemAdded'))
    es.addEventListener('ITEM_REMOVED', makeHandler('ItemRemoved'))
    es.addEventListener('ROOM_UPDATED', makeHandler('RoomUpdated'))
    es.addEventListener(
      'PARTICIPANT_COUNT_UPDATED',
      makeHandler('ParticipantCount'),
    )
    /*
     * 서버가 replay 로 못 메운 구간이 있다고 알려준다. 재연결 직후 replay 이벤트보다
     * 먼저 도착하므로, 화면은 뒤이어 오는 이벤트를 처리하기 전에 재조회를 걸 수 있다.
     *
     * id 없이 오기 때문에 `Last-Event-ID` 에 영향을 주지 않는다 — 버퍼에 없는
     * 이벤트라 번호 자체가 없고, 번호가 붙으면 다음 재연결의 replay 기준이 깨진다.
     */
    es.addEventListener('SYSTEM_EVENTS_LOST', makeHandler('EventsLost'))

    return () => {
      clearTimeout(retryTimer)
      es.close()
    }
  }, [shareCode, retryKey])

  /** 자동 재시도를 다 쓴 뒤(`failed`) 사용자가 직접 다시 붙일 때 쓴다. */
  const retry = useCallback(() => {
    attemptsRef.current = 0
    setStatus('reconnecting')
    setRetryKey((k) => k + 1)
  }, [])

  return { status, retry }
}

/**
 * 연결이 끊겼을 때 띄우는 경고 토스트.
 *
 * 라이브 방과 물품 상세가 같은 문구를 쓰고, `status` 한 값만 보고 판단하므로
 * 훅 옆에 둔다. 화면마다 복사해 두면 문구와 조건이 조용히 갈라진다.
 *
 * `reconnecting` 과 `failed` 를 나눠 알린다. 전자는 훅이 아직 다시 붙는 중이라
 * 기다리면 되고, 후자는 자동 재시도를 다 쓴 뒤라 사용자가 새로고침해야 한다.
 * `closed`(정상 종료)에는 아무것도 띄우지 않는다.
 */
export function useRealtimeStatusToast(status: RealtimeStatus) {
  const notifiedRef = useRef<RealtimeStatus | null>(null)

  useEffect(() => {
    if (status === 'connected') {
      notifiedRef.current = null
      return
    }
    if (status !== 'reconnecting' && status !== 'failed') return
    if (notifiedRef.current === status) return
    notifiedRef.current = status

    if (status === 'reconnecting') {
      toast.error('실시간 연결이 끊겼어요. 다시 연결하는 중이에요.', {
        description: '표시된 금액이 최신이 아닐 수 있어요.',
      })
      return
    }
    // 자동 재시도를 다 썼다. 사용자가 움직여야 하므로 스스로 닫히지 않게 둔다.
    toast.error('실시간 연결을 되살리지 못했어요.', {
      description: '새로고침하면 다시 연결해요.',
      duration: 0,
    })
  }, [status])
}
