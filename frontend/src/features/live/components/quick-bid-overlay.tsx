import { Clock, X } from 'lucide-react'
import { useState } from 'react'

import { formatRemaining, formatWon } from '@/lib/format'
import { isClosingSoon, useCountdown } from '@/hooks/use-countdown'
import { cn } from '@/lib/utils'
import type { AuctionItemDetail } from '@/mocks/types'

/** Figma 옵션 칩: 입찰 단위의 1배 / 5배 / 직접 입력 */
const PRESETS = [1, 5] as const

/**
 * 퀵입찰 카드 하나 (Figma `퀵입찰 물품 N`, 308×202).
 *
 * 물품마다 금액을 따로 들고 있어야 해서 카드 단위로 상태를 둔다.
 */
function QuickBidCard({
  item,
  index,
  onSubmit,
}: {
  item: AuctionItemDetail
  /** 카드가 순서대로 등장하도록 지연을 준다. */
  index: number
  onSubmit: (item: AuctionItemDetail, amount: number) => void
}) {
  const remaining = useCountdown(item.endsAt)
  const urgent = isClosingSoon(remaining)

  const minimum = item.currentPrice + item.bidUnit
  const [amount, setAmount] = useState(minimum)

  const notOnUnit = (amount - item.currentPrice) % item.bidUnit !== 0
  const error =
    amount < minimum
      ? `최소 ${formatWon(minimum)}부터`
      : notOnUnit
        ? `입찰 단위 ${formatWon(item.bidUnit)}에 맞춰주세요`
        : null

  return (
    <li
      className="animate-rise rounded-2xl bg-[#fbfcff] p-4"
      style={{ animationDelay: `${index * 60}ms` }}
    >
      <div className="flex items-baseline gap-2">
        <h3 className="min-w-0 truncate text-[15px] font-bold text-foreground">
          {item.name}
        </h3>
        <span
          className={cn(
            'ml-auto flex shrink-0 items-center gap-1 text-[12px] font-semibold tabular-nums',
            urgent ? 'text-live' : 'text-neutral-tertiary',
          )}
        >
          <Clock aria-hidden className="size-[13px]" />
          {formatRemaining(remaining)}
        </span>
      </div>

      <div className="mt-3 flex h-[42px] items-center rounded-[10px] bg-brand-50 px-3">
        <span className="text-[12px] font-medium text-neutral-tertiary">
          현재 최고가
        </span>
        <span className="ml-auto text-[16px] font-bold tabular-nums text-brand-500">
          {formatWon(item.currentPrice)}
        </span>
      </div>

      <div className="mt-3 flex gap-2">
        {PRESETS.map((multiplier) => (
          <button
            key={multiplier}
            type="button"
            // 누를 때마다 누적된다. 두 번 누르면 두 배만큼 오른다.
            onClick={() =>
              setAmount((prev) => prev + item.bidUnit * multiplier)
            }
            className="ease-soft h-9 flex-1 rounded-[10px] border bg-card text-[12px] font-semibold tabular-nums text-foreground transition-all duration-150 hover:border-border-strong active:scale-95"
          >
            +{(item.bidUnit * multiplier).toLocaleString('ko-KR')}
          </button>
        ))}

        <label className="w-[100px] shrink-0">
          <span className="sr-only">{item.name} 입찰 금액 직접 입력</span>
          <input
            inputMode="numeric"
            placeholder="직접 입력"
            value={amount.toLocaleString('ko-KR')}
            onChange={(event) =>
              setAmount(Number(event.target.value.replace(/\D/g, '')) || 0)
            }
            className="h-9 w-full rounded-[10px] border bg-card px-2 text-right text-[12px] font-semibold tabular-nums outline-none placeholder:font-normal placeholder:text-neutral-muted focus-visible:border-brand-400"
          />
        </label>
      </div>

      <button
        type="button"
        disabled={error !== null}
        onClick={() => onSubmit(item, amount)}
        className="ease-soft mt-3 flex h-10 w-full items-center justify-center gap-2 rounded-[10px] bg-primary text-[14px] font-bold text-primary-foreground transition-all duration-150 hover:opacity-90 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-50 disabled:active:scale-100"
      >
        입찰 {formatWon(amount)}
      </button>

      {error && (
        <p className="mt-2 text-center text-[11px] font-medium text-live">
          {error}
        </p>
      )}
    </li>
  )
}

/**
 * 퀵입찰 오버레이 (Figma `WEB-14 · 구매자 · 퀵입찰 오버레이`).
 *
 * 모달이 아니라 오른쪽 리더보드 열 자리에 뜨는 플로팅 패널이다.
 * 진행 중인 물품마다 카드가 하나씩 쌓이고, 각 카드에서 바로 입찰한다.
 */
export function QuickBidOverlay({
  items,
  onSubmit,
  onClose,
}: {
  items: AuctionItemDetail[]
  /** 금액을 고르면 입찰 확인 패널로 넘긴다. */
  onSubmit: (item: AuctionItemDetail, amount: number) => void
  onClose: () => void
}) {
  return (
    <div className="flex h-full flex-col rounded-[20px] bg-card p-2">
      <div className="flex items-start gap-2 px-2 pt-2 pb-3">
        <div className="min-w-0">
          <h2 className="text-[17px] font-bold text-foreground">빠른 입찰</h2>
          <p className="mt-1 text-[12px] font-medium text-neutral-tertiary">
            진행 중인 물품 {items.length}개
          </p>
        </div>

        <button
          type="button"
          onClick={onClose}
          aria-label="빠른 입찰 닫기"
          className="ml-auto flex size-7 shrink-0 items-center justify-center rounded-full text-[14px] font-semibold text-neutral-secondary transition-colors hover:bg-fill"
        >
          <X aria-hidden className="size-4" />
        </button>
      </div>

      {items.length === 0 ? (
        <p className="rounded-2xl bg-[#fbfcff] px-4 py-10 text-center text-[13px] font-medium text-neutral-muted">
          지금 입찰할 수 있는 물품이 없어요.
        </p>
      ) : (
        <ul className="min-h-0 flex-1 space-y-4 overflow-y-auto pr-0.5">
          {items.map((item, index) => (
            <QuickBidCard
              key={item.id}
              item={item}
              index={index}
              onSubmit={onSubmit}
            />
          ))}
        </ul>
      )}
    </div>
  )
}
