import { ChevronRight, ImageIcon, Search, Share2 } from 'lucide-react'
import { Link } from '@tanstack/react-router'
import { useMemo, useState } from 'react'

import { AppHeader, GuestHeader } from '@/components/layout/app-header'
import { cn } from '@/lib/utils'
import { formatWon } from '@/lib/format'
import type { AuctionRoomDetail } from '@/mocks/types'

/**
 * 종료된 경매방 (Figma `WEB-20 · 구매자 · 종료된 경매방`).
 *
 * 라이브와 열 구성은 같지만 실시간 요소가 전부 빠진다.
 * 왼쪽 종료된 물품 목록 / 가운데 종료 요약 + 전체 낙찰 결과 / 오른쪽 내 낙찰 결과.
 */
export function ClosedRoomView({
  room,
  isGuest,
  closedAt,
}: {
  room: AuctionRoomDetail
  isGuest: boolean
  closedAt: string
}) {
  const [keyword, setKeyword] = useState('')

  const items = room.items
  const visibleItems = useMemo(() => {
    const trimmed = keyword.trim()
    if (!trimmed) return items
    return items.filter((item) => item.name.includes(trimmed))
  }, [items, keyword])

  const sold = items.filter((item) => item.leaderboard.length > 0)
  const totalAmount = sold.reduce(
    (sum, item) => sum + (item.leaderboard[0]?.amount ?? 0),
    0,
  )
  const myWins = items.filter((item) => item.leaderboard[0]?.isMe)
  const myTotal = myWins.reduce(
    (sum, item) => sum + (item.leaderboard[0]?.amount ?? 0),
    0,
  )

  return (
    <div className="flex min-h-svh flex-col bg-background lg:h-svh lg:min-h-0 lg:overflow-hidden">
      {isGuest ? <GuestHeader state="종료" /> : <AppHeader />}

      {/* 방 헤더 */}
      <div className="shrink-0 border-b bg-card">
        <div className="mx-auto flex min-h-[68px] max-w-[1280px] flex-wrap items-center gap-x-4 gap-y-2 px-5 py-3 md:px-7">
          <span className="flex h-6 items-center rounded-full bg-fill px-3 text-[11px] font-bold text-neutral-tertiary">
            종료
          </span>
          <h1 className="text-[17px] font-bold text-foreground">
            {room.title}
          </h1>
          <p className="text-[12px] font-medium text-neutral-tertiary">
            종료 {closedAt}
          </p>

          <button
            type="button"
            className="ease-soft ml-auto flex h-9 items-center gap-1.5 rounded-[10px] border bg-card px-4 text-[13px] font-semibold text-foreground transition-all duration-150 hover:bg-fill active:scale-95"
          >
            <Share2 aria-hidden className="size-3.5" />
            공유
          </button>
        </div>
      </div>

      <div className="lg:min-h-0 lg:flex-1">
        <div className="mx-auto flex max-w-[1280px] flex-col gap-5 px-5 pt-[18px] pb-8 lg:h-full lg:flex-row lg:gap-3 lg:pb-[34px]">
          {/* 왼쪽 · 종료된 물품 */}
          <section className="flex flex-col lg:min-h-0 lg:w-[340px] lg:shrink-0">
            <h2 className="pb-2.5 text-[13px] font-bold text-neutral-tertiary lg:px-2">
              종료된 물품 ({items.length})
            </h2>
            <div className="flex flex-col rounded-2xl border p-3 lg:min-h-0 lg:max-h-full">
              <div className="relative shrink-0">
                <Search
                  aria-hidden
                  className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-neutral-muted"
                />
                <input
                  type="search"
                  value={keyword}
                  onChange={(event) => setKeyword(event.target.value)}
                  placeholder="물품 이름 검색"
                  aria-label="물품 이름 검색"
                  className="h-10 w-full rounded-xl border bg-card pr-3 pl-9 text-[13px] font-normal outline-none placeholder:text-neutral-muted focus-visible:border-ring"
                />
              </div>

              <ul className="mt-3 max-h-[420px] space-y-3 overflow-y-auto pr-0.5 lg:max-h-none lg:min-h-0 lg:flex-1">
                {visibleItems.map((item) => (
                  <li key={item.id}>
                    <Link
                      to="/rooms/$roomId/items/$itemId"
                      params={{
                        roomId: String(room.id),
                        itemId: String(item.id),
                      }}
                      className="ease-soft flex gap-3 rounded-2xl border bg-card p-3 transition-all duration-200 hover:border-border-strong active:scale-[0.99]"
                    >
                      <span
                        aria-hidden
                        className="flex h-[84px] w-[72px] shrink-0 flex-col items-center justify-center gap-1 rounded-xl bg-border-strong text-white"
                      >
                        <ImageIcon className="size-5" />
                        <span className="text-[11px] font-medium">상품</span>
                      </span>

                      <span className="flex min-w-0 flex-1 flex-col">
                        <span className="flex h-5 w-11 items-center justify-center rounded-full bg-fill text-[10px] font-bold text-neutral-tertiary">
                          종료
                        </span>
                        <span className="mt-1.5 block truncate text-[14px] font-bold text-foreground">
                          {item.name}
                        </span>
                        <span className="mt-1 block text-[11px] font-medium text-neutral-tertiary">
                          {item.topBidderNickname ?? '입찰자 없음'}
                        </span>
                        <span className="mt-auto text-right text-[15px] font-bold tabular-nums text-brand-500">
                          {item.leaderboard.length > 0
                            ? formatWon(item.currentPrice)
                            : '유찰'}
                        </span>
                      </span>
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          </section>

          {/* 가운데 · 종료 요약 + 전체 결과 */}
          <section className="flex min-w-0 flex-col lg:min-h-0 lg:flex-1 lg:self-stretch">
            <h2 className="pb-2.5 text-[13px] font-bold text-neutral-tertiary">
              경매방 종료 요약
            </h2>

            <div className="flex flex-col gap-3 lg:min-h-0 lg:flex-1">
              <div className="shrink-0 rounded-[20px] border bg-card p-4">
                <h3 className="text-[16px] font-bold text-foreground">
                  경매방 종료 요약
                </h3>
                <dl className="mt-4 grid grid-cols-3 gap-3">
                  {[
                    {
                      label: '전체 물품',
                      value: `${items.length}개`,
                      bg: 'bg-brand-50',
                      color: 'text-brand-500',
                      size: 'text-[20px]',
                    },
                    {
                      label: '낙찰',
                      value: `${sold.length}건`,
                      bg: 'bg-success-surface',
                      color: 'text-success',
                      size: 'text-[20px]',
                    },
                    {
                      label: '총 낙찰액',
                      value: formatWon(totalAmount),
                      bg: 'bg-fill',
                      color: 'text-foreground',
                      size: 'text-[18px]',
                    },
                  ].map((stat) => (
                    <div
                      key={stat.label}
                      className={cn(
                        'flex h-[72px] flex-col items-center justify-center rounded-xl',
                        stat.bg,
                      )}
                    >
                      <dd
                        className={cn(
                          'font-bold tabular-nums',
                          stat.size,
                          stat.color,
                        )}
                      >
                        {stat.value}
                      </dd>
                      <dt className="mt-1 text-[12px] font-medium text-neutral-tertiary">
                        {stat.label}
                      </dt>
                    </div>
                  ))}
                </dl>
              </div>

              <div className="flex flex-col rounded-[20px] border bg-card p-4 lg:min-h-0 lg:max-h-full">
                <h3 className="shrink-0 text-[18px] font-bold text-foreground">
                  전체 물품 낙찰 결과
                </h3>
                <p className="mt-1 shrink-0 text-[12px] font-medium text-neutral-tertiary">
                  유찰을 제외한 최종 결과입니다.
                </p>

                <div className="mt-3 flex h-10 shrink-0 items-center rounded-lg bg-fill px-4 text-[11px] font-semibold text-neutral-tertiary">
                  <span className="flex-1">물품</span>
                  <span className="w-[120px]">낙찰자</span>
                  <span className="w-[104px] text-right">낙찰가</span>
                </div>

                <ul className="mt-2 min-h-0 flex-1 space-y-2 overflow-y-auto pr-0.5">
                  {sold.map((item, index) => {
                    const winner = item.leaderboard[0]!
                    const first = index === 0
                    return (
                      <li
                        key={item.id}
                        className={cn(
                          'flex h-14 items-center rounded-[10px] px-4 text-[13px]',
                          first ? 'bg-brand-50' : 'border bg-card',
                        )}
                      >
                        <span
                          className={cn(
                            'min-w-0 flex-1 truncate',
                            first
                              ? 'font-semibold text-brand-500'
                              : 'font-medium text-foreground',
                          )}
                        >
                          {item.name}
                        </span>
                        <span
                          className={cn(
                            'w-[120px] truncate',
                            first
                              ? 'font-semibold text-brand-500'
                              : 'font-medium text-neutral-tertiary',
                          )}
                        >
                          {winner.nickname}
                        </span>
                        <span
                          className={cn(
                            'w-[104px] text-right tabular-nums',
                            first
                              ? 'font-bold text-brand-500'
                              : 'font-semibold text-neutral-secondary',
                          )}
                        >
                          {formatWon(winner.amount)}
                        </span>
                      </li>
                    )
                  })}
                </ul>

                <p className="mt-3 shrink-0 text-[11px] font-medium text-neutral-muted">
                  총 {items.length}개 물품 · 낙찰 {sold.length}건 · 유찰{' '}
                  {items.length - sold.length}건
                </p>
              </div>
            </div>
          </section>

          {/* 오른쪽 · 내 낙찰 결과 */}
          <aside className="flex flex-col gap-3 lg:min-h-0 lg:w-[308px] lg:shrink-0">
            <h2 className="pb-0.5 text-[13px] font-bold text-neutral-tertiary lg:px-2">
              내 낙찰 결과
            </h2>

            <div className="flex flex-col rounded-[20px] border bg-card p-4 lg:min-h-0 lg:max-h-full">
              <div className="flex shrink-0 items-baseline">
                <h3 className="text-[16px] font-bold text-foreground">
                  내 낙찰 결과
                </h3>
                <span className="ml-auto text-[12px] font-semibold text-brand-500">
                  {myWins.length}건
                </span>
              </div>

              {myWins.length === 0 ? (
                <p className="mt-4 rounded-xl bg-surface-subtle py-8 text-center text-[12px] font-medium text-neutral-muted">
                  낙찰받은 물품이 없어요.
                </p>
              ) : (
                <>
                  <ul className="mt-3 min-h-0 flex-1 space-y-2.5 overflow-y-auto pr-0.5">
                    {myWins.map((item, index) => (
                      <li
                        key={item.id}
                        className={cn(
                          'rounded-xl p-4',
                          index === 0
                            ? 'border border-brand-200 bg-brand-50'
                            : 'border bg-surface-subtle',
                        )}
                      >
                        <div className="flex items-baseline gap-2">
                          <p className="min-w-0 truncate text-[13px] font-bold text-foreground">
                            {item.name}
                          </p>
                          <p className="ml-auto shrink-0 text-[13px] font-bold tabular-nums text-brand-500">
                            {formatWon(item.currentPrice)}
                          </p>
                        </div>
                        <div className="mt-2 flex items-baseline gap-2">
                          <p className="text-[11px] font-medium text-neutral-tertiary">
                            {room.sellerName}
                          </p>
                          <p
                            className={cn(
                              'ml-auto text-[11px]',
                              index === 0
                                ? 'font-bold text-success'
                                : 'font-semibold text-neutral-tertiary',
                            )}
                          >
                            {index === 0 ? '거래 필요' : '거래 완료'}
                          </p>
                        </div>
                      </li>
                    ))}
                  </ul>

                  <div className="mt-3 shrink-0 rounded-xl border bg-surface-subtle p-4">
                    <p className="text-[12px] font-semibold text-neutral-tertiary">
                      내 낙찰 요약
                    </p>
                    <div className="mt-2.5 flex items-baseline">
                      <span className="text-[12px] font-medium text-neutral-tertiary">
                        총 낙찰액
                      </span>
                      <span className="ml-auto text-[17px] font-extrabold tabular-nums text-brand-500">
                        {formatWon(myTotal)}
                      </span>
                    </div>
                    <p className="mt-2.5 text-[12px] font-semibold text-neutral-secondary">
                      거래 필요 {myWins.length > 0 ? 1 : 0}건 · 거래 완료{' '}
                      {Math.max(0, myWins.length - 1)}건
                    </p>
                  </div>
                </>
              )}
            </div>

            <div className="shrink-0 rounded-[20px] border bg-card p-4">
              <h3 className="text-[16px] font-bold text-foreground">
                종료된 경매방
              </h3>
              <p className="mt-2.5 text-[13px] leading-[1.5] font-medium text-neutral-tertiary">
                실시간 입찰은 종료되었습니다.
                <br />
                낙찰 물품의 거래 상태만 확인할 수 있어요.
              </p>

              <Link
                to="/trades"
                className="ease-soft mt-4 flex h-10 items-center justify-center gap-1 rounded-xl border border-brand-200 bg-brand-50 text-[13px] font-semibold text-brand-500 transition-all duration-150 hover:opacity-90 active:scale-[0.98]"
              >
                거래 내역 확인
                <ChevronRight aria-hidden className="size-3.5" />
              </Link>

              <Link
                to="/rooms"
                className="ease-soft mt-3 flex h-11 items-center justify-center rounded-[14px] bg-primary text-[15px] font-bold text-primary-foreground transition-all duration-150 hover:opacity-90 active:scale-[0.98]"
              >
                참여 경매방 목록으로
              </Link>
            </div>
          </aside>
        </div>
      </div>
    </div>
  )
}
