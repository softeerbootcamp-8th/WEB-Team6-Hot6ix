import { createFileRoute, Link } from '@tanstack/react-router'
import { ImageIcon, Receipt } from 'lucide-react'
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
  { label: string; chip: string; text: string; mini: string; value: string }
> = {
  ACTION_NEEDED: {
    label: '확인 필요',
    chip: 'bg-[#eff6ff]',
    text: 'text-[#3182f6]',
    mini: 'bg-[#eff6ff]',
    value: 'text-[#3182f6]',
  },
  IN_PROGRESS: {
    label: '거래 중',
    chip: 'bg-[#fff6e3]',
    text: 'text-[#d1870d]',
    mini: 'bg-[#fff8e9]',
    value: 'text-[#d1870d]',
  },
  COMPLETED: {
    label: '거래 완료',
    chip: 'bg-[#e9fbf3]',
    text: 'text-[#13b08c]',
    mini: 'bg-[#e9fbf3]',
    value: 'text-[#13b08c]',
  },
  UNSOLD: {
    label: '유찰',
    chip: 'bg-[#ffeeef]',
    text: 'text-[#e5484d]',
    mini: 'bg-[#ffeeef]',
    value: 'text-[#e5484d]',
  },
}

type Filter = 'ALL' | 'BUYER' | 'SELLER' | TradeStatus

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
    { key: 'ACTION_NEEDED', label: `확인 필요 ${countBy('ACTION_NEEDED')}` },
    { key: 'IN_PROGRESS', label: `거래 중 ${countBy('IN_PROGRESS')}` },
    { key: 'COMPLETED', label: `거래 완료 ${countBy('COMPLETED')}` },
  ]

  const MINI: { status: TradeStatus }[] = [
    { status: 'ACTION_NEEDED' },
    { status: 'IN_PROGRESS' },
    { status: 'COMPLETED' },
    { status: 'UNSOLD' },
  ]

  // 확인 필요 + 거래 중 = 사용자가 지금 손대야 하는 거래
  const openCount = countBy('ACTION_NEEDED') + countBy('IN_PROGRESS')

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
          {/* 거래 현황 */}
          <section className="mt-6 flex flex-wrap items-center gap-6 rounded-[20px] border bg-card p-7">
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

            <dl className="flex flex-wrap gap-4">
              {MINI.map(({ status }) => {
                const style = STATUS_STYLE[status]
                return (
                  <div
                    key={status}
                    className={cn(
                      'flex h-[72px] w-[116px] flex-col justify-center rounded-2xl px-4',
                      style.mini,
                    )}
                  >
                    <dt className="text-[12px] font-semibold text-neutral-tertiary">
                      {style.label}
                    </dt>
                    <dd
                      className={cn(
                        'mt-1 text-[20px] font-extrabold tabular-nums',
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
            className="mt-6 flex h-14 items-center gap-3 overflow-x-auto rounded-[18px] border bg-card px-4"
          >
            {FILTERS.map((item) => (
              <button
                key={item.key}
                type="button"
                role="tab"
                aria-selected={filter === item.key}
                onClick={() => setFilter(item.key)}
                className={cn(
                  'ease-soft h-8 w-[114px] shrink-0 rounded-[14px] text-[13px] font-bold transition-all duration-150 active:scale-95',
                  filter === item.key
                    ? 'bg-brand-50 text-brand-500'
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
            <ul className="mt-6 grid gap-6 xl:grid-cols-2">
              {visible.map((trade) => {
                const style = STATUS_STYLE[trade.status]
                const isSeller = trade.role === 'SELLER'
                const unsold = trade.status === 'UNSOLD'

                return (
                  <li
                    key={trade.id}
                    className="flex gap-4 rounded-[20px] border bg-card p-4"
                  >
                    <span
                      aria-hidden
                      className={cn(
                        'flex size-28 shrink-0 items-center justify-center rounded-2xl border',
                        isSeller
                          ? 'border-[#c0ecd8] bg-[#ecfcf5] text-[#13b08c]'
                          : 'border-brand-200 bg-brand-50 text-brand-500',
                      )}
                    >
                      <ImageIcon className="size-7" />
                    </span>

                    <div className="flex min-w-0 flex-1 flex-col">
                      <div className="flex items-start gap-2">
                        <span
                          className={cn(
                            'flex h-7 w-[88px] shrink-0 items-center justify-center rounded-[14px] text-[12px] font-bold',
                            isSeller
                              ? 'bg-[#ecfcf5] text-[#13b08c]'
                              : 'bg-brand-50 text-brand-500',
                          )}
                        >
                          {isSeller ? '판매자' : '구매자'}
                        </span>

                        <span
                          className={cn(
                            'ml-auto flex h-7 w-28 shrink-0 items-center justify-center rounded-[14px] text-[12px] font-bold',
                            style.chip,
                            style.text,
                          )}
                        >
                          {style.label}
                        </span>
                      </div>

                      <h3 className="mt-3 truncate text-[17px] font-extrabold text-foreground">
                        {trade.productName}
                      </h3>

                      <div className="mt-2 flex items-baseline gap-3">
                        <p className="min-w-0 truncate text-[13px] font-medium text-neutral-tertiary">
                          {unsold
                            ? trade.partnerNickname
                            : `${isSeller ? '구매자' : '판매자'} · ${trade.partnerNickname}`}
                        </p>
                        <p className="ml-auto shrink-0 text-[16px] font-extrabold tabular-nums text-foreground">
                          {unsold ? '—' : formatWon(trade.amount)}
                        </p>
                      </div>

                      <Link
                        to="/trades/$itemId"
                        params={{ itemId: String(trade.auctionItemId) }}
                        className="mt-auto pt-2 text-right text-[13px] font-bold text-brand-500 hover:underline"
                      >
                        상세 보기 →
                      </Link>
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
