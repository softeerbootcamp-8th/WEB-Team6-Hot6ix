import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useEffect, useRef, useState } from 'react'

import { AppShell } from '@/components/layout/page-shell'
import { requireMember } from '@/lib/route-guards'
import { sessionStore } from '@/lib/session'

/**
 * 회원 탈퇴 확인 (Figma `WEB-02 · 공통 · 회원 탈퇴 확인`, 713:4776).
 *
 * 720×500 카드 한 장. 경고 아이콘 64, 제목 22, 설명,
 * 608×116 확인 사항 박스, 아래에 296 버튼 두 개.
 */
export const Route = createFileRoute('/my/withdraw/')({
  beforeLoad: requireMember,
  component: WithdrawConfirmPage,
})

const NOTICES = [
  '진행 중인 거래가 있다면 먼저 완료해 주세요.',
  '탈퇴 후에는 기존 계정을 복구할 수 없습니다.',
]

function WithdrawConfirmPage() {
  const navigate = useNavigate()
  const [pending, setPending] = useState(false)

  // 탈퇴 후 화면을 떠나므로 남은 목업 타이머는 언마운트 때 정리한다.
  const timer = useRef<number | null>(null)
  useEffect(
    () => () => {
      if (timer.current !== null) window.clearTimeout(timer.current)
    },
    [],
  )

  const withdraw = () => {
    setPending(true)
    // TODO: DELETE /api/v1/users/me 연동 (현재 목업)
    timer.current = window.setTimeout(() => {
      sessionStore.signOut()
      void navigate({ to: '/my/withdraw/complete' })
    }, 700)
  }

  return (
    <AppShell title="회원 탈퇴" back className="max-w-[1280px]">
      <div className="hidden md:block">
        <h1 className="text-[28px] font-bold text-foreground">회원 탈퇴</h1>
        <p className="mt-2 text-[14px] font-medium text-neutral-tertiary">
          탈퇴 전에 계정과 거래 상태를 확인해 주세요.
        </p>
      </div>

      <section className="mx-auto mt-4 w-full max-w-[720px] rounded-[24px] border bg-card px-6 pt-10 pb-[82px] md:px-14">
        <span
          aria-hidden
          className="mx-auto flex size-16 items-center justify-center rounded-[32px] bg-result-failed-surface text-[28px] font-extrabold text-live"
        >
          !
        </span>

        <h2 className="mt-6 text-center text-[22px] font-bold text-foreground">
          정말 UpBid를 탈퇴하시겠어요?
        </h2>
        <p className="mt-3 text-center text-[14px] font-medium text-neutral-tertiary">
          탈퇴하면 연결된 카카오 계정과 인증 정보가 삭제됩니다.
        </p>

        <div className="mt-7 rounded-2xl border border-[#ffc0c3] bg-[#fff8f8] px-6 py-6">
          <p className="text-[14px] font-bold text-live">탈퇴 전 확인 사항</p>
          <ul className="mt-4 space-y-3.5">
            {NOTICES.map((notice) => (
              <li
                key={notice}
                className="text-[13px] font-medium text-neutral-tertiary"
              >
                • {notice}
              </li>
            ))}
          </ul>
        </div>

        <div className="mt-9 flex flex-col gap-4 sm:flex-row">
          <Link
            to="/my"
            className="ease-soft flex h-[52px] flex-1 items-center justify-center rounded-2xl border bg-card text-[14px] font-bold text-neutral-tertiary transition-all duration-150 hover:bg-fill active:scale-[0.98]"
          >
            마이페이지로 돌아가기
          </Link>
          <button
            type="button"
            onClick={withdraw}
            disabled={pending}
            className="ease-soft h-[52px] flex-1 rounded-2xl bg-live text-[14px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-60 disabled:active:scale-100"
          >
            {pending ? '처리 중…' : '회원 탈퇴'}
          </button>
        </div>
      </section>
    </AppShell>
  )
}
