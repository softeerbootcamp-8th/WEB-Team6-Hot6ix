import { AlertTriangle, Clock, Loader2, Pencil, X } from 'lucide-react'
import { useEffect, useState } from 'react'

import { formatCountdown, formatWon } from '@/lib/format'
import { isClosingSoon, useCountdown } from '@/hooks/use-countdown'
import { cn } from '@/lib/utils'
import type { AuctionItemDetail } from '@/types/domain'

/** Figma 옵션 칩: 입찰 단위의 1배 / 5배 / 직접 입력 */
const PRESETS = [1, 5] as const

/**
 * 퀵입찰 카드 하나 (Figma `퀵입찰 물품 N`, 308×202).
 *
 * 물품마다 금액을 따로 들고 있어야 해서 카드 단위로 상태를 둔다.
 *
 * **확인도 이 카드 안에서 끝난다.** 예전에는 오른쪽 열이 통째로 입찰 확인
 * 패널로 갈리면서 300ms 전환이 두 번 들어갔고, 그동안 다른 물품이 안 보였다.
 * 게다가 확인 패널이 넘겨받은 물품은 스냅샷이라 그 사이에 남이 더 올려도
 * 현재가가 그대로 멈춰 있었다. 카드 안에 두면 `item` 이 계속 흘러 들어와
 * 그럴 일이 없다.
 */
