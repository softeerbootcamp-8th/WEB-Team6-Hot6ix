import { createFileRoute, Link } from '@tanstack/react-router'

import { GuestShell } from '@/components/layout/page-shell'
import { MOCK_ROOM_DETAIL } from '@/mocks/data'
import { StatusBadge } from '@/components/status-badge'
import { formatWon } from '@/lib/format'

export const Route = createFileRoute('/rooms/$roomId/result')({
  component: RoomResultPage,
})

function RoomResultPage() {
  const { roomId } = Route.useParams()
  const room = MOCK_ROOM_DETAIL

  const results = room.items.map((item) => {
    const winner = item.leaderboard[0] ?? null
    const mine = item.leaderboard.find((entry) => entry.isMe) ?? null
    return { item, winner, mine }
  })

  const soldCount = results.filter((result) => result.winner).length
  const myWinCount = results.filter(
    (result) => result.mine && result.mine.rank === 1,
  ).length
  const total = results.reduce(
    (sum, result) => sum + (result.winner?.amount ?? 0),
    0,
  )

  return (
    <GuestShell title="경매 결과" back className="max-w-[1000px]">
      <header className="rounded-4xl border bg-card p-5 md:p-6">
        <StatusBadge tone="muted">종료된 경매방</StatusBadge>
        <h1 className="mt-3 text-[22px] font-extrabold text-foreground md:text-[26px]">
          {room.title}
        </h1>
        <p className="mt-2 text-body font-medium text-neutral-tertiary">
          {room.sellerName} · 참여 {room.participantCount}명
        </p>

        <dl className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-4">
          {[
            { label: '전체 물품', value: `${room.items.length}개` },
            { label: '낙찰', value: `${soldCount}개` },
            { label: '유찰', value: `${room.items.length - soldCount}개` },
            { label: '총 낙찰가', value: formatWon(total) },
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

        {myWinCount > 0 && (
          <p className="mt-4 rounded-2xl bg-success-surface px-4 py-3 text-label font-bold text-success">
            이 방에서 {myWinCount}개 물품을 낙찰받았어요. 거래 내역에서 진행
            상황을 확인하세요.
          </p>
        )}
      </header>

      <section className="mt-6">
        <h2 className="text-label font-bold text-foreground">
          물품별 결과 ({results.length})
        </h2>

        <ul className="mt-3 space-y-3">
          {results.map(({ item, winner, mine }) => (
            <li key={item.id} className="rounded-3xl border bg-card p-5">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-1.5">
                    {winner ? (
                      <StatusBadge tone="success">낙찰</StatusBadge>
                    ) : (
                      <StatusBadge tone="muted">유찰</StatusBadge>
                    )}
                    {mine?.rank === 1 && (
                      <StatusBadge tone="brand">내 낙찰</StatusBadge>
                    )}
                  </div>
                  <h3 className="mt-2.5 text-card-title font-bold text-foreground">
                    {item.name}
                  </h3>
                  <p className="mt-1 text-caption font-normal text-neutral-muted">
                    {item.category} · 입찰 {item.bidCount}회
                  </p>
                </div>

                <div className="text-right">
                  <p className="text-price font-extrabold tabular-nums text-foreground">
                    {winner ? formatWon(winner.amount) : '-'}
                  </p>
                  <p className="mt-1 text-caption font-normal text-neutral-tertiary">
                    {winner ? `${winner.nickname} 낙찰` : '입찰자 없음'}
                  </p>
                </div>
              </div>

              {mine && (
                <p className="mt-4 rounded-xl bg-surface-subtle px-4 py-2.5 text-caption font-normal text-neutral-secondary">
                  내 최종 순위 {mine.rank}위 · {formatWon(mine.amount)}
                </p>
              )}

              <Link
                to="/rooms/$roomId/items/$itemId"
                params={{ roomId, itemId: String(item.id) }}
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
