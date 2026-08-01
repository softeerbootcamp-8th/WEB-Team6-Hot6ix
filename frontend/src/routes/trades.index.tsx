import { createFileRoute, Link } from '@tanstack/react-router'
import { ProductThumbnail } from '@/components/product-thumbnail'
import { Receipt } from 'lucide-react'
import { useMemo, useState } from 'react'

import { AppShell } from '@/components/layout/page-shell'
import { EmptyState, PageHeader } from '@/components/page-header'
import { MOCK_TRADES } from '@/mocks/data'
import { cn } from '@/lib/utils'
import { formatWon } from '@/lib/format'
import { requireMember } from '@/lib/route-guards'
import type { TradeStatus } from '@/mocks/types'

/**
 * 거래 내역 (Figma `WEB-01 · 공통 · 거래 내역`).
 *
 * 상단에 거래 현황 요약(왼쪽 강조 바 + 상태별 미니 카드 4개),
 * 그 아래 필터 바, 그 아래 596×144 카드 2열이다.
 */
export const Route = createFileRoute('/trades/')({
  beforeLoad: requireMember,
  component: TradesPage,
})

/** 상태별 배지 색. Figma 값을 그대로 쓴다. */
const STATUS_STYLE: Record<
  TradeStatus,
  {
    label: string
    chip: string
    text: string
    mini: string
    value: string
    /** 모바일 카드 둘째 줄. Figma MOB-01 문구를 상태별로 그대로 쓴다. */
    hint: (partnerNickname: string) => string
  }
> = {
  IN_PROGRESS: {
    label: '거래 중',
    chip: 'bg-result-progress-surface',
    text: 'text-result-progress',
    mini: 'bg-[#fff8e9]',
    value: 'text-result-progress',
    hint: (partner: string) => `거래: 1순위 ${partner}와 진행 중`,
  },
  COMPLETED: {
    label: '거래 완료',
    chip: 'bg-result-won-surface',
    text: 'text-result-won',
    mini: 'bg-result-won-surface',
    value: 'text-result-won',
    hint: (partner: string) => `거래: ${partner} · 거래 완료`,
  },
  UNSOLD: {
    label: '유찰',
    chip: 'bg-result-failed-surface',
    text: 'text-live',
    mini: 'bg-result-failed-surface',
    value: 'text-live',
    hint: () => '거래할 후보가 없어 종료되었어요.',
  },
}

type Filter = 'ALL' | 'BUYER' | 'SELLER' | TradeStatus

/** 모바일 필터 바에 들어가는 4개. */
const MOBILE_FILTERS: Filter[] = ['ALL', 'BUYER', 'SELLER', 'IN_PROGRESS']

