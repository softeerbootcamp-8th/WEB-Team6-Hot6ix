import { AlertTriangle, ImageIcon, Phone } from 'lucide-react'
import { createFileRoute } from '@tanstack/react-router'
import { useState } from 'react'

import { AppShell } from '@/components/layout/page-shell'
import { Button } from '@/components/ui/button'
import { EmptyState, PageHeader } from '@/components/page-header'
import { MOCK_CANDIDATES, MOCK_ROOM_DETAIL, MOCK_TRADES } from '@/mocks/data'
import { Modal } from '@/components/ui/modal'
import { StatusBadge, type BadgeTone } from '@/components/status-badge'
import { cn } from '@/lib/utils'
import { formatDate, formatWon } from '@/lib/format'
import { requireMember } from '@/lib/route-guards'
import type { CandidateStatus, DealCandidate } from '@/mocks/types'

/**
 * 거래 상세.
 *
 * 물품 단위이고, 보는 사람이 구매자냐 판매자냐에 따라 내용이 갈린다.
 * 판매자는 낙찰 후보를 성사/실패 처리하고 실패 시 차순위로 넘어간다.
 */
export const Route = createFileRoute('/trades/$itemId')({
  beforeLoad: requireMember,
  component: TradeDetailPage,
})

const CANDIDATE_META: Record<
  CandidateStatus,
  { label: string; tone: BadgeTone }
> = {
  IN_PROGRESS: { label: '거래 진행 중', tone: 'brand' },
  COMPLETED: { label: '거래 성사', tone: 'success' },
  FAILED: { label: '거래 실패', tone: 'live' },
  WAITING: { label: '대기', tone: 'muted' },
}

