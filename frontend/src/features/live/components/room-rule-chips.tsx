import { formatClosingLead, formatWon } from '@/lib/format'
import { cn } from '@/lib/utils'

/**
 * 입찰 규칙을 알약 두 개로 보여준다.
 *
 * 입찰 단위와 소프트클로즈(언제 걸리고 몇 분 늘어나는지)는 방마다 다른데,
 * 지금까지는 어디에도 적혀 있지 않아서 마감이 밀려도 왜 밀렸는지 알 수 없었다.
 * 값이 0 이면(= 서버가 아직 안 내려줬거나 규칙이 없으면) 그 알약은 빼고 그린다.
 *
 * 방(`AuctionRoomDetail`)이 아니라 숫자를 받는다. 물품 상세도 같은 규칙을 그리는데
 * 거기서는 방 값이 아니라 물품으로 복사된 `item.bidUnit` 을 써야 하기 때문이다.
 */
export function RoomRuleChips({
  bidUnit,
  softCloseTriggerSeconds,
  softCloseSeconds,
  className,
}: {
  bidUnit: number
  softCloseTriggerSeconds: number
  softCloseSeconds: number
  className?: string
}) {
  const hasSoftClose = softCloseTriggerSeconds > 0 && softCloseSeconds > 0

  if (bidUnit <= 0 && !hasSoftClose) return null

  return (
    <div className={cn('flex flex-wrap items-center gap-1.5', className)}>
      {bidUnit > 0 && (
        <span className="flex h-6 items-center rounded-md bg-fill px-2 text-[11px] font-semibold tabular-nums text-neutral-secondary">
          최소 입찰 단위 {formatWon(bidUnit)}
        </span>
      )}

      {hasSoftClose && (
        <span className="flex h-6 items-center rounded-md bg-notice-surface px-2 text-[11px] font-semibold tabular-nums text-notice">
          마감 {formatClosingLead(softCloseTriggerSeconds)} 전 입찰 시{' '}
          {formatClosingLead(softCloseSeconds)} 연장
        </span>
      )}
    </div>
  )
}
