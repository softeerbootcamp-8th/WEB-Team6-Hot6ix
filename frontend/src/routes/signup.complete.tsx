import { Check } from 'lucide-react'
import { createFileRoute, useNavigate } from '@tanstack/react-router'

import { Button } from '@/components/ui/button'
import { GuestShell } from '@/components/layout/page-shell'
import { MOCK_MEMBER, sessionStore } from '@/lib/session'

export const Route = createFileRoute('/signup/complete')({
  validateSearch: (search: Record<string, unknown>): { redirect?: string } =>
    typeof search.redirect === 'string' ? { redirect: search.redirect } : {},
  component: SignupCompletePage,
})

function SignupCompletePage() {
  const navigate = useNavigate()
  const { redirect } = Route.useSearch()

  const handleStart = () => {
    // TODO: 실제로는 가입 응답으로 세션이 만들어진다 (현재 목업)
    sessionStore.signIn(MOCK_MEMBER)
    void navigate({ href: redirect ?? '/rooms' })
  }

  return (
    <GuestShell
      state="가입 완료"
      title="회원가입 완료"
      className="max-w-[608px] px-5 py-6 md:py-10"
    >
      <section className="rounded-4xl border bg-card p-6 text-center md:p-10">
        <span
          aria-hidden
          className="mx-auto flex size-20 items-center justify-center rounded-full bg-success-surface"
        >
          <Check className="size-9 text-success" strokeWidth={3} />
        </span>

        <h1 className="mt-7 text-[22px] font-extrabold text-foreground md:text-[24px]">
          가입이 완료됐어요
        </h1>
        <p className="mt-3 text-body font-medium text-neutral-tertiary">
          카카오 연결과 전화번호 인증이 모두 완료됐습니다.
        </p>

        <dl className="mt-8 divide-y rounded-3xl bg-surface-subtle text-left">
          <div className="flex items-center justify-between px-5 py-4">
            <dt className="text-label font-bold text-neutral-secondary">
              카카오 계정
            </dt>
            <dd className="text-body font-medium text-foreground">
              {MOCK_MEMBER.kakaoEmail}
            </dd>
          </div>
          <div className="flex items-center justify-between px-5 py-4">
            <dt className="text-label font-bold text-neutral-secondary">
              전화번호
            </dt>
            <dd className="text-body font-medium text-foreground">
              {MOCK_MEMBER.phone}
            </dd>
          </div>
        </dl>

        <Button size="cta" className="mt-8" onClick={handleStart}>
          내 경매방으로 이동
        </Button>
      </section>
    </GuestShell>
  )
}
