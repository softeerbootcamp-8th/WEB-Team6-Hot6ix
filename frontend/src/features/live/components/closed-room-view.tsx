import { ChevronLeft, Search, Share2 } from 'lucide-react'
import { Link } from '@tanstack/react-router'
import { useMemo, useState, type ReactNode } from 'react'

import { AppHeader, GuestHeader } from '@/components/layout/app-header'
import { MobileNavDrawer } from '@/components/layout/mobile-nav-drawer'
import { findMockTrade } from '@/mocks/data'
import { ProductThumbnail } from '@/components/product-thumbnail'
import { cn } from '@/lib/utils'
import { formatWon } from '@/lib/format'
import type { AuctionRoomDetail } from '@/mocks/types'

/**
 * 종료된 경매방 (Figma `WEB-20 · 구매자 · 종료된 경매방`).
 *
 * 라이브와 열 구성은 같지만 실시간 요소가 전부 빠진다.
 * 왼쪽 종료된 물품 목록 / 가운데 종료 요약 + 전체 낙찰 결과.
 */
/**
 * 결과 한 줄. 이어지는 거래가 있으면 그 거래 상세로 보낸다.
 *
 * 종료된 방에서 라이브 물품 상세로 보내면 진행 중 화면이 떠 버린다.
 * 내 거래가 아닌 물품(남이 낙찰받은 것)은 볼 권한이 없으므로 누를 수 없다.
 *
 * 예전에는 이름으로 거래를 찾다 실패하면 `itemId % 거래수` 로 아무 거래나
 * 골라서, 물품을 눌렀는데 전혀 다른 물품의 거래가 떴다.
 */
