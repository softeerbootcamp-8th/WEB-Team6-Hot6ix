import { Link } from '@tanstack/react-router'

import { useGetRoomDeals } from '@/api/generated/경매방-거래-현황/경매방-거래-현황'
import {
  DEAL_STATUS_LABEL,
  DEAL_STATUS_TONE,
} from '@/features/trades/deal-status'
import { cn } from '@/lib/utils'
import { formatWon } from '@/lib/format'

/**
 * 판매자 전용 거래 현황. `ClosedRoomView` 안에 얹는다.
 *
 * 판매자가 아니면 서버가 404 를 준다. 재시도해도 바뀌지 않으니
 * `retry: false` 로 두고, 실패하면 섹션 자체를 조용히 감춘다 — 화면 제어가
 * 권한을 대신하지 않는다(루트 CLAUDE.md), 서버가 이미 판정했다.
 *
 * 거래 성사·실패 버튼은 여기 없다. `trades.$itemId.tsx` 가 이미 그 뮤테이션과
 * 무효화를 갖고 있어, 여기서 복제하면 캐시가 두 곳에서 따로 갱신된다.
 */
export function RoomDealStatus({
  roomId,
  isOwner,
}: {
  roomId: number
  isOwner: boolean
}) {
  const dealsQuery = useGetRoomDeals(roomId, {
    query: { enabled: isOwner && Number.isInteger(roomId), retry: false },
  })

  if (!isOwner || dealsQuery.isError) return null

  if (dealsQuery.isPending) {
    return (
      <section className="rounded-[20px] border bg-card p-4">
        <div className="h-5 w-20 animate-skeleton rounded-lg bg-fill" />
        <div className="mt-3 space-y-2">
          {[0, 1, 2].map((row) => (
            <div
              key={row}
              className="h-14 animate-skeleton rounded-2xl bg-fill"
            />
          ))}
        </div>
      </section>
    )
  }

  const items = (dealsQuery.data?.data?.items ?? []).flatMap((item) => {
    if (item.auctionItemId == null || item.dealStatus == null) return []
    return [
      {
        auctionItemId: item.auctionItemId,
        productName: item.productName ?? '이름 없는 물품',
        dealStatus: item.dealStatus,
        amount: item.amount ?? null,
        partnerNickname: item.partnerNickname ?? null,
        candidateCount: item.candidateCount ?? 0,
        failedCandidateCount: item.failedCandidateCount ?? 0,
      },
    ]
  })

  if (items.length === 0) return null

  return (
    <section className="rounded-[20px] border bg-card p-4">
      <h3 className="text-[16px] font-bold text-foreground">거래 현황</h3>
      <p className="mt-1 text-[12px] font-medium text-neutral-tertiary">
        낙찰된 물품의 거래 진행 상태입니다.
      </p>

      <ul className="mt-3 space-y-2">
        {items.map((item) => {
          const tone = DEAL_STATUS_TONE[item.dealStatus]

          return (
            <li key={item.auctionItemId}>
              <Link
                to="/trades/$itemId"
                params={{ itemId: String(item.auctionItemId) }}
                className="ease-soft flex items-center gap-3 rounded-2xl border p-3 transition-all duration-150 hover:border-brand-300 active:scale-[0.99]"
              >
                <span className="min-w-0 flex-1">
                  <span className="flex items-center gap-1.5">
                    <span className="min-w-0 truncate text-[13px] font-bold text-foreground">
                      {item.productName}
                    </span>
                    <span
                      className={cn(
                        'shrink-0 rounded-full px-2 py-0.5 text-[10px] font-bold',
                        tone.chip,
                        tone.text,
                      )}
                    >
                      {DEAL_STATUS_LABEL[item.dealStatus]}
                    </span>
                  </span>
                  <span className="mt-0.5 block truncate text-[11px] font-medium text-neutral-tertiary">
                    {item.partnerNickname ?? '상대 없음'} · 후보{' '}
                    {item.candidateCount}명
                    {item.failedCandidateCount > 0 &&
                      ` (실패 ${item.failedCandidateCount})`}
                  </span>
                </span>

                <span className="shrink-0 text-[13px] font-bold tabular-nums text-foreground">
                  {item.amount != null ? formatWon(item.amount) : '—'}
                </span>
              </Link>
            </li>
          )
        })}
      </ul>
    </section>
  )
}
