import { Clock } from 'lucide-react'

import { AuctionCloseFlashOverlay } from '@/features/live/components/auction-close-flash-overlay'
import {
  AuctionStartFlashOverlay,
  type AuctionStartFlashState,
} from '@/features/live/components/auction-start-flash'
import { LeaderboardRows } from '@/features/live/components/leaderboard-rows'
import { SoftCloseFlashOverlay } from '@/features/live/components/soft-close-flash-overlay'
import type { SoftCloseFlash } from '@/features/live/soft-close-flash'
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
  justExtended = null,
  justStarted = null,
}: {
  item: AuctionItemDetail
  /** 목록에서 자리를 옮길 때 쓰는 FLIP 참조 */
  rowRef?: (element: HTMLLIElement | null) => void
  /** 방금 마감된 물품. 물품 카드와 같은 도장을 찍는다. */
  justClosed?: boolean
  /** 방금 소프트클로즈로 연장된 물품. `null` 이면 연출하지 않는다. */
  justExtended?: SoftCloseFlash | null
  /** 방금 경매가 시작된 물품. `null` 이면 연출하지 않는다. */
  justStarted?: AuctionStartFlashState | null
}) {
  const remaining = useCountdown(item.endsAt)
  const closed = item.status === 'CLOSED'
  const urgent = !closed && isClosingSoon(remaining)

  return (
    <li
      ref={rowRef}
      className="relative overflow-hidden rounded-2xl border bg-card p-4"
    >
      {/* 물품 카드와 같은 도장을 찍는다. */}
      {justClosed && <AuctionCloseFlashOverlay item={item} />}

      {/* 소프트클로즈 연장. 물품 카드와 같은 자리, 같은 규칙이다. */}
      {justExtended && !justClosed && (
        <SoftCloseFlashOverlay flash={justExtended} />
      )}

      {/* 경매 시작도 같은 자리다. */}
      {justStarted && !justClosed && !justExtended && (
        <AuctionStartFlashOverlay flash={justStarted} />
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
