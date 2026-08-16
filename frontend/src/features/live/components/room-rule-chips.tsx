import { formatClosingLead, formatWon } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { AuctionRoomDetail } from '@/types/domain'

/**
 * 방의 입찰 규칙을 알약 두 개로 보여준다.
 *
 * 입찰 단위와 소프트클로즈(언제 걸리고 몇 분 늘어나는지)는 방마다 다른데,
 * 지금까지는 어디에도 적혀 있지 않아서 마감이 밀려도 왜 밀렸는지 알 수 없었다.
 * 값이 0 이면(= 서버가 아직 안 내려줬거나 규칙이 없으면) 그 알약은 빼고 그린다.
 */
export function RoomRuleChips({
  room,
  className,
}: {
  room: AuctionRoomDetail
  className?: string
}) {
  const hasSoftClose =
    room.softCloseTriggerSeconds > 0 && room.softCloseSeconds > 0

  if (room.bidUnit <= 0 && !hasSoftClose) return null

  return (
    <div className={cn('flex flex-wrap items-center gap-1.5', className)}>
      {room.bidUnit > 0 && (
        <span className="flex h-6 items-center rounded-md bg-fill px-2 text-[11px] font-semibold tabular-nums text-neutral-secondary">
          최소 입찰 단위 {formatWon(room.bidUnit)}
        </span>
      )}

      {hasSoftClose && (
        <span className="flex h-6 items-center rounded-md bg-notice-surface px-2 text-[11px] font-semibold tabular-nums text-notice">
          마감 {formatClosingLead(room.softCloseTriggerSeconds)} 전 입찰 시{' '}
          {formatClosingLead(room.softCloseSeconds)} 연장
        </span>
      )}
    </div>
  )
}
