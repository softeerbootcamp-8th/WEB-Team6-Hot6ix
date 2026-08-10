import {
  BidIcon,
  ClosingIcon,
  SoftCloseIcon,
  StartIcon,
  WinIcon,
} from '@/features/live/components/event-icons'
import type { RoomEvent } from '@/mocks/types'

/**
 * 이벤트 한 줄의 배경·아이콘 칩 색과 아이콘. Figma `MOB-04` 값이다.
 *
 * 웹과 모바일이 각자 같은 switch 를 들고 있었다. 한쪽만 고치면 같은 이벤트가
 * 화면마다 다른 색으로 보여서 한 곳으로 모았다.
 */
export function resolveEventTone(event: RoomEvent) {
  switch (event.kind) {
    case 'START':
      return { row: 'bg-[#f7f9fb]', chip: 'bg-fill', icon: <StartIcon /> }
    case 'WIN':
      return { row: 'bg-[#f2fcf8]', chip: 'bg-[#e7f7ef]', icon: <WinIcon /> }
    case 'CLOSE':
      return {
        row: 'bg-[#fff5f5]',
        chip: 'bg-[#fdeced]',
        icon: <ClosingIcon />,
      }
    case 'EXTEND':
      return {
        row: 'bg-notice-surface/50',
        chip: 'bg-notice-surface',
        icon: <SoftCloseIcon />,
      }
    case 'REJECT':
      return {
        row: 'bg-[#fff5f5]',
        chip: 'bg-[#fdeced]',
        icon: <ClosingIcon />,
      }
    case 'BID':
    default:
      return { row: 'bg-[#f4f9ff]', chip: 'bg-brand-100', icon: <BidIcon /> }
  }
}
