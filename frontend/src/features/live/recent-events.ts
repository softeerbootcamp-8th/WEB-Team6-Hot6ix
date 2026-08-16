import type { RecentRoomEventDto } from '@/api/generated/model'
import type { SseEventPayload } from '@/features/live/use-realtime-status'
import { formatClosingLead, formatWon } from '@/lib/format'
import type { RoomEvent } from '@/types/domain'

/**
 * 새로고침 등으로 화면 진입 직후 알림 피드를 미리 채울 때 쓴다.
 *
 * `useRealtimeStatus`가 실시간으로 받는 이벤트와 같은 이름·문구 규칙을 쓰지만,
 * 여기서는 `RoomEvent`만 만들고 `items`(입찰가·리더보드 등) 상태는 건드리지 않는다.
 * 이 API가 돌려주는 이벤트는 이미 최신 REST 응답(`items`)에 반영된 과거 사건이라,
 * 실시간 핸들러처럼 값을 또 더하면 입찰 수·리더보드가 중복으로 갱신된다.
 */
export function toInitialRoomEvents(events: RecentRoomEventDto[]): RoomEvent[] {
  return events
    .map(toRoomEvent)
    .filter((event): event is RoomEvent => event !== null)
}

type PayloadOf<K extends SseEventPayload['kind']> = Omit<
  Extract<SseEventPayload, { kind: K }>,
  'kind'
>

function toRoomEvent(event: RecentRoomEventDto): RoomEvent | null {
  const { id, eventName, data, occurredAt } = event
  if (id == null || !eventName || !occurredAt) return null

  const at = occurredAt

  switch (eventName) {
    case 'ITEM_STARTED': {
      const payload = data as PayloadOf<'ItemStarted'>
      return {
        id,
        at,
        kind: 'START',
        itemId: payload.itemId,
        itemName: payload.itemName,
        message: '경매가 시작됐어요',
      }
    }

    case 'ITEM_CLOSING_SOON': {
      const payload = data as PayloadOf<'ItemClosingSoon'>
      return {
        id,
        at,
        kind: 'CLOSE',
        itemId: payload.itemId,
        itemName: payload.itemName,
        message: `마감 ${formatClosingLead(payload.remainingSeconds)} 전`,
        emphasized: true,
      }
    }

    case 'BID_PLACED': {
      const payload = data as PayloadOf<'BidPlaced'>
      return {
        id,
        at,
        kind: 'BID',
        itemId: payload.itemId,
        itemName: payload.itemName,
        message: `${payload.bidderNickname}님이 ${formatWon(payload.bidPrice)} 입찰`,
        emphasized: true,
      }
    }

    case 'SOFT_CLOSE_EXTENDED': {
      const payload = data as PayloadOf<'SoftCloseExtended'>
      return {
        id,
        at,
        kind: 'EXTEND',
        itemId: payload.itemId,
        itemName: payload.itemName,
        message: `마감 직전 입찰 발생 · 마감 +${
          payload.extendSeconds <= 60
            ? `${payload.extendSeconds}초`
            : `${Math.floor(payload.extendSeconds / 60)}분`
        } 자동 연장`,
        emphasized: true,
      }
    }

    case 'ITEM_CLOSE_ADVANCED': {
      const payload = data as PayloadOf<'ItemCloseAdvanced'>
      return {
        id,
        at,
        kind: 'EXTEND',
        itemId: payload.itemId,
        itemName: payload.itemName,
        message: `판매자가 마감 앞당김 · ${formatClosingLead(payload.remainingSeconds)} 뒤 마감`,
        emphasized: true,
      }
    }

    case 'ITEM_ENDED': {
      const payload = data as PayloadOf<'ItemEnded'>
      return {
        id,
        at,
        kind: payload.winnerNickname ? 'WIN' : 'CLOSE',
        itemId: payload.itemId,
        itemName: payload.itemName,
        message:
          payload.winnerNickname && payload.finalPrice !== null
            ? `${payload.winnerNickname}님이 ${formatWon(payload.finalPrice)}에 낙찰`
            : '경매 종료 · 낙찰자 없음',
        emphasized: true,
      }
    }

    case 'ROOM_CLOSED':
      return {
        id,
        at,
        kind: 'CLOSE',
        message: '판매자가 경매방을 종료했어요',
        emphasized: true,
      }

    // 백엔드가 알림 대상 이벤트만 골라서 주지만, 알 수 없는 이름이 와도
    // 화면이 깨지지 않도록 조용히 무시한다.
    default:
      return null
  }
}
