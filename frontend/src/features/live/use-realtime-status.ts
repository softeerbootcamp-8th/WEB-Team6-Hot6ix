import { useCallback, useEffect, useRef, useState } from 'react'

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
  | {
      kind: 'SoftCloseExtended'
      itemId: number
      itemName: string
      extendSeconds: number
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

/**
 * 실시간 SSE 연결과 상태.
 *
 * roomId 가 바뀌거나 retry() 를 호출하면 EventSource 를 닫고 다시 연다.
 * onEvent 는 매 렌더에서 ref 로 최신값을 유지하므로 바뀌어도 재연결하지 않는다.
 * 언마운트 시 EventSource 를 닫아 구독을 정리한다.
 */
export function useRealtimeStatus(
  roomId: string,
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

    const es = new EventSource(`/api/v1/auction-rooms/${roomId}/subscribe`, {
      withCredentials: true,
    })

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
    es.addEventListener(
      'PARTICIPANT_COUNT_UPDATED',
      makeHandler('ParticipantCount'),
    )

    return () => es.close()
  }, [roomId, retryKey])

  const retry = useCallback(() => {
    setStatus('reconnecting')
    setRetryKey((k) => k + 1)
  }, [])

  return { status, retry }
}
