import { AlertTriangle } from 'lucide-react'
import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useState } from 'react'

import { AppShell } from '@/components/layout/page-shell'
import { Button } from '@/components/ui/button'
import { MOCK_TRADES } from '@/mocks/data'
import { requireMember } from '@/lib/route-guards'
import { sessionStore } from '@/lib/session'

export const Route = createFileRoute('/my/withdraw/')({
  beforeLoad: requireMember,
  component: WithdrawConfirmPage,
})

const NOTICES = [
  '참여한 경매와 입찰 이력은 복구할 수 없어요.',
  '진행 중인 거래가 있으면 상대방에게 연락이 닿지 않을 수 있어요.',
  '같은 카카오 계정으로 다시 가입할 수 있지만 이전 기록은 이어지지 않아요.',
]

function WithdrawConfirmPage() {
  const navigate = useNavigate()
  const [agreed, setAgreed] = useState(false)
  const [pending, setPending] = useState(false)

  const openTrades = MOCK_TRADES.filter(
    (trade) => trade.status === 'IN_PROGRESS',
  )

  const withdraw = () => {
    setPending(true)
    // TODO: DELETE /api/v1/users/me 연동 (현재 목업)
    window.setTimeout(() => {
      sessionStore.signOut()
      void navigate({ to: '/my/withdraw/complete' })
    }, 700)
  }

  return (
    <AppShell title="회원 탈퇴" back className="max-w-[608px]">
      <section className="rounded-4xl border bg-card p-6 md:p-8">
        <span
          aria-hidden
          className="flex size-16 items-center justify-center rounded-full bg-live-surface"
        >
          <AlertTriangle className="size-8 text-live" />
        </span>

        <h1 className="mt-6 text-[22px] font-extrabold text-foreground md:text-[24px]">
          정말 탈퇴하시겠어요?
        </h1>
        <p className="mt-3 text-body font-medium text-neutral-tertiary">
          탈퇴하면 계정과 관련된 정보가 삭제되고 되돌릴 수 없어요.
        </p>

        {openTrades.length > 0 && (
          <div className="mt-6 rounded-2xl bg-notice-surface px-4 py-3.5">
            <p className="text-label font-bold text-notice">
              진행 중인 거래가 {openTrades.length}건 있어요
            </p>
            <p className="mt-1.5 text-caption font-normal text-notice/80">
              탈퇴 전에 거래를 마무리하는 게 좋아요.
            </p>
            <Link
              to="/trades"
              className="mt-2 inline-block text-caption font-bold text-notice hover:underline"
            >
              거래 내역 확인하기 →
            </Link>
          </div>
        )}

        <ul className="mt-6 space-y-2.5 rounded-2xl bg-surface-subtle p-5">
          {NOTICES.map((notice) => (
            <li
              key={notice}
              className="flex gap-2 text-label font-medium text-neutral-secondary"
            >
              <span aria-hidden className="text-neutral-muted">
                ·
              </span>
              {notice}
            </li>
          ))}
        </ul>

        <label className="mt-6 flex items-start gap-3">
          <input
            type="checkbox"
            checked={agreed}
            onChange={(event) => setAgreed(event.target.checked)}
            className="mt-0.5 size-4 shrink-0 accent-brand-500"
          />
          <span className="text-label font-medium text-neutral-secondary">
            위 내용을 확인했고 탈퇴에 동의합니다
          </span>
        </label>

        <div className="mt-6 flex flex-col gap-2 sm:flex-row">
          <Link
            to="/my"
            className="flex h-12 flex-1 items-center justify-center rounded-xl border text-label font-semibold text-neutral-secondary transition-colors hover:border-border-strong"
          >
            돌아가기
          </Link>
          <Button
            variant="destructive"
            disabled={!agreed || pending}
            onClick={withdraw}
            className="h-12 flex-1 rounded-xl text-label font-bold"
          >
            {pending ? '처리 중…' : '탈퇴하기'}
          </Button>
        </div>
      </section>
    </AppShell>
  )
}