function ResultRow({
  itemId,
  className,
  children,
}: {
  itemId: number
  className?: string
  children: ReactNode
}) {
  const trade = findMockTrade(itemId)
  if (!trade) return <div className={className}>{children}</div>

  return (
    <Link
      to="/trades/$itemId"
      params={{ itemId: String(trade.auctionItemId) }}
      className={cn(
        'ease-soft transition-all duration-150 hover:border-brand-300 active:scale-[0.99]',
        className,
      )}
    >
      {children}
    </Link>
  )
}

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

  const unsoldCount = items.length - sold.length

  return (
    <>
      {/*
       * 모바일(MOB-23)은 웹 3열이 아니라 세로 카드 스택 + 하단 고정 바다.
       * 헤더 카드 → 통계 4칸 → 전체 낙찰 결과.
       */}
      <div className="flex min-h-svh flex-col bg-background pb-16 md:hidden">
        <header className="sticky top-0 z-30 flex h-14 shrink-0 items-center gap-2 border-b bg-card px-4">
          <Link
            to="/rooms"
            aria-label="뒤로 가기"
            className="ease-soft -ml-1 flex size-8 shrink-0 items-center justify-center rounded-lg text-foreground transition-all duration-150 active:scale-90"
          >
            <ChevronLeft aria-hidden className="size-6" strokeWidth={2} />
          </Link>
          <h1 className="min-w-0 flex-1 truncate text-[17px] font-bold text-foreground">
            종료된 경매방
          </h1>

          <MobileNavDrawer />
        </header>

        <main className="flex-1 px-4 pt-5">
          <section className="rounded-2xl border bg-card p-4">
            <span className="flex h-[22px] w-16 items-center justify-center rounded-full bg-fill text-[11px] font-bold text-neutral-tertiary">
              종료
            </span>
            <h2 className="mt-3 text-[18px] font-bold text-foreground">
              {room.title}
            </h2>
            <p className="mt-2 text-[13px] font-medium text-neutral-tertiary">
              모든 물품의 경매가 종료되었습니다.
            </p>
            <p className="mt-2 text-[11px] font-medium text-neutral-muted">
              종료 {closedAt}
            </p>
          </section>

          <dl className="mt-4 grid grid-cols-2 gap-2">
            {[
              {
                label: '전체 물품',
                value: `${items.length}개`,
                accent: 'text-result-idle',
                bg: 'bg-result-idle-surface',
              },
              {
                label: '낙찰',
                value: `${sold.length}건`,
                accent: 'text-result-won',
                bg: 'bg-result-won-surface',
              },
              {
                label: '유찰',
                value: `${unsoldCount}건`,
                accent: 'text-result-failed',
                bg: 'bg-result-failed-surface',
              },
              {
                label: '내 낙찰',
                value: `${myWins.length}개`,
                accent: 'text-brand-500',
                bg: 'bg-brand-50',
              },
            ].map((stat) => (
              <div
                key={stat.label}
                className={cn(
                  'flex h-[68px] flex-col items-center justify-center rounded-xl',
                  stat.bg,
                )}
              >
                <dd
                  className={cn(
                    'text-[17px] font-extrabold tabular-nums',
                    stat.accent,
                  )}
                >
                  {stat.value}
                </dd>
                <dt className="mt-1 text-[12px] font-semibold text-neutral-secondary">
                  {stat.label}
                </dt>
              </div>
            ))}
          </dl>

          {/* 전체 결과도 다른 화면으로 넘기지 않고 여기서 바로 보여준다. */}
          <section className="mt-4 rounded-2xl border bg-card p-4">
            <h3 className="text-[14px] font-bold text-foreground">
              전체 낙찰 결과
            </h3>
            <p className="mt-2 text-[12px] font-medium text-neutral-tertiary">
              낙찰 {sold.length}건 · 유찰 {unsoldCount}건 · 총 낙찰액{' '}
              {formatWon(totalAmount)}
            </p>

            <ul className="mt-3 space-y-2">
              {items.map((item) => {
                const winner = item.leaderboard[0] ?? null
                const won = winner !== null
                const mine = Boolean(winner?.isMe)

                return (
                  <li key={item.id}>
                    <ResultRow
                      itemId={item.id}
                      className={cn(
                        'flex items-center gap-2 rounded-xl border px-3 py-2.5',
                        // 내가 낙찰받은 물품은 한눈에 찾을 수 있어야 한다.
                        mine ? 'border-brand-300 bg-brand-50' : 'bg-card',
                      )}
                    >
                      <span className="min-w-0 flex-1">
                        <span className="flex items-center gap-1.5">
                          <span className="min-w-0 truncate text-[13px] font-bold text-foreground">
                            {item.name}
                          </span>
                          {mine && (
                            <span className="shrink-0 rounded-full bg-brand-500 px-1.5 py-0.5 text-[10px] font-extrabold text-white">
                              내 낙찰
                            </span>
                          )}
                        </span>
                        <span className="mt-0.5 block truncate text-[11px] font-medium text-neutral-tertiary">
                          {winner?.nickname ?? '낙찰자 없음'}
                        </span>
                      </span>

                      <span
                        className={cn(
                          'flex h-6 w-[46px] shrink-0 items-center justify-center rounded-full text-[11px] font-bold',
                          won
                            ? 'bg-result-won-surface text-result-won'
                            : 'bg-result-failed-surface text-result-failed',
                        )}
                      >
                        {won ? '낙찰' : '유찰'}
                      </span>

                      <span
                        className={cn(
                          'w-[84px] shrink-0 text-right text-[13px] tabular-nums',
                          won
                            ? 'font-bold text-foreground'
                            : 'font-medium text-neutral-muted',
                        )}
                      >
                        {won ? formatWon(winner.amount) : '—'}
                      </span>
                    </ResultRow>
                  </li>
                )
              })}
            </ul>
          </section>
        </main>
      </div>

      <div className="hidden min-h-svh flex-col bg-background md:flex lg:h-svh lg:min-h-0 lg:overflow-hidden">
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
                  {visibleItems.map((item) => {
                    return (
                      <li key={item.id}>
                        {/*
                         * 끝난 물품은 그 물품의 거래로 보낸다. 이어지는 거래가
                         * 없으면 아무 데도 보내지 않는다. 예전에는 라이브 물품
                         * 상세로 떨어져서, 종료된 방인데 진행 중 화면이 떴다.
                         */}
                        {/* 누르면 그 물품의 거래 상세로 간다. */}
                        <ResultRow
                          itemId={item.id}
                          className={cn(
                            'flex gap-3 rounded-2xl border p-3',
                            item.leaderboard[0]?.isMe
                              ? 'border-brand-300 bg-brand-50'
                              : 'bg-card',
                          )}
                        >
                          <ProductThumbnail
                            name={item.name}
                            size={200}
                            iconClassName="size-5"
                            className="flex h-[84px] w-[72px] shrink-0 flex-col items-center justify-center gap-1 rounded-xl bg-fill text-neutral-muted"
                          />
                          <span className="flex min-w-0 flex-1 flex-col">
                            <span
                              className={cn(
                                'flex h-5 w-11 items-center justify-center rounded-full text-[10px] font-bold',
                                item.leaderboard.length > 0
                                  ? 'bg-result-won-surface text-result-won'
                                  : 'bg-result-failed-surface text-result-failed',
                              )}
                            >
                              {item.leaderboard.length > 0 ? '낙찰' : '유찰'}
                            </span>
                            <span className="mt-1.5 block truncate text-[14px] font-bold text-foreground">
                              {item.name}
                            </span>
                            <span className="mt-1 block text-[11px] font-medium text-neutral-tertiary">
                              {item.topBidderNickname ?? '입찰자 없음'}
                            </span>
                            <span
                              className={cn(
                                'mt-auto text-right text-[15px] tabular-nums',
                                item.leaderboard.length > 0
                                  ? 'font-bold text-foreground'
                                  : 'font-medium text-neutral-muted',
                              )}
                            >
                              {item.leaderboard.length > 0
                                ? formatWon(item.currentPrice)
                                : '—'}
                            </span>
                          </span>
                        </ResultRow>
                      </li>
                    )
                  })}
                </ul>
              </div>
            </section>

            {/* 가운데 · 종료 요약 + 전체 결과 (오른쪽 열 없이 넓게 쓴다) */}
            <section className="relative flex min-w-0 flex-col lg:min-h-0 lg:flex-1 lg:self-stretch">
              <h2 className="pb-2.5 text-[13px] font-bold text-neutral-tertiary">
                경매방 종료 요약
              </h2>

              {/* 왼쪽 목록과 같은 높이를 쓰도록 남는 공간을 채운다. */}
              <div className="flex flex-col gap-3 lg:min-h-0 lg:flex-1">
                <div className="shrink-0 rounded-[20px] border bg-card p-4">
                  <h3 className="text-[16px] font-bold text-foreground">
                    경매방 종료 요약
                  </h3>
                  {/*
                   * 낙찰과 유찰은 성격이 반대라 한 칸에 `5 · 1` 로 묶으면
                   * 무엇이 무엇인지 읽어야 안다. 칸을 나누고 결과색을 입힌다.
                   */}
                  <dl className="mt-4 grid grid-cols-2 gap-3 xl:grid-cols-4">
                    {[
                      {
                        label: '전체 물품',
                        value: `${items.length}개`,
                        bg: 'bg-result-idle-surface',
                        color: 'text-result-idle',
                      },
                      {
                        label: '낙찰',
                        value: `${sold.length}건`,
                        bg: 'bg-result-won-surface',
                        color: 'text-result-won',
                      },
                      {
                        label: '유찰',
                        value: `${items.length - sold.length}건`,
                        bg: 'bg-result-failed-surface',
                        color: 'text-result-failed',
                      },
                      {
                        label: '총 낙찰액',
                        value: formatWon(totalAmount),
                        bg: 'bg-brand-50',
                        color: 'text-brand-500',
                      },
                    ].map((stat) => (
                      <div
                        key={stat.label}
                        className={cn(
                          'flex h-[76px] flex-col items-center justify-center rounded-xl',
                          stat.bg,
                        )}
                      >
                        <dd
                          className={cn(
                            'text-[19px] font-extrabold tabular-nums',
                            stat.color,
                          )}
                        >
                          {stat.value}
                        </dd>
                        <dt className="mt-1 text-[12px] font-semibold text-neutral-secondary">
                          {stat.label}
                        </dt>
                      </div>
                    ))}
                  </dl>
                </div>

                <div className="flex flex-col rounded-[20px] border bg-card p-4 lg:min-h-0 lg:flex-1">
                  <h3 className="shrink-0 text-[18px] font-bold text-foreground">
                    전체 물품 결과
                  </h3>
                  <p className="mt-1 shrink-0 text-[12px] font-medium text-neutral-tertiary">
                    낙찰 {sold.length}건 · 유찰 {items.length - sold.length}건
                  </p>

                  <div className="mt-3 flex h-10 shrink-0 items-center rounded-lg bg-surface-subtle px-4 text-[11px] font-semibold text-neutral-tertiary">
                    <span className="flex-1">물품</span>
                    <span className="hidden w-[120px] sm:block">낙찰자</span>
                    <span className="w-[68px] text-center">결과</span>
                    <span className="w-[92px] text-right sm:w-[104px]">
                      낙찰가
                    </span>
                  </div>

                  <ul className="mt-2 min-h-0 flex-1 space-y-2 overflow-y-auto pr-0.5">
                    {items.map((item) => {
                      const winner = item.leaderboard[0] ?? null
                      const won = winner !== null

                      return (
                        <li key={item.id}>
                          <ResultRow
                            itemId={item.id}
                            className="flex h-14 items-center rounded-[10px] border bg-card px-4 text-[13px]"
                          >
                            <span className="min-w-0 flex-1 truncate font-medium text-foreground">
                              {item.name}
                            </span>

                            <span className="hidden w-[120px] truncate font-medium text-neutral-tertiary sm:block">
                              {winner?.nickname ?? '—'}
                            </span>

                            {/* 낙찰·유찰은 색이 아니라 배지로 구분한다. 훑어볼 때 이게 제일 빠르다. */}
                            <span className="flex w-[68px] justify-center">
                              <span
                                className={cn(
                                  'flex h-6 w-[52px] items-center justify-center rounded-full text-[11px] font-bold',
                                  won
                                    ? 'bg-result-won-surface text-result-won'
                                    : 'bg-result-failed-surface text-result-failed',
                                )}
                              >
                                {won ? '낙찰' : '유찰'}
                              </span>
                            </span>

                            <span
                              className={cn(
                                'w-[92px] text-right tabular-nums sm:w-[104px]',
                                won
                                  ? 'font-bold text-foreground'
                                  : 'font-medium text-neutral-muted',
                              )}
                            >
                              {won ? formatWon(winner.amount) : '—'}
                            </span>
                          </ResultRow>
                        </li>
                      )
                    })}
                  </ul>
                </div>
              </div>
            </section>
          </div>
        </div>
      </div>
    </>
  )
}