function QuickBidCard({
  item,
  index,
  pending,
  onSubmit,
}: {
  item: AuctionItemDetail
  /** 카드가 순서대로 등장하도록 지연을 준다. */
  index: number
  /** 이 물품의 입찰을 서버가 처리 중이다. */
  pending: boolean
  onSubmit: (item: AuctionItemDetail, amount: number) => void
}) {
  const remaining = useCountdown(item.endsAt)
  const urgent = isClosingSoon(remaining)

  const minimum = item.currentPrice + item.bidUnit
  const [amount, setAmount] = useState(minimum)
  const [confirming, setConfirming] = useState(false)
  /** 확인 줄을 열 때의 현재가. 그 뒤로 올랐으면 알린다. */
  const [priceAtConfirm, setPriceAtConfirm] = useState<number | null>(null)

  /*
   * 남이 입찰해서 최소가가 올라가면 입력 금액도 끌어올린다. 이게 없으면
   * "최소 N부터" 오류가 뜬 채로 남아 입찰을 못 한다.
   * 최소가 이상을 직접 적어둔 경우는 건드리지 않는다.
   *
   * **확인 줄이 떠 있는 동안에는 건드리지 않는다.** 확정하려는 금액이 눈앞에서
   * 바뀌면 무엇을 확정하는 건지 알 수 없다. 대신 아래에서 현재가가 올랐다고 알린다.
   */
  useEffect(() => {
    if (confirming) return
    setAmount((prev) => (prev < minimum ? minimum : prev))
  }, [minimum, confirming])

  // 요청이 끝나면(성공·실패 모두) 확인 줄을 닫는다.
  useEffect(() => {
    if (!pending) setConfirming(false)
  }, [pending])

  const notOnUnit = (amount - item.currentPrice) % item.bidUnit !== 0
  const error =
    amount < minimum
      ? `최소 ${formatWon(minimum)}부터`
      : notOnUnit
        ? `입찰 단위 ${formatWon(item.bidUnit)}에 맞춰주세요`
        : null

  /*
   * 확인하는 사이에 남이 더 올렸다. 확정 버튼은 잠그지 않는다 — 서버가
   * 거절하겠지만, 무엇을 누르는지 알려준 뒤 고르게 하는 쪽이 낫다.
   */
  const outbid = priceAtConfirm !== null && item.currentPrice > priceAtConfirm

  const openConfirm = () => {
    setPriceAtConfirm(item.currentPrice)
    setConfirming(true)
  }

  const closeConfirm = () => {
    setConfirming(false)
    setPriceAtConfirm(null)
  }

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
          {formatCountdown(remaining)}
        </span>
      </div>

      <p className="mt-1.5 text-[12px] font-medium text-neutral-tertiary">
        현재 최고가{' '}
        <span className="font-bold tabular-nums text-neutral-secondary">
          {formatWon(item.currentPrice)}
        </span>
      </p>

      {confirming ? (
        /*
         * 확인 줄. 카드 아래쪽만 이걸로 바뀌고 물품 이름과 남은 시간, 현재가는
         * 그대로 남는다. 확정하는 동안에도 마감이 얼마나 남았는지 보여야 한다.
         */
        <div className="animate-rise mt-3 rounded-2xl border border-brand-200 bg-brand-50 px-3 py-2.5">
          {outbid && (
            <p
              role="alert"
              className="mb-2 flex items-start gap-1.5 text-[11px] font-bold text-live"
            >
              <AlertTriangle aria-hidden className="mt-px size-3.5 shrink-0" />
              현재가가 {formatWon(item.currentPrice)}으로 올랐어요
            </p>
          )}

          <p className="truncate text-[15px] font-extrabold tabular-nums text-brand-600">
            {formatWon(amount)}
          </p>
          {/* 현재가보다 낮아진 금액에 `+` 를 붙이면 더 쓴 것처럼 읽힌다. */}
          {amount > item.currentPrice ? (
            <p className="text-[11px] font-medium tabular-nums text-brand-400">
              현재 최고가보다 +
              {(amount - item.currentPrice).toLocaleString('ko-KR')}원
            </p>
          ) : (
            <p className="text-[11px] font-medium text-live">
              현재 최고가보다 낮아요
            </p>
          )}

          <div className="mt-2.5 flex gap-2">
            <button
              type="button"
              disabled={pending}
              onClick={closeConfirm}
              className="ease-soft h-9 flex-1 rounded-xl border bg-card text-[13px] font-bold text-neutral-secondary transition-all duration-150 hover:border-border-strong active:scale-[0.98] disabled:opacity-50"
            >
              취소
            </button>
            <button
              type="button"
              disabled={pending}
              onClick={() => onSubmit(item, amount)}
              className="ease-soft flex h-9 flex-[1.4] items-center justify-center gap-1.5 rounded-xl bg-brand-500 text-[13px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-[0.98] disabled:opacity-50 disabled:active:scale-100"
            >
              {pending && (
                <Loader2 aria-hidden className="size-3.5 animate-spin" />
              )}
              {pending ? '처리 중…' : '입찰 확정'}
            </button>
          </div>
        </div>
      ) : (
        <>
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
                  'min-w-0 flex-1 cursor-text bg-transparent text-right text-[16px] leading-none font-extrabold tabular-nums caret-brand-500 outline-none',
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

            {/*
             * "초기화" 가 아니라 "최소가" 다. 이 버튼은 칸을 비우는 게 아니라
             * `현재가 + 입찰 단위` 를 넣는데, 초기화라고 적혀 있으면 쓰던 금액이
             * 지워지는 줄 알고 눌렀다가 3,000원 같은 값이 들어온 것으로 본다(#328).
             */}
            <button
              type="button"
              onClick={() => setAmount(minimum)}
              disabled={amount === minimum}
              className="ease-soft h-9 shrink-0 rounded-xl bg-fill px-3 text-[13px] font-bold text-neutral-tertiary transition-all duration-150 active:scale-95 disabled:opacity-40"
            >
              최소가
            </button>
          </div>

          <button
            type="button"
            disabled={error !== null}
            onClick={openConfirm}
            className="ease-soft mt-3.5 flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-brand-500 text-[14px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-50 disabled:active:scale-100"
          >
            {formatWon(amount)} 입찰하기
          </button>

          {/*
           * 자리를 항상 잡아 둔다. 남이 입찰할 때마다 최소가가 올라가 이 줄이
           * 켜졌다 꺼지는데, 카드가 세로로 쌓여 있어서 한 줄이 생길 때마다
           * 아래 카드들이 통째로 밀린다.
           */}
          <p
            aria-hidden={error === null}
            className={cn(
              'mt-2 min-h-[15px] text-center text-[11px] font-medium text-live',
              error === null ? 'invisible' : 'visible',
            )}
          >
            {error ?? ''}
          </p>
        </>
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
  pendingItemId = null,
  onSubmit,
  onClose,
}: {
  items: AuctionItemDetail[]
  /** 입찰 요청을 서버가 처리 중인 물품. 그 카드의 확인 줄만 잠근다. */
  pendingItemId?: number | null
  /** 카드에서 확정한 입찰. 확인은 카드가 이미 받았으므로 바로 보낸다. */
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
              pending={pendingItemId === item.id}
              onSubmit={onSubmit}
            />
          ))}
        </ul>
      )}
    </div>
  )
}
