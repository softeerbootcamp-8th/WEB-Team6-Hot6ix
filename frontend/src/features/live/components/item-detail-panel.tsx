import { Clock, Loader2, Pencil, Share2 } from 'lucide-react'
import { Link } from '@tanstack/react-router'

import { ItemEventList } from '@/features/live/components/item-event-list'
import { LeaderboardRows } from '@/features/live/components/leaderboard-rows'
import { ProductThumbnail } from '@/components/product-thumbnail'
import { formatRemaining, formatWon } from '@/lib/format'
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
  roomId,
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
  roomId: string
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

  return (
    <div className="animate-rise rounded-[20px] border bg-card p-5 lg:min-h-0 lg:flex-1 lg:overflow-hidden">
      <div className="grid gap-5 lg:h-full xl:grid-cols-[minmax(0,360px)_minmax(0,1fr)]">
        {/* 왼쪽 · 상품 정보 */}
        <div className="min-w-0 overflow-y-auto">
          <div className="relative">
            <ProductThumbnail
              name={item.name}
              size={640}
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
              {closed ? '낙찰가' : ready ? '시작가' : '현재 최고가'}
            </span>
            <span className="shrink-0 text-[14px] font-bold tabular-nums text-neutral-secondary">
              {formatWon(ready ? item.startPrice : item.currentPrice)}
            </span>
          </div>

          {item.productUrl && (
            <a
              href={`https://${item.productUrl}`}
              target="_blank"
              rel="noreferrer noopener"
              className="mt-4 flex items-center gap-1.5 text-[13px] font-semibold text-brand-500 hover:underline"
            >
              <Share2 aria-hidden className="size-3.5" />
              상품 링크 · {item.productUrl}
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
        <div className="flex min-w-0 flex-col gap-4 overflow-y-auto overscroll-contain lg:min-h-0">
          <section className="rounded-2xl border p-5">
            <div className="flex items-baseline">
              <h3 className="text-[13px] font-bold text-neutral-tertiary">
                실시간 리더보드
              </h3>
              <span
                className={cn(
                  'ml-auto flex items-center gap-1 text-[12px] font-semibold tabular-nums',
                  urgent ? 'text-live' : 'text-neutral-tertiary',
                )}
              >
                <Clock aria-hidden className="size-[13px]" />
                {closed ? '마감됨' : `남은 시간 ${formatRemaining(remaining)}`}
              </span>
            </div>

            <div className="mt-3 border-t pt-3">
              <LeaderboardRows entries={item.leaderboard} />
            </div>
          </section>

          <ItemEventList events={events} />

          {/*
           * 입찰 영역.
           *
           * 예전에는 칩·직접 입력·큰 버튼이 한 줄에 몰려 있어 어느 것이
           * 지금 값이고 어느 것이 조작인지 읽히지 않았다.
           * 한 장의 카드 안에서 금액 → 조절 → 확정 순으로 세운다.
           */}
          {action ? (
            action
          ) : isGuest ? (
            <Link
              to="/"
              search={{
                redirect: `/rooms/${roomId}/items/${itemId}`,
              }}
              className="ease-soft flex h-11 items-center justify-center rounded-xl bg-brand-500 text-[14px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-[0.99]"
            >
              로그인하고 입찰하기
            </Link>
          ) : closed || ready ? (
            <p className="flex h-11 items-center justify-center rounded-xl bg-fill text-[13px] font-medium text-neutral-tertiary">
              {closed ? '마감된 물품이에요' : '아직 시작하지 않은 물품이에요'}
            </p>
          ) : (
            <div className="rounded-2xl border bg-surface-subtle p-3.5">
              <div className="flex items-end justify-between gap-3">
                <div className="min-w-0">
                  <p className="text-[11px] font-medium text-neutral-tertiary">
                    현재가 {formatWon(item.currentPrice)} · 단위 +
                    {item.bidUnit.toLocaleString('ko-KR')}
                  </p>
                  <p className="mt-1 text-[12px] font-bold text-brand-600">
                    내 입찰가
                  </p>
                </div>

                {/* 이 숫자가 곧 입력칸이다. 눌러서 바로 고쳐 쓸 수 있다. */}
                <label
                  className={cn(
                    'ease-soft flex h-11 min-w-0 max-w-[180px] flex-1 cursor-text items-center gap-1.5 rounded-xl border-2 bg-card px-3 transition-all duration-150',
                    'focus-within:ring-2 focus-within:ring-brand-200',
                    amount < minimum
                      ? 'border-live/50'
                      : 'border-brand-300 hover:border-brand-400',
                  )}
                >
                  <Pencil
                    aria-hidden
                    className="size-3.5 shrink-0 text-neutral-muted"
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
                      'min-w-0 flex-1 bg-transparent text-right text-[20px] leading-none font-extrabold tabular-nums caret-brand-500 outline-none',
                      amount < minimum ? 'text-live' : 'text-brand-600',
                    )}
                  />
                  <span
                    className={cn(
                      'shrink-0 text-[13px] font-bold',
                      amount < minimum ? 'text-live' : 'text-brand-600',
                    )}
                  >
                    원
                  </span>
                </label>
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
                    className="ease-soft h-10 flex-1 rounded-xl bg-fill text-[13px] font-bold tabular-nums text-neutral-secondary transition-all duration-150 hover:text-brand-500 active:scale-95"
                  >
                    +{(item.bidUnit * multiplier).toLocaleString('ko-KR')}
                  </button>
                ))}

                <button
                  type="button"
                  onClick={() => setAmount(minimum)}
                  disabled={amount === minimum}
                  className="ease-soft h-10 shrink-0 rounded-xl bg-fill px-3 text-[13px] font-bold text-neutral-tertiary transition-all duration-150 active:scale-95 disabled:opacity-40"
                >
                  초기화
                </button>
              </div>

              <button
                type="button"
                disabled={bidBlocked || pending}
                onClick={onBid}
                className="ease-soft mt-2.5 flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-brand-500 text-[14px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-50 disabled:active:scale-100"
              >
                {pending && (
                  <Loader2 aria-hidden className="size-4 animate-spin" />
                )}
                {pending ? '처리 중…' : `${formatWon(amount)} 입찰하기`}
              </button>
            </div>
          )}

          {!isGuest && amount < minimum && !closed && !ready && (
            <p className="text-[12px] font-medium text-live">
              최소 {formatWon(minimum)}부터 입찰할 수 있어요.
            </p>
          )}
        </div>
      </div>
    </div>
  )
}
