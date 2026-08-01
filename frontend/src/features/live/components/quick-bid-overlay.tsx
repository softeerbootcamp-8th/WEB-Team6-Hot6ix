import { Clock, Pencil, X } from 'lucide-react'
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
    /*
     * 물품마다 카드로 한 번 더 감싸지 않는다. 좁은 열에서 카드 안에 카드가
     * 들어가면 안쪽 여백이 두 겹이 되어 내용이 가운데로 몰린다.
     * 줄만 쌓고 물품 사이는 구분선으로 나눈다.
     */
    <li
      className="animate-rise border-b py-5 last:border-b-0"
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

      <p className="mt-1.5 text-[12px] font-medium text-neutral-tertiary">
        현재 최고가{' '}
        <span className="font-bold tabular-nums text-neutral-secondary">
          {formatWon(item.currentPrice)}
        </span>
      </p>

      {/* 라벨과 입력칸을 한 줄에. 금액은 직접 고쳐 쓸 수 있다. */}
      <div className="mt-3 flex items-center gap-2">
        <span className="shrink-0 text-[12px] font-bold whitespace-nowrap text-brand-600">
          내 입찰가
        </span>

        {/*
         * 고칠 수 있는 칸이라는 게 보여야 한다.
         * 연필 아이콘 + 포커스 링 + 흰 배경으로 "입력칸"임을 드러낸다.
         */}
        <label
          className={cn(
            'ease-soft flex h-11 min-w-0 flex-1 cursor-text items-center gap-1.5 rounded-xl border-2 bg-card px-3 transition-all duration-150',
            'focus-within:ring-2 focus-within:ring-brand-200',
            error
              ? 'border-live/50'
              : 'border-brand-300 hover:border-brand-400',
          )}
        >
          <Pencil
            aria-hidden
            className="size-3.5 shrink-0 text-neutral-muted"
          />
          <span className="sr-only">{item.name} 입찰 금액</span>
          <input
            inputMode="numeric"
            onFocus={(event) => event.currentTarget.select()}
            value={amount.toLocaleString('ko-KR')}
            onChange={(event) =>
              setAmount(Number(event.target.value.replace(/\D/g, '')) || 0)
            }
            className={cn(
              'min-w-0 flex-1 cursor-text bg-transparent text-right text-[20px] leading-none font-extrabold tabular-nums caret-brand-500 outline-none',
              error ? 'text-live' : 'text-brand-600',
            )}
          />
          <span
            className={cn(
              'shrink-0 text-[13px] font-bold',
              error ? 'text-live' : 'text-brand-600',
            )}
          >
            원
          </span>
        </label>
      </div>

      <div className="mt-3.5 flex gap-2">
        {PRESETS.map((multiplier) => (
          <button
            key={multiplier}
            type="button"
            // 누를 때마다 누적된다. 두 번 누르면 두 배만큼 오른다.
            onClick={() =>
              setAmount((prev) => prev + item.bidUnit * multiplier)
            }
            className="ease-soft h-9 flex-1 rounded-xl bg-fill text-[13px] font-bold tabular-nums text-neutral-secondary transition-all duration-150 hover:text-brand-500 active:scale-95"
          >
            +{(item.bidUnit * multiplier).toLocaleString('ko-KR')}
          </button>
        ))}

        <button
          type="button"
          onClick={() => setAmount(minimum)}
          disabled={amount === minimum}
          className="ease-soft h-9 shrink-0 rounded-xl bg-fill px-3 text-[13px] font-bold text-neutral-tertiary transition-all duration-150 active:scale-95 disabled:opacity-40"
        >
          초기화
        </button>
      </div>

      <button
        type="button"
        disabled={error !== null}
        onClick={() => onSubmit(item, amount)}
        className="ease-soft mt-3.5 flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-brand-500 text-[14px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-50 disabled:active:scale-100"
      >
        {formatWon(amount)} 입찰하기
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
    <div className="flex h-full flex-col rounded-[20px] bg-card p-3">
      <div className="flex shrink-0 items-start gap-2 px-2 pt-2 pb-3">
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
        <p className="shrink-0 rounded-2xl bg-[#fbfcff] px-4 py-10 text-center text-[13px] font-medium text-neutral-muted">
          지금 입찰할 수 있는 물품이 없어요.
        </p>
      ) : (
        // 물품이 많아도 이 목록 안에서만 스크롤한다.
        <ul className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-1">
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
