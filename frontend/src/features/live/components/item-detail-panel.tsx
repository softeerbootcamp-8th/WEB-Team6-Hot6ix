import { Clock, Loader2, Pencil, Share2 } from 'lucide-react'
import { Link } from '@tanstack/react-router'
import { useEffect, useState } from 'react'

import { ItemEventList } from '@/features/live/components/item-event-list'
import { LeaderboardRows } from '@/features/live/components/leaderboard-rows'
import { ProductThumbnail } from '@/components/product-thumbnail'
import {
  formatRemaining,
  formatUrlLabel,
  formatWon,
  toHref,
} from '@/lib/format'
import { cn } from '@/lib/utils'
import type { ReactNode } from 'react'

import type { AuctionItemDetail, RoomEvent } from '@/mocks/types'

/** Figma 퀵입찰 칩: 입찰 단위의 1배 / 5배. 누를 때마다 그만큼 더해진다. */
const PRESETS = [1, 5] as const

/**
 * 물품 상세 본문 (Figma `WEB-13 · 구매자 · 물품 상세 (LIVE)`).
 *
 * 왼쪽 360: 상품 이미지·제목·현재가·링크·설명
 * 오른쪽: 실시간 리더보드 → 물품 이벤트 → 내 입찰가·퀵입찰 행
 *
 * **경매방 화면 위에 얹는 층**과 물품 상세 라우트가 같은 것을 쓴다.
 * 두 벌로 그리면 한쪽만 고쳐져 금세 어긋난다.
 */
