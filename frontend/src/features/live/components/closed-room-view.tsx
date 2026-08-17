import { ChevronLeft, Search } from 'lucide-react'
import { Link } from '@tanstack/react-router'
import { useMemo, useState, type ReactNode } from 'react'

import { AppHeader, GuestHeader } from '@/components/layout/app-header'
import { MobileNavDrawer } from '@/components/layout/mobile-nav-drawer'
import { ProductThumbnail } from '@/components/product-thumbnail'
import { RoomDealStatus } from '@/features/rooms/components/room-deal-status'
import { useGetDeals } from '@/api/generated/거래-내역/거래-내역'
import { toDeals } from '@/features/trades/adapt-deal'
import { cn } from '@/lib/utils'
import { formatDate, formatWon } from '@/lib/format'
import type { RoomResult } from '@/features/live/adapt-result'

/**
 * 종료된 경매방 (Figma `WEB-20 · 구매자 · 종료된 경매방`).
 *
 * 라이브와 열 구성은 같지만 실시간 요소가 전부 빠진다.
 * 왼쪽 종료된 물품 목록 / 가운데 종료 요약 + 전체 낙찰 결과.
 */
/**
 * 결과 한 줄. 이어지는 거래를 볼 권한이 있을 때만 그 거래 상세로 보낸다.
 *
 * 거래 상대(판매자·구매자)만 `/trades/$itemId` 를 볼 수 있고, 그 외 사용자는
 * 눌러도 403 이 난다. 서버가 실제로 판정하지만(루트 CLAUDE.md), 볼 수 없는
 * 링크를 미리 감춰 헛걸음을 없앤다.
 *
 * `isMyWin`(최종 1순위 낙찰)만으로는 부족하다 — 1순위가 거래를 접어 차순위로
 * 넘어온 경우도 거래 상대다. `/deals` 는 내 거래만 내려주므로, 그 목록에
 * `auctionItemId` 가 있으면 순위와 무관하게 내가 상대라는 뜻이다.
 *
 * 예전에는 이름으로 거래를 찾다 실패하면 `itemId % 거래수` 로 아무 거래나
 * 골라서, 물품을 눌렀는데 전혀 다른 물품의 거래가 떴다.
 */