function TradesPage() {
  const [filter, setFilter] = useState<Filter>('ALL')
  const trades = MOCK_TRADES

  const countBy = (status: TradeStatus) =>
    trades.filter((trade) => trade.status === status).length

  const visible = useMemo(() => {
    if (filter === 'ALL') return trades
    if (filter === 'BUYER' || filter === 'SELLER') {
      return trades.filter((trade) => trade.role === filter)
    }
    return trades.filter((trade) => trade.status === filter)
  }, [trades, filter])

  const FILTERS: { key: Filter; label: string }[] = [
    { key: 'ALL', label: `전체 ${trades.length}` },
    {
      key: 'BUYER',
      label: `구매 ${trades.filter((t) => t.role === 'BUYER').length}`,
    },
    {
      key: 'SELLER',
      label: `판매 ${trades.filter((t) => t.role === 'SELLER').length}`,
    },
    { key: 'IN_PROGRESS', label: `거래 중 ${countBy('IN_PROGRESS')}` },
    { key: 'COMPLETED', label: `거래 완료 ${countBy('COMPLETED')}` },
  ]

  const MINI: { status: TradeStatus }[] = [
    { status: 'IN_PROGRESS' },
    { status: 'COMPLETED' },
    { status: 'UNSOLD' },
  ]

  // 아직 마무리되지 않은 거래 = 사용자가 지금 손대야 하는 거래
  const openCount = countBy('IN_PROGRESS')

  return (
    <AppShell title="거래 내역" className="max-w-[1280px]">
      <PageHeader
        title="거래 내역"
        description="구매와 판매 거래의 진행 상태를 한곳에서 확인하세요."
      />

      {trades.length === 0 ? (
        <div className="mt-6">
          <EmptyState
            icon={<Receipt className="size-8" />}
            title="아직 거래 내역이 없어요"
            description="경매에서 낙찰되면 여기에 표시됩니다."
          />
        </div>
      ) : (
        <>
          {/* 거래 현황 — 모바일(MOB-01)에는 없는 패널이다 */}
          <section className="mt-6 hidden flex-wrap items-center gap-6 rounded-[20px] border bg-card p-7 md:flex">
            <span
              aria-hidden
              className="h-[72px] w-1.5 rounded-[3px] bg-brand-500"
            />

            <div className="min-w-0 flex-1">
              <p className="text-[13px] font-bold text-brand-500">거래 현황</p>
              <p className="mt-1.5 text-[22px] font-extrabold text-foreground">
                확인할 거래가 {openCount}건 있어요
              </p>
              <p className="mt-2 text-[13px] font-medium text-neutral-tertiary">
                상세 화면에서 상대방 정보와 다음 할 일을 확인할 수 있어요.
              </p>
            </div>

            {/*
             * 상태 이름과 건수를 한 줄에 둔 알약 4개(2×2).
             * 위아래로 쪼개 놓으면 이름과 숫자가 따로 읽히고, 한쪽으로 몰아
             * 정렬해도 빈 쪽이 눈에 걸린다. 같은 줄에 두면 눈이 한 번만 간다.
             */}
            <dl className="ml-auto grid w-full max-w-[360px] grid-cols-2 gap-2">
              {MINI.map(({ status }) => {
                const style = STATUS_STYLE[status]
                return (
                  <div
                    key={status}
                    className={cn(
                      'flex h-12 items-center gap-2 rounded-2xl px-3.5',
                      style.mini,
                    )}
                  >
                    <span
                      aria-hidden
                      className={cn(
                        'size-1.5 rounded-full bg-current',
                        style.value,
                      )}
                    />
                    <dt className="truncate text-[12px] font-semibold text-neutral-secondary">
                      {style.label}
                    </dt>
                    <dd
                      className={cn(
                        'ml-auto text-[18px] font-extrabold tabular-nums',
                        style.value,
                      )}
                    >
                      {countBy(status)}
                    </dd>
                  </div>
                )
              })}
            </dl>
          </section>

          {/* 필터 */}
          <div
            role="tablist"
            aria-label="거래 필터"
            className="mt-4 flex h-11 items-center gap-1.5 overflow-x-auto rounded-[14px] border bg-card px-1.5 md:mt-6 md:h-14 md:gap-3 md:rounded-[18px] md:px-4"
          >
            {FILTERS.map((item) => (
              <button
                key={item.key}
                type="button"
                role="tab"
                aria-selected={filter === item.key}
                onClick={() => setFilter(item.key)}
                className={cn(
                  'ease-soft h-8 shrink-0 rounded-[10px] px-3 text-[11px] font-medium transition-all duration-150 active:scale-95 md:w-[114px] md:rounded-[14px] md:px-0 md:text-[13px] md:font-bold',
                  // 모바일(MOB-01)은 앞 4개만 쓴다. 좁은 폭에 딱 맞는다.
                  MOBILE_FILTERS.includes(item.key) ? '' : 'hidden md:block',
                  filter === item.key
                    ? 'bg-brand-500 text-white md:bg-brand-50 md:text-brand-500'
                    : 'text-neutral-tertiary hover:bg-fill',
                )}
              >
                {item.label}
              </button>
            ))}
          </div>

          {visible.length === 0 ? (
            <div className="mt-6">
              <EmptyState
                title="조건에 맞는 거래가 없어요"
                description="필터를 바꿔보세요."
              />
            </div>
          ) : (
            <ul className="mt-4 grid gap-4 md:mt-6 md:gap-6 xl:grid-cols-2">
              {visible.map((trade) => {
                const style = STATUS_STYLE[trade.status]
                const isSeller = trade.role === 'SELLER'
                const unsold = trade.status === 'UNSOLD'

                return (
                  <li
                    key={trade.id}
                    className="rounded-2xl border bg-card p-3 md:rounded-[20px] md:p-4"
                  >
                    {/*
                     * 웹과 모바일이 같은 구조를 쓴다. 예전에는 두 벌을 각각
                     * 그려서 줄 순서와 여백이 서로 어긋나 보였다.
                     * 배지 줄 → 상품명 → 상대·안내 → 금액과 상세 보기.
                     */}
                    <div className="flex items-center gap-3 md:gap-4">
                      <ProductThumbnail
                        name={trade.productName}
                        size={240}
                        iconClassName="size-5 md:size-7"
                        className={cn(
                          'flex size-12 shrink-0 items-center justify-center rounded-xl border md:size-28 md:rounded-2xl',
                          isSeller
                            ? 'border-[#c0ecd8] bg-[#ecfcf5] text-result-won'
                            : 'border-brand-200 bg-brand-50 text-brand-500',
                        )}
                      />

                      <div className="flex min-w-0 flex-1 flex-col justify-center">
                        {/* 제목과 태그가 한 줄. 태그는 오른쪽 끝에 나란히. */}
                        <div className="flex items-center gap-2">
                          <h3 className="min-w-0 flex-1 truncate text-[15px] font-extrabold text-foreground md:text-[17px]">
                            {trade.productName}
                          </h3>

                          <span
                            className={cn(
                              'flex h-6 shrink-0 items-center rounded-full px-2 text-[11px] font-bold',
                              isSeller
                                ? 'bg-[#ecfcf5] text-result-won'
                                : 'bg-brand-50 text-brand-500',
                            )}
                          >
                            {isSeller ? '판매자' : '구매자'}
                          </span>

                          <span
                            className={cn(
                              'flex h-6 shrink-0 items-center rounded-full px-2 text-[11px] font-bold',
                              style.chip,
                              style.text,
                            )}
                          >
                            {style.label}
                          </span>
                        </div>

                        <p className="mt-1.5 truncate text-[12px] font-medium text-neutral-tertiary">
                          {trade.category}
                          {!unsold &&
                            ` · ${isSeller ? '낙찰자' : '판매자'} ${trade.partnerNickname}`}
                        </p>

                        <p className="mt-1 truncate text-[12px] font-medium text-neutral-muted">
                          {style.hint(trade.partnerNickname)}
                        </p>

                        <div className="mt-3 flex items-center justify-between gap-3">
                          <p className="text-[17px] font-extrabold tabular-nums text-foreground md:text-[18px]">
                            {unsold ? '—' : formatWon(trade.amount)}
                          </p>

                          <Link
                            to="/trades/$itemId"
                            params={{ itemId: String(trade.auctionItemId) }}
                            className="ease-soft flex h-10 shrink-0 items-center rounded-xl border bg-card px-4 text-[12px] font-bold text-brand-500 transition-all duration-150 hover:bg-brand-50 active:scale-95 md:text-[13px]"
                          >
                            상세 보기 →
                          </Link>
                        </div>
                      </div>
                    </div>
                  </li>
                )
              })}
            </ul>
          )}
        </>
      )}
    </AppShell>
  )
}