export function ItemDetailPanel({
  item,
  shareCode,
  itemId,
  events,
  isGuest,
  closed,
  ready,
  urgent,
  remaining,
  amount,
  minimum,
  pending,
  onAmountChange,
  onBid,
  action,
}: {
  item: AuctionItemDetail
  shareCode: string
  itemId: string
  events: RoomEvent[]
  isGuest: boolean
  closed: boolean
  ready: boolean
  urgent: boolean
  remaining: number
  amount: number
  minimum: number
  pending: boolean
  onAmountChange: (next: number) => void
  onBid: () => void
  /**
   * 입찰 자리에 대신 넣을 것. 종료된 물품처럼 더 입찰할 수 없는 경우
   * "거래 상세 보기" 같은 다음 행동을 여기에 넣는다.
   */
  action?: ReactNode
}) {
  const setAmount = (next: number | ((prev: number) => number)) =>
    onAmountChange(typeof next === 'function' ? next(amount) : next)
  const bidBlocked = closed || ready || amount < minimum

  const [confirming, setConfirming] = useState(false)

  // 입찰 요청이 끝나면(성공·실패 모두) 확인 화면을 닫는다.
  useEffect(() => {
    if (!pending) setConfirming(false)
  }, [pending])

  return (
    <div className="animate-rise rounded-[20px] border bg-card p-5 lg:min-h-0 lg:flex-1 lg:overflow-hidden">
      <div className="grid gap-5 lg:h-full xl:grid-cols-[minmax(0,360px)_minmax(0,1fr)]">
        {/* 왼쪽 · 상품 정보 */}
        <div className="min-w-0 overflow-y-auto">
          <div className="relative">
            <ProductThumbnail
              src={item.imageUrl}
              iconClassName="size-8"
              className="flex h-[220px] items-center justify-center rounded-xl bg-fill text-neutral-muted"
            />
            <span
              className={cn(
                'absolute top-3 left-3 flex h-[22px] items-center justify-center rounded px-2.5 text-[11px] font-bold text-white',
                closed ? 'bg-neutral-muted' : ready ? 'bg-notice' : 'bg-live',
              )}
            >
              {closed ? '경매 종료' : ready ? '시작 전' : 'LIVE'}
            </span>
          </div>

          <div className="mt-5 flex items-baseline gap-2">
            <h2 className="min-w-0 truncate text-[17px] font-bold text-foreground">
              {item.name}
            </h2>
            <span className="ml-auto shrink-0 text-[12px] font-medium text-neutral-tertiary">
              {closed
                ? item.sold
                  ? '낙찰가'
                  : '유찰 · 시작가'
                : ready
                  ? '시작가'
                  : '현재 최고가'}
            </span>
            <span className="shrink-0 text-[14px] font-bold tabular-nums text-neutral-secondary">
              {formatWon(ready ? item.startPrice : item.currentPrice)}
            </span>
          </div>

          {item.productUrl && (
            <a
              href={toHref(item.productUrl)}
              target="_blank"
              rel="noreferrer noopener"
              className="mt-4 flex items-center gap-1.5 text-[13px] font-semibold text-brand-500 hover:underline"
            >
              <Share2 aria-hidden className="size-3.5" />
              상품 링크 · {formatUrlLabel(item.productUrl)}
            </a>
          )}

          <h3 className="mt-5 text-[13px] font-bold text-foreground">
            상품 설명
          </h3>
          <p className="mt-1.5 text-[13px] leading-[1.6] font-normal text-neutral-secondary">
            {item.description}
          </p>
        </div>

        {/* 오른쪽 · 리더보드 → 이벤트 → 퀵입찰 */}
        <div className="flex min-w-0 flex-col gap-4 lg:min-h-0">
          {/*
            남는 높이는 아래 두 칸 중 살아 있는 쪽이 가져간다. 셋 다 고정 높이면
            패널 아래쪽에 아무것도 없는 빈칸이 크게 남는다.
          */}
          <section
            className={cn(
              'flex flex-col rounded-2xl border p-5',
              closed ? 'lg:min-h-0 lg:flex-1' : 'shrink-0',
            )}
          >
            <div className="flex shrink-0 items-baseline">
              <h3 className="text-[13px] font-bold text-neutral-tertiary">
                {closed ? '최종 순위' : '실시간 리더보드'}
              </h3>
              <span
                className={cn(
                  'ml-auto flex items-center gap-1 text-[12px] font-semibold tabular-nums',
                  urgent ? 'text-live' : 'text-neutral-tertiary',
                )}
              >
                <Clock aria-hidden className="size-[13px]" />
                {/* 시작 전에는 마감 시각이 아직 없다. 카운트다운을 그리면 0초로 굳는다. */}
                {closed
                  ? '마감됨'
                  : ready
                    ? '시작 전'
                    : `남은 시간 ${formatRemaining(remaining)}`}
              </span>
            </div>

            <div className="mt-3 min-h-0 flex-1 overflow-y-auto border-t pt-3">
              {/*
                끝난 물품은 이벤트 칸이 빠진 자리를 이 카드가 물려받는다.
                서버가 상위 3명만 내려주므로(`LEADERBOARD_SIZE`) 입찰자가 적으면
                카드가 헐렁하다. 더 채우려면 서버가 내려주는 수를 늘려야 한다.
              */}
              <LeaderboardRows
                entries={item.leaderboard}
                emptyText={closed ? '입찰 없이 마감됐어요' : undefined}
              />
            </div>
          </section>

          {/*
            끝난 물품에는 이벤트 칸을 두지 않는다. 더 들어올 이벤트가 없어
            "시작했습니다 / 마감되었습니다" 두 줄만 남은 칸이 자리만 차지한다.
          */}
          {!closed && (
            <div className="flex h-[200px] flex-col overflow-hidden lg:h-auto lg:min-h-[200px] lg:flex-1">
              <ItemEventList events={events} />
            </div>
          )}

          {/* 입찰 영역 — 하단 한 줄 고정 */}
          <div className="shrink-0">
            {!isGuest && amount < minimum && !closed && !ready && (
              <p className="mb-2 text-[12px] font-medium text-live">
                최소 {formatWon(minimum)}부터 입찰할 수 있어요.
              </p>
            )}

            {action ? (
              action
            ) : isGuest ? (
              <Link
                to="/"
                search={{ redirect: `/rooms/${shareCode}/items/${itemId}` }}
                className="ease-soft flex h-11 items-center justify-center rounded-xl bg-brand-500 text-[14px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-[0.99]"
              >
                로그인하고 입찰하기
              </Link>
            ) : closed || ready ? (
              <p className="flex h-11 items-center justify-center rounded-xl bg-fill text-[13px] font-medium text-neutral-tertiary">
                {closed ? '마감된 물품이에요' : '아직 시작하지 않은 물품이에요'}
              </p>
            ) : confirming ? (
              /* 입찰 확인 행 — 금액을 한 번 더 보여주고 확정/취소를 고른다 */
              <div className="flex items-center gap-2 rounded-2xl border border-brand-200 bg-brand-50 px-3 py-2.5">
                <div className="min-w-0 flex-1">
                  <p className="truncate text-[13px] font-bold tabular-nums text-brand-600">
                    {formatWon(amount)}
                  </p>
                  <p className="text-[11px] font-medium tabular-nums text-brand-400">
                    현재가보다 +
                    {(amount - item.currentPrice).toLocaleString('ko-KR')}원
                  </p>
                </div>
                <button
                  type="button"
                  disabled={pending}
                  onClick={() => setConfirming(false)}
                  className="ease-soft flex h-9 shrink-0 items-center justify-center rounded-xl border bg-card px-3 text-[13px] font-bold text-neutral-secondary transition-all duration-150 hover:border-border-strong active:scale-95 disabled:opacity-50"
                >
                  취소
                </button>
                <button
                  type="button"
                  disabled={pending}
                  onClick={onBid}
                  className="ease-soft flex h-9 shrink-0 items-center justify-center gap-1.5 rounded-xl bg-brand-500 px-4 text-[13px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-[0.99] disabled:opacity-50 disabled:active:scale-100"
                >
                  {pending && (
                    <Loader2 aria-hidden className="size-3.5 animate-spin" />
                  )}
                  {pending ? '처리 중…' : '입찰 확정'}
                </button>
              </div>
            ) : (
              <div className="flex items-center gap-2 rounded-2xl border bg-surface-subtle px-3 py-2.5">
                {/* 칩 버튼 */}
                {PRESETS.map((multiplier) => (
                  <button
                    key={multiplier}
                    type="button"
                    onClick={() =>
                      setAmount((prev) => prev + item.bidUnit * multiplier)
                    }
                    className="ease-soft h-9 shrink-0 rounded-xl bg-fill px-3 text-[12px] font-bold tabular-nums text-neutral-secondary transition-all duration-150 hover:text-brand-500 active:scale-95"
                  >
                    +{(item.bidUnit * multiplier).toLocaleString('ko-KR')}
                  </button>
                ))}

                {/* 금액 입력 */}
                <label
                  className={cn(
                    'ease-soft flex h-9 min-w-0 flex-1 cursor-text items-center gap-1 rounded-xl border-2 bg-card px-2.5 transition-all duration-150',
                    'focus-within:ring-2 focus-within:ring-brand-200',
                    amount < minimum
                      ? 'border-live/50'
                      : 'border-brand-300 hover:border-brand-400',
                  )}
                >
                  <Pencil
                    aria-hidden
                    className="size-3 shrink-0 text-neutral-muted"
                  />
                  <span className="sr-only">입찰 금액</span>
                  <input
                    inputMode="numeric"
                    onFocus={(event) => event.currentTarget.select()}
                    value={amount.toLocaleString('ko-KR')}
                    onChange={(event) =>
                      setAmount(
                        Number(event.target.value.replace(/\D/g, '')) || 0,
                      )
                    }
                    className={cn(
                      'min-w-0 flex-1 bg-transparent text-right text-[16px] leading-none font-extrabold tabular-nums caret-brand-500 outline-none',
                      amount < minimum ? 'text-live' : 'text-brand-600',
                    )}
                  />
                  <span
                    className={cn(
                      'shrink-0 text-[12px] font-bold',
                      amount < minimum ? 'text-live' : 'text-brand-600',
                    )}
                  >
                    원
                  </span>
                </label>

                {/*
                 * 입찰 버튼 — 금액은 바로 왼쪽 입력칸에 이미 표시되므로
                 * 버튼 텍스트는 "입찰"만 담아 너비를 최소화한다.
                 * 버튼이 좁아진 만큼 입력칸이 더 넓어져 13자리까지 잘리지 않는다.
                 */}
                <button
                  type="button"
                  disabled={bidBlocked}
                  onClick={() => setConfirming(true)}
                  className="ease-soft flex h-9 shrink-0 items-center justify-center gap-1.5 rounded-xl bg-brand-500 px-4 text-[13px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-50 disabled:active:scale-100"
                >
                  입찰
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