function ResultRow({
  itemId,
  canLink,
  className,
  children,
}: {
  itemId: number
  canLink: boolean
  className?: string
  children: ReactNode
}) {
  if (!canLink) return <div className={className}>{children}</div>

  return (
    <Link
      to="/trades/$itemId"
      params={{ itemId: String(itemId) }}
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
  result,
  isGuest,
  isOwner,
}: {
  result: RoomResult
  isGuest: boolean
  isOwner: boolean
}) {
  const [keyword, setKeyword] = useState('')

  const items = result.items

  /*
   * 결과를 열면 낙찰된 물품부터 찾게 된다. 서버 순서는 낙찰과 유찰이 섞여 있어
   * 여기서 낙찰만 앞으로 당긴다. 같은 무리 안에서는 서버 순서를 그대로 둔다
   * (`sort` 는 안정 정렬이다). `PENDING` 은 아래 배지도 유찰로 그리므로 뒤에 둔다.
   *
   * 목록이 세 군데(모바일 결과·데스크톱 왼쪽 목록·데스크톱 전체 결과)라 순서는
   * 여기서 한 번만 정하고 셋이 같이 쓴다.
   */
  const orderedItems = useMemo(
    () =>
      [...items].sort(
        (a, b) => Number(b.outcome === 'SOLD') - Number(a.outcome === 'SOLD'),
      ),
    [items],
  )

  /** 검색은 데스크톱 왼쪽 목록에만 있다. 위 순서를 그대로 이어받는다. */
  const visibleItems = useMemo(() => {
    const trimmed = keyword.trim()
    if (!trimmed) return orderedItems
    return orderedItems.filter((item) => item.productName.includes(trimmed))
  }, [orderedItems, keyword])

  const closedAtLabel = result.closedAt ? formatDate(result.closedAt) : null

  /*
   * 차순위로 넘어온 거래 상대를 잡으려고 내 거래 목록을 대조한다(#178).
   * 게스트는 거래가 있을 수 없으니 아예 부르지 않는다.
   */
  const dealsQuery = useGetDeals({ query: { enabled: !isGuest } })
  const myDealItemIds = useMemo(
    () =>
      new Set(toDeals(dealsQuery.data?.data).map((deal) => deal.auctionItemId)),
    [dealsQuery.data],
  )

  /*
   * `result.unsoldCount`(adapt-result.ts)는 `FAILED` 만 센다 — `/results` 를
   * 라이브 방과도 같이 쓰느라 진행 중(`PENDING`)인 물품을 유찰과 구분해야
   * 해서다. 이 화면은 방이 닫혔을 때만 그려지므로 `PENDING` 은 "시작한 적
   * 없이 끝난 물품" 뿐이라 유찰과 같다. 아래 배지(`won` 판정)도 SOLD 가
   * 아니면 전부 유찰로 그리므로, 요약 숫자도 그 기준(전체 − 낙찰)에 맞춘다.
   */
  const unsoldCount = items.length - result.soldCount

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
              {result.name}
            </h2>
            <p className="mt-2 text-[13px] font-medium text-neutral-tertiary">
              모든 물품의 경매가 종료되었습니다.
            </p>
            {closedAtLabel && (
              <p className="mt-2 text-[11px] font-medium text-neutral-muted">
                종료 {closedAtLabel}
              </p>
            )}
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
                value: `${result.soldCount}건`,
                accent: 'text-result-won',
                bg: 'bg-result-won-surface',
              },
              {
                label: '유찰',
                value: `${unsoldCount}건`,
                accent: 'text-result-failed',
                bg: 'bg-result-failed-surface',
              },
              /*
               * 데스크톱 요약과 같은 칸을 쓴다. 여기만 `내 낙찰` 이었던 탓에,
               * 판매자가 자기 방 결과를 폰으로 열면 총 낙찰액 자리에 늘 `0개` 가
               * 떠서 집계가 안 잡히는 것처럼 보였다.
               */
              {
                label: '총 낙찰액',
                value: formatWon(result.totalAmount),
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
              낙찰 {result.soldCount}건 · 유찰 {unsoldCount}건
            </p>

            <ul className="mt-3 space-y-2">
              {orderedItems.map((item) => {
                const won = item.outcome === 'SOLD'

                return (
                  <li key={item.auctionItemId}>
                    <ResultRow
                      itemId={item.auctionItemId}
                      canLink={
                        isOwner ||
                        item.isMyWin ||
                        myDealItemIds.has(item.auctionItemId)
                      }
                      className={cn(
                        'flex items-center gap-2 rounded-xl border px-3 py-2.5',
                        // 내가 낙찰받은 물품은 한눈에 찾을 수 있어야 한다.
                        item.isMyWin
                          ? 'border-brand-300 bg-brand-50'
                          : 'bg-card',
                      )}
                    >
                      <span className="min-w-0 flex-1">
                        <span className="flex items-center gap-1.5">
                          <span className="min-w-0 truncate text-[13px] font-bold text-foreground">
                            {item.productName}
                          </span>
                          {item.isMyWin && (
                            <span className="shrink-0 rounded-full bg-brand-500 px-1.5 py-0.5 text-[10px] font-extrabold text-white">
                              내 낙찰
                            </span>
                          )}
                        </span>
                        <span className="mt-0.5 block truncate text-[11px] font-medium text-neutral-tertiary">
                          {item.winnerNickname ?? '낙찰자 없음'}
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

                      {/*
                        폭을 84 로 못 박으면 억 단위에서 글자가 칸을 넘어 줄이
                        두 줄로 터진다. 최소 폭만 잡아 자리를 맞추고, 모자라면
                        왼쪽 물품명이 잘리게 둔다.
                      */}
                      <span
                        className={cn(
                          'min-w-[84px] shrink-0 text-right text-[13px] whitespace-nowrap tabular-nums',
                          won
                            ? 'font-bold text-foreground'
                            : 'font-medium text-neutral-muted',
                        )}
                      >
                        {won ? formatWon(item.finalPrice ?? 0) : '—'}
                      </span>
                    </ResultRow>
                  </li>
                )
              })}
            </ul>
          </section>

          <div className="mt-4">
            <RoomDealStatus roomId={result.auctionRoomId} isOwner={isOwner} />
          </div>
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
              {result.name}
            </h1>
            {closedAtLabel && (
              <p className="text-[12px] font-medium text-neutral-tertiary">
                종료 {closedAtLabel}
              </p>
            )}
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
                    const won = item.outcome === 'SOLD'

                    return (
                      <li key={item.auctionItemId}>
                        {/*
                         * 끝난 물품은 그 물품의 거래로 보낸다. 이어지는 거래가
                         * 없으면 아무 데도 보내지 않는다. 예전에는 라이브 물품
                         * 상세로 떨어져서, 종료된 방인데 진행 중 화면이 떴다.
                         */}
                        {/* 누르면 그 물품의 거래 상세로 간다. */}
                        <ResultRow
                          itemId={item.auctionItemId}
                          canLink={
                            isOwner ||
                            item.isMyWin ||
                            myDealItemIds.has(item.auctionItemId)
                          }
                          className={cn(
                            'flex gap-3 rounded-2xl border p-3',
                            item.isMyWin
                              ? 'border-brand-300 bg-brand-50'
                              : 'bg-card',
                          )}
                        >
                          <ProductThumbnail
                            src={item.imageUrl}
                            iconClassName="size-5"
                            className="flex h-[84px] w-[72px] shrink-0 flex-col items-center justify-center gap-1 rounded-xl bg-fill text-neutral-muted"
                          />
                          <span className="flex min-w-0 flex-1 flex-col">
                            <span
                              className={cn(
                                'flex h-5 w-11 items-center justify-center rounded-full text-[10px] font-bold',
                                won
                                  ? 'bg-result-won-surface text-result-won'
                                  : 'bg-result-failed-surface text-result-failed',
                              )}
                            >
                              {won ? '낙찰' : '유찰'}
                            </span>
                            <span className="mt-1.5 block truncate text-[14px] font-bold text-foreground">
                              {item.productName}
                            </span>
                            <span className="mt-1 block text-[11px] font-medium text-neutral-tertiary">
                              {item.winnerNickname ?? '입찰자 없음'}
                            </span>
                            <span
                              className={cn(
                                'mt-auto text-right text-[15px] tabular-nums',
                                won
                                  ? 'font-bold text-foreground'
                                  : 'font-medium text-neutral-muted',
                              )}
                            >
                              {won ? formatWon(item.finalPrice ?? 0) : '—'}
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

              {/*
                왼쪽 목록과 같은 높이를 쓰도록 남는 공간을 채운다.
                다만 화면이 낮으면 요약·결과·거래 현황 셋이 다 들어가지 못한다.
                그때는 열을 통째로 스크롤한다 — 그냥 두면 바깥 `overflow-hidden`
                에 걸려 결과표의 컬럼 머리글부터 아래가 잘려 나간다.
              */}
              <div className="flex flex-col gap-3 lg:min-h-0 lg:flex-1 lg:overflow-y-auto">
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
                        value: `${result.soldCount}건`,
                        bg: 'bg-result-won-surface',
                        color: 'text-result-won',
                      },
                      {
                        label: '유찰',
                        value: `${unsoldCount}건`,
                        bg: 'bg-result-failed-surface',
                        color: 'text-result-failed',
                      },
                      {
                        label: '총 낙찰액',
                        value: formatWon(result.totalAmount),
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

                {/*
                  머리글(40) + 두 줄은 항상 보이게 최소 높이를 준다. `flex-1` 만
                  두면 남는 높이가 모자랄 때 카드가 제 내용보다 작게 눌린다.
                */}
                <div className="flex flex-col rounded-[20px] border bg-card p-4 lg:min-h-[260px] lg:flex-1">
                  <h3 className="shrink-0 text-[18px] font-bold text-foreground">
                    전체 물품 결과
                  </h3>
                  <p className="mt-1 shrink-0 text-[12px] font-medium text-neutral-tertiary">
                    낙찰 {result.soldCount}건 · 유찰 {unsoldCount}건
                  </p>

                  {/*
                    머리글을 스크롤 영역 밖에 두면 스크롤바 폭만큼 줄과 어긋나
                    오른쪽 끝(낙찰가)이 밀려 잘린다. 같은 통 안에 넣고 sticky 로
                    붙여 두면 줄과 정확히 같은 폭을 쓴다.
                  */}
                  <div className="mt-3 min-h-0 flex-1 overflow-y-auto pr-0.5">
                    <div className="sticky top-0 z-10 bg-card pb-2">
                      <div className="flex h-10 items-center rounded-lg bg-surface-subtle px-4 text-[11px] font-semibold text-neutral-tertiary">
                        <span className="min-w-0 flex-1">물품</span>
                        <span className="hidden w-[120px] shrink-0 sm:block">
                          낙찰자
                        </span>
                        <span className="w-[68px] shrink-0 text-center">
                          결과
                        </span>
                        <span className="w-[104px] shrink-0 text-right sm:w-[116px]">
                          낙찰가
                        </span>
                      </div>
                    </div>

                    <ul className="space-y-2">
                      {orderedItems.map((item) => {
                        const won = item.outcome === 'SOLD'

                        return (
                          <li key={item.auctionItemId}>
                            <ResultRow
                              itemId={item.auctionItemId}
                              canLink={
                                isOwner ||
                                item.isMyWin ||
                                myDealItemIds.has(item.auctionItemId)
                              }
                              className="flex h-14 items-center rounded-[10px] border bg-card px-4 text-[13px]"
                            >
                              <span className="min-w-0 flex-1 truncate font-medium text-foreground">
                                {item.productName}
                              </span>

                              <span className="hidden w-[120px] shrink-0 truncate font-medium text-neutral-tertiary sm:block">
                                {item.winnerNickname ?? '—'}
                              </span>

                              {/* 낙찰·유찰은 색이 아니라 배지로 구분한다. 훑어볼 때 이게 제일 빠르다. */}
                              <span className="flex w-[68px] shrink-0 justify-center">
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

                              {/*
                                104 는 `9,999,999,999원`(14자)이 한 줄에 들어가는
                                최소치(108)에 여유를 더한 값이다. 예전 92/104 에서는
                                10억대부터 칸 안에서 두 줄로 접혀 행 높이가 어긋났다.
                              */}
                              <span
                                className={cn(
                                  'w-[104px] shrink-0 text-right whitespace-nowrap tabular-nums sm:w-[116px]',
                                  won
                                    ? 'font-bold text-foreground'
                                    : 'font-medium text-neutral-muted',
                                )}
                              >
                                {won ? formatWon(item.finalPrice ?? 0) : '—'}
                              </span>
                            </ResultRow>
                          </li>
                        )
                      })}
                    </ul>
                  </div>
                </div>

                <div className="shrink-0">
                  <RoomDealStatus
                    roomId={result.auctionRoomId}
                    isOwner={isOwner}
                  />
                </div>
              </div>
            </section>
          </div>
        </div>
      </div>
    </>
  )
}
