import { useCallback, useEffect, useRef, useState } from 'react'

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

export type RealtimeStatus =
  'connecting' | 'connected' | 'reconnecting' | 'failed'

export type SseEventPayload =
  | { kind: 'ItemStarted'; itemId: number; itemName: string; endedTime: string }
  | { kind: 'ItemClosingSoon'; itemId: number; itemName: string }
  | {
      kind: 'BidPlaced'
      itemId: number
      itemName: string
      bidPrice: number
      bidderNickname: string
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
    }
  // 유찰도 이 이벤트로 온다. 입찰이 없었으면 낙찰가·낙찰자가 둘 다 null 이다.
  | {
      kind: 'ItemEnded'
      itemId: number
      itemName: string
      finalPrice: number | null
      winnerNickname: string | null
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
 * 실시간 SSE 연결과 상태.
 *
 * 구독 경로는 인증이 필요 없는 공개 경로라 방을 숫자 ID 가 아닌 공유 코드로 지목한다.
 * 숫자 ID 를 받던 시절에는 링크를 못 받은 사람도 1, 2, 3... 을 훑어 남의 방 이벤트를
 * 구독할 수 있었다.
 *
 * shareCode 가 바뀌거나 retry() 를 호출하면 EventSource 를 닫고 다시 연다.
 * onEvent 는 매 렌더에서 ref 로 최신값을 유지하므로 바뀌어도 재연결하지 않는다.
 * 언마운트 시 EventSource 를 닫아 구독을 정리한다.
 */
export function useRealtimeStatus(
  shareCode: string,
  onEvent: (payload: SseEventPayload) => void,
) {
  const [status, setStatus] = useState<RealtimeStatus>('connecting')
  const [retryKey, setRetryKey] = useState(0)
  const onEventRef = useRef(onEvent)

  // 콜백이 바뀌어도 EventSource 를 다시 열지 않는다.
  useEffect(() => {
    onEventRef.current = onEvent
  })

  useEffect(() => {
    setStatus('connecting')

    const es = new EventSource(
      `${API_BASE_URL}/api/v1/auction-rooms/share/${shareCode}/subscribe`,
      { withCredentials: true },
    )

    es.onopen = () => {
      console.log('[SSE] connected')
      setStatus('connected')
    }
    es.onerror = (e) => {
      console.error('[SSE] error', e)
      setStatus('reconnecting')
    }

    function makeHandler(kind: SseEventPayload['kind']) {
      return (e: MessageEvent) => {
        console.log('[SSE] received', kind, e.data)
        try {
          const data = JSON.parse(e.data as string)
          onEventRef.current({ kind, ...data } as SseEventPayload)
        } catch (err) {
          console.error('[SSE] parse error', kind, e.data, err)
        }
      }
    }

    es.addEventListener('ITEM_STARTED', makeHandler('ItemStarted'))
    es.addEventListener('ITEM_CLOSING_SOON', makeHandler('ItemClosingSoon'))
    es.addEventListener('BID_PLACED', makeHandler('BidPlaced'))
    es.addEventListener('SOFT_CLOSE_EXTENDED', makeHandler('SoftCloseExtended'))
    es.addEventListener('ITEM_ENDED', makeHandler('ItemEnded'))
    es.addEventListener('ROOM_CLOSED', makeHandler('RoomClosed'))
    es.addEventListener('ITEM_ADDED', makeHandler('ItemAdded'))
    es.addEventListener('ITEM_REMOVED', makeHandler('ItemRemoved'))
    es.addEventListener('ROOM_UPDATED', makeHandler('RoomUpdated'))
    es.addEventListener(
      'PARTICIPANT_COUNT_UPDATED',
      makeHandler('ParticipantCount'),
    )

    return () => es.close()
  }, [shareCode, retryKey])

  const retry = useCallback(() => {
    setStatus('reconnecting')
    setRetryKey((k) => k + 1)
  }, [])

  return { status, retry }
}
