import { AlertTriangle, ChevronLeft, Loader2, Pencil } from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'

import { ItemLeaderboard } from '@/features/live/components/leaderboard'
import { MobileEventFeed } from '@/features/live/components/mobile-event-feed'
import { MobileNavDrawer } from '@/components/layout/mobile-nav-drawer'
import { ProductThumbnail } from '@/components/product-thumbnail'
import { cn } from '@/lib/utils'
import {
  formatClosingLead,
  formatRemaining,
  formatWon,
  toHref,
} from '@/lib/format'
import type { AuctionItemDetail, RoomEvent } from '@/types/domain'

/**
 * 모바일 물품 상세 (Figma `MOB-05 · 구매자 · 물품 상세 (LIVE)`, 713:4920).
 *
 * 웹(WEB-13)은 896×590 패널 한 장인데 모바일은 스크롤 페이지다.
 * 앱바(56) → 343 상품 카드 → 세그먼트(이벤트/리더보드) → 하단 고정 입찰 바(88).
 */
export function MobileItemDetailView({
  item,
  sellerName,
  softCloseTriggerSeconds = 0,
  softCloseSeconds = 0,
  events,
  remaining,
  closed,
  ready,
  urgent,
  amount,
  minimum,
  onAmountChange,
  pending,
  bidBlocked,
  feedback,
  onBack,
  onBid,
  onConfirmingChange,
}: {
  item: AuctionItemDetail
  sellerName: string
  /** 방의 마감 임박 기준(초). 0 이면 연장 규칙 줄을 그리지 않는다. */
  softCloseTriggerSeconds?: number
  /** 방의 연장 폭(초) */
  softCloseSeconds?: number
  events: RoomEvent[]
  remaining: number
  closed: boolean
  ready: boolean
  urgent: boolean
  amount: number
  /** 최소 입찰가. 내 입찰가 표시와 초기화에 쓴다. */
  minimum: number
  /** 모바일에서도 금액을 바꿀 수 있어야 한다. */
  onAmountChange?: (next: number) => void
  pending: boolean
  bidBlocked: boolean
  feedback: { tone: 'success' | 'error'; message: string } | null
  onBack: () => void
  onBid: () => void
  /**
   * 확인 화면에 들어갔는지 알린다. 금액을 들고 있는 쪽이 그동안 자동 상향을
   * 멈춰야 해서 위로 올려 보낸다.
   */
  onConfirmingChange?: (confirming: boolean) => void
}) {
  const [tab, setTab] = useState<'events' | 'leaderboard'>('events')
  const [confirming, setConfirming] = useState(false)
  /** 확인 화면을 열 때의 현재가. 그 뒤로 올랐으면 알린다. */
  const [priceAtConfirm, setPriceAtConfirm] = useState<number | null>(null)

  // 입찰 요청이 끝나면(성공·실패 모두) 확인 화면을 닫는다.
  useEffect(() => {
    if (pending) return
    setConfirming(false)
    setPriceAtConfirm(null)
  }, [pending])

  /*
   * 확인 중인지 위로 알린다. 화면을 떠날 때도 꺼진 것으로 알려야 자동 상향이
   * 멈춘 채로 남지 않는다.
   */
  useEffect(() => {
    onConfirmingChange?.(confirming)
    return () => onConfirmingChange?.(false)
  }, [confirming, onConfirmingChange])

  /*
   * 확인하는 사이에 남이 더 올렸다. 확정 버튼은 잠그지 않는다
   * (`QuickBidOverlay`·`ItemDetailPanel` 과 같은 규칙).
   */
  const outbid = priceAtConfirm !== null && item.currentPrice > priceAtConfirm

  /** 지금 금액을 조절할 수 있는 상태인지 */
  const biddable = !closed && !ready && onAmountChange !== undefined
  const belowMinimum = amount < minimum

  return (
    // 하단 고정 바 높이만큼만 본문 아래를 비운다.
    <div className="flex min-h-svh flex-col bg-background">
      {/* 앱바 — 375×56 */}
      <header className="sticky top-0 z-30 flex h-14 shrink-0 items-center gap-2 border-b bg-card px-4">
        <button
          type="button"
          onClick={onBack}
          aria-label="뒤로 가기"
          className="ease-soft -ml-1 flex size-8 shrink-0 items-center justify-center rounded-lg text-foreground transition-all duration-150 hover:bg-fill active:scale-90"
        >
          <ChevronLeft aria-hidden className="size-6" strokeWidth={2} />
        </button>

        {/* 뒤로가기가 이미 있으니 닫기(X)는 두지 않는다. */}
        <h1 className="min-w-0 flex-1 truncate text-[16px] font-bold text-foreground">
          {item.name}
        </h1>

        <MobileNavDrawer />
      </header>

      <main className="flex-1 px-4 pt-4">
        <p className="text-[13px] font-bold text-neutral-tertiary">
          물품 상세 · {closed ? '종료' : ready ? '준비 중' : 'LIVE'}
        </p>

        {/* 안쪽 여백을 넉넉히. 글자가 카드 테두리에 붙으면 답답하다. */}
        <section className="mt-1 rounded-2xl border bg-card p-5">
          {/* 상품 이미지 — 319×260. 데스크톱 `ItemDetailPanel` 과 같은 부품을 쓴다. */}
          <div className="relative">
            <ProductThumbnail
              src={item.imageUrl}
              iconClassName="size-8"
              className="flex h-[260px] items-center justify-center rounded-xl bg-fill text-neutral-muted"
            />
            <span
              className={cn(
                'absolute top-3 left-3 flex h-[22px] items-center rounded-full px-3 text-[11px] font-extrabold text-white',
                closed ? 'bg-neutral-muted' : 'bg-live',
              )}
            >
              {closed ? '종료' : 'LIVE'}
            </span>
          </div>

          <h2 className="mt-6 text-[16px] font-bold text-foreground">
            {item.name}
          </h2>
          <p className="mt-2 text-[12px] font-medium text-neutral-tertiary">
            {sellerName}
          </p>

          {/* 현재 최고가 알약 — 브랜드색으로 강조한다. */}
          <div className="mt-5 rounded-xl border border-brand-200 bg-brand-50 px-4 py-3.5">
            <div className="flex items-baseline justify-between gap-3">
              <span className="text-[12px] font-semibold text-brand-600">
                {closed
                  ? item.sold
                    ? '낙찰가'
                    : '유찰 · 시작가'
                  : ready
                    ? '시작가'
                    : '현재 최고가'}
              </span>
              <span className="text-[20px] font-extrabold tabular-nums text-brand-600">
                {formatWon(ready ? item.startPrice : item.currentPrice)}
              </span>
            </div>
            <div className="mt-1.5 flex items-baseline justify-between gap-3">
              <span
                className={cn(
                  'text-[10px] font-semibold tabular-nums',
                  urgent ? 'text-live' : 'text-neutral-tertiary',
                )}
              >
                {/* 시작 전에는 마감 시각이 아직 없다. 카운트다운을 그리면 0초로 굳는다. */}
                {closed
                  ? '마감됨'
                  : ready
                    ? '시작 전'
                    : /*
                       * 0 초에서는 "남은 시간" 을 떼고 상태만 적는다.
                       * "남은 시간 마감 처리 중" 은 읽히지 않는다.
                       */
                      remaining <= 0
                      ? '마감 처리 중'
                      : `남은 시간 ${formatRemaining(remaining)}`}
              </span>
              <span className="text-[10px] font-semibold tabular-nums text-neutral-tertiary">
                입찰 단위 +{item.bidUnit.toLocaleString('ko-KR')}원
              </span>
            </div>

            {/*
              이 화면은 링크로 바로 들어오면 방 헤더 없이 단독으로 뜬다.
              데스크톱은 `LiveShell` 헤더가 규칙을 들고 있지만 여기는 없어서,
              연장 규칙을 볼 자리가 아예 없었다. 끝난 물품에는 의미가 없어 뺀다.
            */}
            {!closed && softCloseTriggerSeconds > 0 && softCloseSeconds > 0 && (
              <p className="mt-1.5 border-t border-brand-200 pt-1.5 text-[10px] font-semibold tabular-nums text-neutral-tertiary">
                마감 {formatClosingLead(softCloseTriggerSeconds)} 전 입찰 시{' '}
                {formatClosingLead(softCloseSeconds)} 자동 연장
              </p>
            )}
          </div>

          <div className="mt-5 border-t pt-5">
            {item.productUrl ? (
              <a
                href={toHref(item.productUrl)}
                target="_blank"
                rel="noreferrer"
                className="text-[12px] font-semibold text-brand-500 hover:underline"
              >
                상품 링크 ↗
              </a>
            ) : (
              <span className="text-[12px] font-semibold text-neutral-muted">
                상품 링크 없음
              </span>
            )}
          </div>

          <h3 className="mt-5 text-[14px] font-bold text-foreground">
            상품 설명
          </h3>
          <p className="mt-2.5 text-[13px] leading-[21px] font-medium text-neutral-tertiary">
            {item.description}
          </p>
        </section>

        {/*
         * 세그먼트 + 내용 (MOB-04 와 같은 구조).
         * 라이브 화면과 마찬가지로 높이를 뷰포트에 묶어 **카드 안에서만** 스크롤한다.
         */}
        <section className="mt-4 flex h-[min(400px,calc(100svh-22rem))] flex-col overflow-hidden rounded-2xl border bg-card">
          {/*
            끝난 물품에는 이벤트 칸을 두지 않는다. 더 들어올 이벤트가 없어서
            세그먼트 한 칸이 죽은 탭이 된다. 그때는 최종 순위만 남긴다.
          */}
          {closed ? (
            <>
              <p className="border-b p-4 text-[13px] font-bold text-foreground">
                최종 순위
              </p>
              <ul className="min-h-0 flex-1 space-y-3 overflow-y-auto overscroll-contain p-4">
                <ItemLeaderboard item={item} />
              </ul>
            </>
          ) : (
            <>
              <div
                role="tablist"
                aria-label="물품 상세 보기"
                className="grid grid-cols-2 gap-2 border-b p-2"
              >
                {(
                  [
                    { key: 'events', label: '경매방 이벤트' },
                    { key: 'leaderboard', label: '리더보드' },
                  ] as const
                ).map((entry) => (
                  <button
                    key={entry.key}
                    type="button"
                    role="tab"
                    aria-selected={tab === entry.key}
                    onClick={() => setTab(entry.key)}
                    className={cn(
                      'ease-soft h-8 rounded-lg border text-[13px] transition-all duration-150 active:scale-95',
                      tab === entry.key
                        ? 'border-brand-300 bg-brand-100 font-bold text-brand-500'
                        : 'border-transparent font-medium text-neutral-tertiary hover:bg-fill',
                    )}
                  >
                    {entry.label}
                  </button>
                ))}
              </div>

              <div className="grid min-h-0 flex-1 grid-cols-[minmax(0,1fr)]">
                <Slot active={tab === 'events'}>
                  {/* 이 물품 것만 걸러 보여주므로 물품 이름표는 붙이지 않는다. */}
                  <MobileEventFeed events={events} showItemTag={false} />
                </Slot>

                <Slot active={tab === 'leaderboard'}>
                  {/* 경매방 리더보드와 같은 카드. 이 물품 하나만 담는다. */}
                  <ul className="min-h-0 flex-1 space-y-3 overflow-y-auto overscroll-contain p-4">
                    <ItemLeaderboard item={item} />
                  </ul>
                </Slot>
              </div>
            </>
          )}
        </section>

        {feedback && (
          <p
            role="status"
            className={cn(
              'mt-3 rounded-xl px-4 py-3 text-[13px] font-bold',
              feedback.tone === 'success'
                ? 'bg-success-surface text-success'
                : 'bg-live-surface text-live',
            )}
          >
            {feedback.message}
          </p>
        )}
      </main>

      {/*
       * 입찰 영역.
       *
       * 화면에 붙어 따라다니지 않고 본문 맨 아래에 놓는다. 좁은 화면에서
       * 고정 바는 내용 절반을 가려서, 정작 무엇에 입찰하는지가 안 보인다.
       */}
      <div className="mt-4 px-4 pb-16">
        <div className="rounded-2xl border bg-card p-4">
          {biddable && !confirming ? (
            <>
              <p className="text-[11px] font-medium text-neutral-tertiary">
                현재가 {formatWon(item.currentPrice)} · 단위 +
                {item.bidUnit.toLocaleString('ko-KR')}
              </p>

              {/* 이 숫자가 곧 입력칸이다. 눌러서 바로 고쳐 쓸 수 있다. */}
              <div className="mt-3 flex items-center gap-2">
                <span className="shrink-0 text-[12px] font-bold whitespace-nowrap text-brand-600">
                  내 입찰가
                </span>

                <label
                  className={cn(
                    'ease-soft flex h-12 min-w-0 flex-1 cursor-text items-center gap-1.5 rounded-xl border-2 bg-card px-3 transition-all duration-150',
                    'focus-within:ring-2 focus-within:ring-brand-200',
                    belowMinimum ? 'border-live/50' : 'border-brand-300',
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
                      onAmountChange?.(
                        Number(event.target.value.replace(/\D/g, '')) || 0,
                      )
                    }
                    className={cn(
                      'min-w-0 flex-1 bg-transparent text-right text-[18px] leading-none font-extrabold tabular-nums caret-brand-500 outline-none',
                      belowMinimum ? 'text-live' : 'text-brand-600',
                    )}
                  />
                  <span
                    className={cn(
                      'shrink-0 text-[13px] font-bold',
                      belowMinimum ? 'text-live' : 'text-brand-600',
                    )}
                  >
                    원
                  </span>
                </label>
              </div>

              <div className="mt-3 flex gap-2">
                {[1, 5].map((multiplier) => (
                  <button
                    key={multiplier}
                    type="button"
                    onClick={() =>
                      onAmountChange?.(amount + item.bidUnit * multiplier)
                    }
                    className="ease-soft h-10 min-w-0 flex-1 rounded-xl bg-fill text-[13px] font-bold tabular-nums text-neutral-secondary transition-all duration-150 active:scale-95"
                  >
                    +{(item.bidUnit * multiplier).toLocaleString('ko-KR')}
                  </button>
                ))}
                {/* 칸을 비우는 게 아니라 최소 입찰가를 넣는다. `quick-bid-overlay.tsx` 참고. */}
                <button
                  type="button"
                  onClick={() => onAmountChange?.(minimum)}
                  disabled={amount === minimum}
                  className="ease-soft h-10 shrink-0 rounded-xl bg-fill px-3 text-[13px] font-bold text-neutral-tertiary transition-all duration-150 active:scale-95 disabled:opacity-40"
                >
                  최소가
                </button>
              </div>

              {belowMinimum && (
                <p className="mt-2.5 text-center text-[11px] font-semibold text-live">
                  최소 {formatWon(minimum)}부터 입찰할 수 있어요
                </p>
              )}
            </>
          ) : null}

          {confirming && (
            /* 확인 화면 — 금액을 보여주고 확정/취소를 고른다 */
            <div className="mb-3 rounded-2xl border border-brand-200 bg-brand-50 px-4 py-3.5">
              {outbid && (
                <p
                  role="alert"
                  className="mb-2.5 flex items-start justify-center gap-1.5 text-[12px] font-bold text-live"
                >
                  <AlertTriangle
                    aria-hidden
                    className="mt-px size-3.5 shrink-0"
                  />
                  현재가가 {formatWon(item.currentPrice)}으로 올랐어요
                </p>
              )}

              <p className="text-center text-[12px] font-semibold text-brand-600">
                입찰 금액
              </p>
              <p className="mt-1 text-center text-[26px] font-extrabold tabular-nums text-brand-600">
                {formatWon(amount)}
              </p>
              {/* 현재가보다 낮아진 금액에 `+` 를 붙이면 더 쓴 것처럼 읽힌다. */}
              {amount > item.currentPrice ? (
                <p className="mt-1 text-center text-[12px] font-semibold tabular-nums text-brand-500">
                  현재가보다 +
                  {(amount - item.currentPrice).toLocaleString('ko-KR')}원
                </p>
              ) : (
                <p className="mt-1 text-center text-[12px] font-semibold text-live">
                  현재가보다 낮아요
                </p>
              )}
              <p className="mt-1 text-center text-[11px] font-medium text-neutral-muted">
                확인하면 즉시 입찰에 반영됩니다.
              </p>
            </div>
          )}

          {confirming ? (
            <div className="flex gap-2">
              <button
                type="button"
                disabled={pending}
                onClick={() => {
                  setConfirming(false)
                  setPriceAtConfirm(null)
                }}
                className="ease-soft flex h-13 flex-1 items-center justify-center rounded-[14px] border bg-card text-[15px] font-bold text-neutral-secondary transition-all duration-150 active:scale-[0.99] disabled:opacity-50"
              >
                취소
              </button>
              <button
                type="button"
                disabled={pending}
                onClick={onBid}
                className="ease-soft flex h-13 flex-[1.4] items-center justify-center gap-2 rounded-[14px] bg-brand-500 text-[15px] font-bold text-white transition-all duration-150 active:scale-[0.99] disabled:opacity-50 disabled:active:scale-100"
              >
                {pending && (
                  <Loader2 aria-hidden className="size-5 animate-spin" />
                )}
                {pending ? '처리 중…' : '입찰 확정'}
              </button>
            </div>
          ) : (
            <button
              type="button"
              onClick={
                biddable
                  ? () => {
                      setPriceAtConfirm(item.currentPrice)
                      setConfirming(true)
                    }
                  : undefined
              }
              disabled={bidBlocked || pending}
              className="ease-soft mt-3 flex h-13 w-full items-center justify-center gap-2 rounded-[14px] bg-brand-500 text-[16px] font-bold text-white transition-all duration-150 active:scale-[0.99] disabled:cursor-not-allowed disabled:bg-border-strong disabled:opacity-100 disabled:active:scale-100"
            >
              {closed
                ? '마감된 물품이에요'
                : ready
                  ? '아직 시작 전이에요'
                  : `${formatWon(amount)} 입찰하기`}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}

/** 세그먼트로 교차되는 패널 한 칸. */
function Slot({ active, children }: { active: boolean; children: ReactNode }) {
  return (
    <div
      inert={!active}
      className={cn(
        'ease-soft flex min-h-0 min-w-0 flex-col overflow-hidden [grid-area:1/1] transition-all duration-300',
        active
          ? 'translate-y-0 opacity-100'
          : 'pointer-events-none translate-y-2 opacity-0',
      )}
    >
      {children}
    </div>
  )
}
