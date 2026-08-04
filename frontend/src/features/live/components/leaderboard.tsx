import { Clock } from 'lucide-react'

import { LeaderboardRows } from '@/features/live/components/leaderboard-rows'
import { formatRemaining, formatWon } from '@/lib/format'
import { isClosingSoon, useCountdown } from '@/hooks/use-countdown'
import { cn } from '@/lib/utils'
import type { AuctionItemDetail } from '@/mocks/types'

/**
 * 물품별 리더보드 카드 (Figma `lb*_card_bg`, 높이 184).
 *
 * 순위 행은 물품 상세의 실시간 리더보드와 같은 모양을 쓰도록
 * `LeaderboardRows` 를 공유한다.
 */
export function ItemLeaderboard({
  item,
  rowRef,
  justClosed = false,
}: {
  item: AuctionItemDetail
  /** 목록에서 자리를 옮길 때 쓰는 FLIP 참조 */
  rowRef?: (element: HTMLLIElement | null) => void
  /** 방금 마감된 물품. 물품 카드와 같은 도장을 찍는다. */
  justClosed?: boolean
}) {
  const remaining = useCountdown(item.endsAt)
  const closed = item.status === 'CLOSED'
  const urgent = !closed && isClosingSoon(remaining)

  return (
    <li
      ref={rowRef}
      className="relative overflow-hidden rounded-2xl border bg-card p-4"
    >
      {justClosed && (
        <span
          aria-hidden
          className="animate-closed-stamp absolute inset-0 z-10 flex items-center justify-center rounded-2xl"
        >
          <span className="animate-closed-label rounded-xl border-2 border-white bg-live px-4 py-1.5 text-[15px] font-extrabold tracking-wide text-white shadow-lg">
            경매 종료
          </span>
        </span>
      )}

      <div className="flex items-center">
        {closed ? (
          <span className="flex h-[18px] items-center rounded-md bg-fill px-2 text-[9px] font-extrabold text-neutral-tertiary">
            종료
          </span>
        ) : (
          <span className="flex h-[18px] items-center rounded-md bg-live px-2 text-[9px] font-extrabold text-white">
            LIVE
          </span>
        )}

        <span
          className={cn(
            'ml-auto flex items-center gap-1 text-[12px] tabular-nums',
            urgent
              ? 'font-bold text-live'
              : 'font-semibold text-neutral-tertiary',
          )}
        >
          <Clock aria-hidden className="size-[13px]" />
          {closed ? '마감됨' : formatRemaining(remaining)}
        </span>
      </div>

      <div className="mt-2.5 flex items-baseline gap-2">
        <h3 className="min-w-0 truncate text-[13px] font-bold text-foreground">
          {item.name}
        </h3>
        <span className="ml-auto shrink-0 text-[12px] font-medium text-neutral-tertiary">
          {closed ? (item.sold ? '낙찰가' : '유찰') : '현재가'}
        </span>
        <span className="shrink-0 text-[14px] font-bold tabular-nums text-brand-500">
          {formatWon(item.currentPrice)}
        </span>
      </div>

      <div className="mt-3">
        <LeaderboardRows entries={item.leaderboard} />
      </div>
    </li>
  )
}
