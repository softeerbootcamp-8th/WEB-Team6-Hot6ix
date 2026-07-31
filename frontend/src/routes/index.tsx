import { createFileRoute, redirect, useNavigate } from '@tanstack/react-router'

import { KakaoLoginButton } from '@/features/auth/components/kakao-login-button'
import { GuestShell } from '@/components/layout/page-shell'
import { sessionStore } from '@/lib/session'

export const Route = createFileRoute('/')({
  // 로그인 후 돌아갈 위치. 입찰하려다 로그인한 경우 원래 방으로 복귀한다.
  // 없을 때 키 자체를 빼야 다른 화면의 `<Link to="/">` 가 search 를 요구하지 않는다.
  validateSearch: (search: Record<string, unknown>): { redirect?: string } =>
    typeof search.redirect === 'string' ? { redirect: search.redirect } : {},
  beforeLoad: ({ search }) => {
    if (sessionStore.getState().status === 'member') {
      throw redirect({ href: search.redirect ?? '/rooms' })
    }
  },
  component: LandingPage,
})

const VALUE_POINTS = [
  {
    title: '링크로 바로 참여',
    description: '초대 링크만 있으면\n경매방을 확인할 수 있어요.',
  },
  {
    title: '실시간 입찰',
    description: '현재가와 순위를 보며\n빠르게 입찰해요.',
  },
  {
    title: '거래까지 한곳에서',
    description: '낙찰 결과와 거래 상태를\n놓치지 않고 확인해요.',
  },
] as const

function LandingPage() {
  const navigate = useNavigate()
  const { redirect: redirectTo } = Route.useSearch()

  /**
   * 카카오 인증은 백엔드(`/api/v1/oauth/kakao/callback`)가 처리한다.
   * 지금은 목업이라 신규 가입 흐름인 전화번호 인증으로 바로 넘긴다.
   */
  const handleKakaoLogin = () => {
    void navigate({
      to: '/signup/phone',
      search: redirectTo ? { redirect: redirectTo } : {},
    })
  }

  return (
    <GuestShell
      state="로그인"
      className="max-w-[1216px] px-5 py-6 md:px-8 md:py-8"
    >
      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,788px)_minmax(0,404px)]">
        <section className="rounded-4xl border bg-card p-6 md:p-10">
          <p className="text-label font-bold tracking-wide text-brand-500">
            UPBID AUCTION
          </p>

          <h1 className="mt-5 text-[26px] leading-[1.35] font-extrabold text-foreground md:text-[34px]">
            좋은 물건을 만나는 순간,
            <br />
            경매가 시작됩니다
          </h1>

          <p className="mt-6 text-[14px] leading-[1.6] font-medium text-neutral-tertiary md:text-[15px]">
            누구나 링크로 참여하고, 실시간으로 경쟁하며,
            <br />
            낙찰 이후 거래까지 한곳에서 이어지는 경매 서비스입니다.
          </p>

          <p className="mt-8 text-body-strong font-semibold text-neutral-secondary">
            참여부터 거래까지, 필요한 흐름만 담았습니다.
          </p>

          <ul className="mt-6 grid gap-4 sm:grid-cols-3 md:gap-6">
            {VALUE_POINTS.map((point, index) => (
              <li
                key={point.title}
                className="rounded-3xl border bg-surface-subtle p-5"
              >
                <span
                  aria-hidden
                  className="flex size-11 items-center justify-center rounded-full bg-brand-50 text-body-strong font-extrabold text-brand-500"
                >
                  {index + 1}
                </span>
                <h2 className="mt-4 text-[16px] font-extrabold text-foreground">
                  {point.title}
                </h2>
                <p className="mt-2 text-[13px] leading-[1.6] font-medium whitespace-pre-line text-neutral-tertiary">
                  {point.description}
                </p>
              </li>
            ))}
          </ul>
        </section>

        <aside className="rounded-4xl border bg-card p-6 md:p-10">
          <p className="text-label font-bold tracking-wide text-brand-500">
            간편 로그인
          </p>

          <h2 className="mt-5 text-[22px] font-extrabold text-foreground md:text-[26px]">
            UpBid 시작하기
          </h2>

          <p className="mt-4 text-body font-medium leading-[1.6] text-neutral-tertiary">
            계정 연결 후 바로 경매방을
            <br />
            둘러보고 참여할 수 있어요.
          </p>

          <KakaoLoginButton onClick={handleKakaoLogin} className="mt-8" />

          <p className="mt-4 text-caption font-normal leading-[1.6] text-neutral-muted">
            로그인 없이도 경매방을 둘러볼 수 있어요. 입찰할 때만 로그인이
            필요합니다.
          </p>
        </aside>
      </div>
    </GuestShell>
  )
}
