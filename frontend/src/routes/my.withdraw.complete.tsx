import { createFileRoute, Link } from '@tanstack/react-router'

import { GuestShell } from '@/components/layout/page-shell'

/**
 * 회원 탈퇴 완료 (Figma `WEB-03 · 공통 · 회원 탈퇴 완료`, 713:4796).
 *
 * 640×480 카드 한 장. 탈퇴 직후라 세션이 없어 상단바는 로고 +
 * "로그아웃됨" 배지만 있는 게스트 골격이다.
 */
export const Route = createFileRoute('/my/withdraw/complete')({
  component: WithdrawCompletePage,
})

function WithdrawCompletePage() {
  return (
    <GuestShell title="탈퇴 완료" state="로그아웃됨" className="max-w-[1280px]">
      <section className="mx-auto w-full max-w-[640px] rounded-[24px] border bg-card px-6 pt-16 pb-16 md:px-14">
        <span
          aria-hidden
          className="mx-auto flex size-20 items-center justify-center rounded-[40px] bg-result-won-surface text-[28px] font-extrabold text-result-won"
        >
          ✓
        </span>

        <h1 className="mt-8 text-center text-[24px] font-bold text-foreground">
          회원 탈퇴가 완료되었어요
        </h1>
        <p className="mt-3 text-center text-[14px] font-medium text-neutral-tertiary">
          그동안 UpBid를 이용해 주셔서 감사합니다.
        </p>

        <p className="mt-5 flex min-h-[72px] items-center justify-center rounded-2xl border bg-surface-subtle px-6 text-center text-[13px] font-medium text-neutral-tertiary">
          카카오 계정 연결과 인증 정보가 해제되었습니다.
        </p>

        <Link
          to="/"
          className="ease-soft mt-[34px] flex h-14 items-center justify-center rounded-2xl bg-brand-500 text-[15px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-[0.99]"
        >
          메인 화면으로
        </Link>
      </section>
    </GuestShell>
  )
}
