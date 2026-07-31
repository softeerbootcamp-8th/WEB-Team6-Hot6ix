import { Clock, ImageIcon, Play } from 'lucide-react'

import { formatRemaining, formatWon } from '@/lib/format'
import { isClosingSoon, useCountdown } from '@/hooks/use-countdown'
import { cn } from '@/lib/utils'
import type { AuctionItemDetail } from '@/mocks/types'

/** Figma `c*_status` 표기 그대로 쓴다. */
const STATUS_LABEL = {
  READY: '시작 전',
  ACTIVE: '진행 중',
  CLOSED: '경매 종료',
} as const

/**
 * 라이브 왼쪽 열의 물품 카드 (Figma `card*_bg`).
 *
 * 카드 폭 316, 썸네일 72, 상태 점 7px. 진행 중이면 상태와 남은 시간이
 * 빨강, 아니면 회색이다. 마감 임박은 굵기까지 달라진다.
 */
export function LiveItemCard({
  item,
  selected = false,
  onSelect,
}: {
  item: AuctionItemDetail
  selected?: boolean
  onSelect?: () => void
}) {
  const remaining = useCountdown(item.endsAt)
  const active = item.status === 'ACTIVE'
  const ready = item.status === 'READY'
  const closed = item.status === 'CLOSED'
  const urgent = active && isClosingSoon(remaining)

  return (
    <li>
      <button
        type="button"
        onClick={onSelect}
        aria-pressed={selected}
        className={cn(
          'ease-soft flex w-full gap-3 rounded-2xl border bg-card p-3 text-left transition-all duration-200 active:scale-[0.99]',
          selected
            ? 'border-brand-400 ring-1 ring-brand-200'
            : 'hover:border-border-strong',
        )}
      >
        <span
          aria-hidden
          className="flex size-[72px] shrink-0 items-center justify-center rounded-xl bg-border-strong text-white"
        >
          <ImageIcon className="size-6" />
        </span>

        <span className="flex min-w-0 flex-1 flex-col">
          <span className="flex items-center gap-1.5">
            <span
              aria-hidden
              className={cn(
                'size-[7px] shrink-0 rounded-full',
                active ? 'bg-live' : 'bg-neutral-muted',
              )}
            />
            <span
              className={cn(
                'text-[12px] font-semibold',
                active ? 'text-live' : 'text-neutral-tertiary',
              )}
            >
              {STATUS_LABEL[item.status]}
            </span>

            {!closed && !ready && (
              <span
                className={cn(
                  'ml-auto flex items-center gap-1 text-[11px] tabular-nums',
                  urgent
                    ? 'font-bold text-live'
                    : 'font-medium text-neutral-tertiary',
                )}
              >
                <Clock aria-hidden className="size-[13px]" />
                {formatRemaining(remaining)}
              </span>
            )}
          </span>

          <span className="mt-1.5 block truncate text-[15px] font-bold text-foreground">
            {item.name}
          </span>

          {!ready && (
            <span className="mt-2 flex items-baseline justify-between">
              <span className="text-[11px] font-medium text-neutral-tertiary">
                {closed ? '낙찰가' : '현재가'}
              </span>
              <span
                className={cn(
                  'text-[18px] tabular-nums text-brand-500',
                  closed ? 'font-bold' : 'font-extrabold',
                )}
              >
                {formatWon(item.currentPrice)}
              </span>
            </span>
          )}

          {/* 시작 전 물품은 진행 시간을 정해 바로 시작할 수 있다. */}
          {ready && (
            <span className="mt-2 flex items-center gap-2">
              <span className="flex h-[34px] flex-1 items-center gap-1.5 rounded-[10px] border border-border-strong bg-card px-2.5">
                <Clock
                  aria-hidden
                  className="size-[13px] text-neutral-tertiary"
                />
                <input
                  aria-label={`${item.name} 진행 시간`}
                  defaultValue="30분 00초"
                  onClick={(event) => event.stopPropagation()}
                  className="w-full min-w-0 bg-transparent text-[12px] font-medium text-neutral-tertiary outline-none"
                />
              </span>
              <span
                role="button"
                tabIndex={0}
                onClick={(event) => event.stopPropagation()}
                onKeyDown={(event) => event.stopPropagation()}
                className="flex h-[34px] w-[86px] shrink-0 items-center justify-center gap-1.5 rounded-[10px] bg-success text-[13px] font-bold text-white transition-opacity hover:opacity-90"
              >
                <Play aria-hidden className="size-2.5 fill-current" />
                시작
              </span>
            </span>
          )}
        </span>
      </button>
    </li>
  )
}