function TradeDetailPage() {
  const { itemId } = Route.useParams()

  const trade = MOCK_TRADES.find(
    (candidate) => String(candidate.auctionItemId) === itemId,
  )
  const item = MOCK_ROOM_DETAIL.items.find(
    (candidate) => String(candidate.id) === itemId,
  )

  const [candidates, setCandidates] = useState<DealCandidate[]>(MOCK_CANDIDATES)
  const [pendingAction, setPendingAction] = useState<
    'complete' | 'fail' | null
  >(null)

  if (!trade) {
    return (
      <AppShell title="거래 상세" back>
        <EmptyState
          title="거래를 찾을 수 없어요"
          description="삭제되었거나 접근할 수 없는 거래입니다."
        />
      </AppShell>
    )
  }

  const isSeller = trade.role === 'SELLER'
  const unsold = trade.status === 'UNSOLD'
  const current = candidates.find((c) => c.status === 'IN_PROGRESS') ?? null

  const applyAction = (action: 'complete' | 'fail') => {
    if (!current) return

    // TODO: POST .../deal-candidates/{id}/{complete|fail} 연동 (현재 목업)
    setCandidates((prev) => {
      const index = prev.findIndex((c) => c.id === current.id)
      return prev.map((candidate, position) => {
        if (position === index) {
          return {
            ...candidate,
            status: action === 'complete' ? 'COMPLETED' : 'FAILED',
          }
        }
        // 실패하면 바로 다음 후보가 거래 진행 중이 된다.
        if (action === 'fail' && position === index + 1) {
          return { ...candidate, status: 'IN_PROGRESS' }
        }
        return candidate
      })
    })
    setPendingAction(null)
  }

  return (
    <AppShell title="거래 상세" back className="max-w-[1000px]">
      <PageHeader
        title="거래 상세"
        description={
          isSeller
            ? '낙찰 후보와 거래를 진행하고 결과를 기록하세요.'
            : '최종 순위와 판매자 연락처를 확인할 수 있어요.'
        }
      />

      <div className="mt-6 grid gap-4 lg:grid-cols-[minmax(0,340px)_minmax(0,1fr)]">
        {/* 물품 정보 */}
        <section className="rounded-4xl border bg-card p-5 md:p-6">
          <div className="flex flex-wrap items-center gap-1.5">
            <StatusBadge tone={isSeller ? 'brand' : 'neutral'}>
              {isSeller ? '판매' : '구매'}
            </StatusBadge>
            {unsold ? (
              <StatusBadge tone="muted">유찰</StatusBadge>
            ) : (
              <StatusBadge tone="success">낙찰</StatusBadge>
            )}
          </div>

          <span
            aria-hidden
            className="mt-4 flex h-[180px] items-center justify-center rounded-2xl bg-fill text-neutral-muted"
          >
            <ImageIcon className="size-7" />
          </span>

          <h2 className="mt-4 text-room-title font-bold text-foreground">
            {trade.productName}
          </h2>
          <p className="mt-1.5 text-caption font-normal text-neutral-tertiary">
            {trade.roomTitle} · {trade.category}
          </p>

          <dl className="mt-5 space-y-3 border-t pt-5">
            <div className="flex items-baseline justify-between">
              <dt className="text-label font-bold text-neutral-secondary">
                {unsold ? '시작가' : '낙찰가'}
              </dt>
              <dd className="text-price font-extrabold tabular-nums text-foreground">
                {unsold
                  ? item
                    ? formatWon(item.startPrice)
                    : '-'
                  : formatWon(trade.amount)}
              </dd>
            </div>
            <div className="flex items-baseline justify-between">
              <dt className="text-label font-bold text-neutral-secondary">
                마감
              </dt>
              <dd className="text-body font-medium text-neutral-tertiary">
                {formatDate(trade.closedAt)}
              </dd>
            </div>
          </dl>

          {!isSeller && !unsold && (
            <div className="mt-5 rounded-2xl bg-surface-subtle p-4">
              <p className="text-caption font-semibold text-neutral-secondary">
                판매자 연락처
              </p>
              <p className="mt-2 text-body-strong font-semibold text-foreground">
                {trade.partnerNickname}
              </p>
              <a
                href={`tel:${trade.partnerPhone}`}
                className="mt-1 flex items-center gap-1.5 text-label font-bold text-brand-500 hover:underline"
              >
                <Phone aria-hidden className="size-3.5" />
                {trade.partnerPhone}
              </a>
              <p className="mt-3 text-caption font-normal text-neutral-muted">
                낙찰자와 판매자에게만 공개됩니다.
              </p>
            </div>
          )}
        </section>

        {/* 순위 / 낙찰 후보 */}
        <section className="rounded-4xl border bg-card p-5 md:p-6">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h2 className="text-card-title font-bold text-foreground">
              {isSeller ? '낙찰 후보' : '최종 순위'}
            </h2>
            <p className="text-caption font-normal text-neutral-muted">
              {isSeller
                ? '1순위와 거래가 안 되면 차순위로 넘어가요.'
                : '입찰 금액이 높은 순서예요.'}
            </p>
          </div>

          {unsold ? (
            <div className="mt-6 flex flex-col items-center rounded-3xl bg-surface-subtle px-6 py-14 text-center">
              <span
                aria-hidden
                className="flex size-16 items-center justify-center rounded-full bg-card text-neutral-muted"
              >
                <AlertTriangle className="size-7" />
              </span>
              <h3 className="mt-5 text-card-title font-bold text-foreground">
                낙찰 후보가 없어요
              </h3>
              <p className="mt-2 text-label font-medium text-neutral-tertiary">
                유효 입찰이 없어 유찰됐습니다. 상품을 다시 경매에 올릴 수
                있어요.
              </p>
            </div>
          ) : isSeller ? (
            <>
              <ul className="mt-5 divide-y">
                {candidates.map((candidate) => {
                  const meta = CANDIDATE_META[candidate.status]
                  const active = candidate.status === 'IN_PROGRESS'

                  return (
                    <li
                      key={candidate.id}
                      className={cn(
                        'flex flex-wrap items-center gap-3 py-4',
                        active && '-mx-3 rounded-2xl bg-brand-50 px-3',
                      )}
                    >
                      <span
                        className={cn(
                          'flex size-7 shrink-0 items-center justify-center rounded-md text-caption font-normal',
                          candidate.rank === 1
                            ? 'bg-brand-500 text-primary-foreground'
                            : 'bg-fill text-neutral-secondary',
                        )}
                      >
                        {candidate.rank}
                      </span>

                      <div className="min-w-0 flex-1">
                        <p className="truncate text-label font-bold text-foreground">
                          {candidate.nickname}
                        </p>
                        {active || candidate.status === 'COMPLETED' ? (
                          <a
                            href={`tel:${candidate.phone}`}
                            className="mt-0.5 flex items-center gap-1 text-caption font-normal text-brand-500 hover:underline"
                          >
                            <Phone aria-hidden className="size-3" />
                            {candidate.phone}
                          </a>
                        ) : (
                          <p className="mt-0.5 text-caption font-normal text-neutral-muted">
                            차례가 되면 연락처가 열려요
                          </p>
                        )}
                      </div>

                      <p className="text-body-strong font-semibold tabular-nums text-foreground">
                        {formatWon(candidate.amount)}
                      </p>

                      <StatusBadge tone={meta.tone} className="shrink-0">
                        {meta.label}
                      </StatusBadge>
                    </li>
                  )
                })}
              </ul>

              {current ? (
                <div className="mt-6 flex flex-col gap-2 border-t pt-6 sm:flex-row">
                  <Button
                    variant="outline"
                    className="h-12 flex-1 rounded-xl text-live hover:bg-live-surface hover:text-live"
                    onClick={() => setPendingAction('fail')}
                  >
                    거래 실패 · 차순위로
                  </Button>
                  <Button
                    className="h-12 flex-1 rounded-xl"
                    onClick={() => setPendingAction('complete')}
                  >
                    거래 성사 확정
                  </Button>
                </div>
              ) : (
                <p className="mt-6 rounded-2xl bg-surface-subtle px-4 py-4 text-center text-label font-medium text-neutral-tertiary">
                  {candidates.some((c) => c.status === 'COMPLETED')
                    ? '거래가 완료된 물품이에요.'
                    : '남은 후보가 없어요. 상품을 다시 경매에 올릴 수 있어요.'}
                </p>
              )}
            </>
          ) : (
            <ul className="mt-5 divide-y">
              {candidates.map((candidate) => (
                <li
                  key={candidate.id}
                  className="flex items-center gap-3 py-3.5"
                >
                  <span
                    className={cn(
                      'flex size-7 shrink-0 items-center justify-center rounded-md text-caption font-normal',
                      candidate.rank === 1
                        ? 'bg-brand-500 text-primary-foreground'
                        : 'bg-fill text-neutral-secondary',
                    )}
                  >
                    {candidate.rank}
                  </span>
                  <span className="min-w-0 flex-1 truncate text-label font-medium text-neutral-secondary">
                    {candidate.nickname}
                  </span>
                  <span className="text-body-strong font-semibold tabular-nums text-foreground">
                    {formatWon(candidate.amount)}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <Modal
        open={pendingAction !== null}
        onClose={() => setPendingAction(null)}
        labelledBy="deal-action-title"
      >
        <h3
          id="deal-action-title"
          className="text-room-title font-bold text-foreground"
        >
          {pendingAction === 'complete'
            ? '거래를 성사로 확정할까요?'
            : '거래를 실패로 처리할까요?'}
        </h3>
        <p className="mt-3 text-body font-medium text-neutral-tertiary">
          {pendingAction === 'complete'
            ? `${current?.nickname} 님과의 거래가 완료된 것으로 기록됩니다. 되돌릴 수 없어요.`
            : `${current?.nickname} 님과의 거래가 실패로 기록되고, 차순위 후보에게 기회가 넘어갑니다.`}
        </p>
        <div className="mt-6 flex gap-2">
          <Button
            variant="outline"
            className="h-12 flex-1 rounded-xl"
            onClick={() => setPendingAction(null)}
          >
            취소
          </Button>
          <Button
            className="h-12 flex-1 rounded-xl"
            onClick={() => pendingAction && applyAction(pendingAction)}
          >
            확인
          </Button>
        </div>
      </Modal>
    </AppShell>
  )
}
