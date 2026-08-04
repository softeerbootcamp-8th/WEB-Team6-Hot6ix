import { createFileRoute, Link } from '@tanstack/react-router'

import { GuestShell } from '@/components/layout/page-shell'
import { EmptyState } from '@/components/page-header'
import { RouteError, RoutePending } from '@/components/route-states'
import { useGetResults } from '@/api/generated/경매방/경매방'
import { toRoomResult } from '@/features/live/adapt-result'
import { StatusBadge } from '@/components/status-badge'
import { formatWon } from '@/lib/format'

export const Route = createFileRoute('/rooms/$roomId/result')({
  component: RoomResultPage,
})

/** 이 화면은 종료된 방 전용이 아니다 — 진행 중인 방 주소로도 열릴 수 있다. */
const ROOM_BADGE_LABEL = {
  READY: '오픈 예정 경매방',
  LIVE: '진행 중인 경매방',
  CLOSED: '종료된 경매방',
} as const

function RoomResultPage() {
  const { roomId } = Route.useParams()
  const auctionRoomId = Number(roomId)

  // `/rooms/abc/result` 처럼 숫자가 아닌 주소로 들어오면 서버를 부르지 않는다.
  const resultsQuery = useGetResults(auctionRoomId, {
    query: { enabled: Number.isInteger(auctionRoomId) },
  })

  if (resultsQuery.isPending) return <RoutePending />
  if (resultsQuery.isError) {
    return (
      <RouteError
        error={resultsQuery.error}
        reset={() => void resultsQuery.refetch()}
      />
    )
  }

  const result = toRoomResult(resultsQuery.data?.data)
  if (!result) {
    return (
      <GuestShell title="경매 결과" back className="max-w-[1000px]">
        <EmptyState
          title="결과를 찾을 수 없어요"
          description="삭제되었거나 존재하지 않는 경매방입니다."
        />
      </GuestShell>
    )
  }

  return (
    <GuestShell title="경매 결과" back className="max-w-[1000px]">
      <header className="rounded-4xl border bg-card p-5 md:p-6">
        <StatusBadge tone="muted">
          {ROOM_BADGE_LABEL[result.status]}
        </StatusBadge>
        <h1 className="mt-3 text-[22px] font-extrabold text-foreground md:text-[26px]">
          {result.name}
        </h1>
        {result.sellerStoreName && (
          <p className="mt-2 text-body font-medium text-neutral-tertiary">
            {result.sellerStoreName}
          </p>
        )}

        <dl className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-4">
          {[
            { label: '전체 물품', value: `${result.items.length}개` },
            { label: '낙찰', value: `${result.soldCount}개` },
            { label: '유찰', value: `${result.unsoldCount}개` },
            { label: '총 낙찰가', value: formatWon(result.totalAmount) },
          ].map((stat) => (
            <div key={stat.label} className="rounded-2xl bg-surface-subtle p-4">
              <dt className="text-caption font-normal text-neutral-muted">
                {stat.label}
              </dt>
              <dd className="mt-1.5 text-body-strong font-semibold tabular-nums text-foreground">
                {stat.value}
              </dd>
            </div>
          ))}
        </dl>

        {result.myWinCount > 0 && (
          <p className="mt-4 rounded-2xl bg-success-surface px-4 py-3 text-label font-bold text-success">
            이 방에서 {result.myWinCount}개 물품을 낙찰받았어요. 거래 내역에서
            진행 상황을 확인하세요.
          </p>
        )}
      </header>

      <section className="mt-6">
        <h2 className="text-label font-bold text-foreground">
          물품별 결과 ({result.items.length})
        </h2>

        <ul className="mt-3 space-y-3">
          {result.items.map((item) => (
            <li
              key={item.auctionItemId}
              className="rounded-3xl border bg-card p-5"
            >
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-1.5">
                    {item.outcome === 'SOLD' ? (
                      <StatusBadge tone="success">낙찰</StatusBadge>
                    ) : item.outcome === 'FAILED' ? (
                      <StatusBadge tone="muted">유찰</StatusBadge>
                    ) : (
                      <StatusBadge tone="notice">진행 중</StatusBadge>
                    )}
                    {item.isMyWin && (
                      <StatusBadge tone="brand">내 낙찰</StatusBadge>
                    )}
                  </div>
                  <h3 className="mt-2.5 text-card-title font-bold text-foreground">
                    {item.productName}
                  </h3>
                </div>

                <div className="text-right">
                  <p className="text-price font-extrabold tabular-nums text-foreground">
                    {item.outcome === 'SOLD'
                      ? formatWon(item.finalPrice ?? 0)
                      : '-'}
                  </p>
                  <p className="mt-1 text-caption font-normal text-neutral-tertiary">
                    {item.outcome === 'SOLD'
                      ? `${item.winnerNickname ?? '알 수 없음'} 낙찰`
                      : item.outcome === 'FAILED'
                        ? '입찰자 없음'
                        : '경매 진행 중'}
                  </p>
                </div>
              </div>

              {item.myRank != null && (
                <p className="mt-4 rounded-xl bg-surface-subtle px-4 py-2.5 text-caption font-normal text-neutral-secondary">
                  내 최종 순위 {item.myRank}위 · {formatWon(item.myAmount ?? 0)}
                </p>
              )}

              <Link
                to="/rooms/$roomId/items/$itemId"
                params={{ roomId, itemId: String(item.auctionItemId) }}
                className="mt-4 block rounded-lg border py-2 text-center text-label font-semibold text-neutral-secondary transition-colors hover:border-border-strong"
              >
                상세 결과 보기
              </Link>
            </li>
          ))}
        </ul>
      </section>
    </GuestShell>
  )
}
