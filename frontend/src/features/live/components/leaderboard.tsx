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
export function ItemLeaderboard({ item }: { item: AuctionItemDetail }) {
  const remaining = useCountdown(item.endsAt)
  const closed = item.status === 'CLOSED'
  const urgent = !closed && isClosingSoon(remaining)

  return (
    <li className="rounded-2xl border bg-card p-4">
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
          {closed ? '낙찰가' : '현재가'}
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
