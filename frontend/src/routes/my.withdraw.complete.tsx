import { Check } from 'lucide-react'
import { createFileRoute, Link } from '@tanstack/react-router'

import { GuestShell } from '@/components/layout/page-shell'

/** 탈퇴 후에는 세션이 없으므로 게스트 골격을 쓴다. */
export const Route = createFileRoute('/my/withdraw/complete')({
  component: WithdrawCompletePage,
})

function WithdrawCompletePage() {
  return (
    <GuestShell title="탈퇴 완료" state="탈퇴 완료" className="max-w-[608px]">
      <section className="rounded-4xl border bg-card p-6 text-center md:p-10">
        <span
          aria-hidden
          className="mx-auto flex size-20 items-center justify-center rounded-full bg-success-surface"
        >
          <Check className="size-9 text-success" strokeWidth={3} />
        </span>

        <h1 className="mt-7 text-[22px] font-extrabold text-foreground md:text-[24px]">
          탈퇴가 완료됐어요
        </h1>
        <p className="mt-3 text-body font-medium text-neutral-tertiary">
          그동안 UpBid를 이용해주셔서 감사합니다.
        </p>

        <p className="mt-8 rounded-2xl bg-surface-subtle px-5 py-4 text-label font-medium text-neutral-secondary">
          연결된 카카오 계정이 해제됐어요. 언제든 다시 가입할 수 있습니다.
        </p>

        <Link
          to="/"
          className="mt-8 block rounded-2xl bg-primary py-4 text-card-title font-bold text-primary-foreground transition-opacity hover:opacity-90"
        >
          처음으로
        </Link>
      </section>
    </GuestShell>
  )
}
