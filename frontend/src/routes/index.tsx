import { createFileRoute, redirect, useNavigate } from '@tanstack/react-router'

import { KakaoLoginButton } from '@/features/auth/components/kakao-login-button'
import { Reveal } from '@/components/reveal'
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
    title: '링크·QR 로 참여',
    description: '초대 링크만 있으면\n로그인 없이 둘러볼 수 있어요.',
  },
  {
    title: '실시간 입찰',
    description: '현재가와 순위가 바로 보여서\n망설일 시간이 없어요.',
  },
  {
    // 우리는 결제를 받지 않는다. 중개까지가 서비스 범위다.
    title: '거래는 직접, 연결은 저희가',
    description: '낙찰되면 판매자와 낙찰자를\n이어드려요. 결제는 두 분이 직접.',
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
      // 상단바(64)를 뺀 높이를 채우고, 그 안에서 카드를 세로 가운데로 모은다.
      className="flex min-h-[calc(100svh-4rem)] max-w-[1216px] flex-col justify-center px-5 py-6 md:px-8 md:py-10"
    >
      {/*
        두 카드는 같은 높이로 맞춘다(`items-start` 를 쓰면 오른쪽이 내용만큼만
        줄어 짝이 안 맞는다). 각 카드 안에서도 세로 가운데로 모은다.
      */}
      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(0,404px)]">
        <section className="flex flex-col justify-center rounded-4xl border bg-card p-6 md:p-9">
          <Reveal as="div">
            <span className="inline-flex h-7 items-center gap-1.5 rounded-full border border-brand-200 bg-brand-50 px-3 text-[11px] font-extrabold tracking-[0.08em] text-brand-600">
              <span
                aria-hidden
                className="size-1.5 rounded-full bg-brand-500"
              />
              UPBID AUCTION
            </span>
          </Reveal>

          <Reveal
            as="h1"
            delay={60}
            className="mt-5 text-[26px] leading-[1.3] font-extrabold text-foreground md:text-[32px]"
          >
            입찰은 빠르게,
            <br />
            낙찰은 정확하게
          </Reveal>

          <Reveal
            as="p"
            delay={120}
            className="mt-4 text-[14px] leading-[1.6] font-medium text-neutral-tertiary md:text-[15px]"
          >
            SNS 에 올린 물건을 경매방으로 만들어 공유하세요. 참여자는 링크로
            들어와
            <br className="hidden md:inline" />
            실시간으로 입찰하고, 낙찰되면 판매자와 바로 연결됩니다.
          </Reveal>

          <Reveal
            as="p"
            delay={180}
            className="mt-7 text-body-strong font-semibold text-neutral-secondary"
          >
            방 만들기부터 낙찰자 연결까지, 필요한 흐름만 담았습니다.
          </Reveal>

          <ul className="mt-5 grid gap-3 sm:grid-cols-3 md:gap-4">
            {VALUE_POINTS.map((point, index) => (
              <Reveal
                as="li"
                key={point.title}
                delay={220 + index * 90}
                className="rounded-3xl border bg-surface-subtle p-4 md:p-5"
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
              </Reveal>
            ))}
          </ul>
        </section>

        {/* 모바일에서는 로그인 카드가 먼저 보여야 한다. 소개는 그 아래로. */}
        <Reveal
          as="aside"
          className="order-first flex flex-col justify-center rounded-4xl border bg-card p-6 md:p-9 lg:order-none"
        >
          <span className="inline-flex h-7 w-fit items-center gap-1.5 rounded-full border border-brand-200 bg-brand-50 px-3 text-[11px] font-extrabold tracking-[0.08em] text-brand-600">
            <span aria-hidden className="size-1.5 rounded-full bg-brand-500" />
            간편 로그인
          </span>

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

          {/* 결제를 대행하지 않는다는 점은 가입 전에 분명히 알린다. */}
          <p className="mt-6 rounded-2xl bg-surface-subtle px-4 py-3 text-badge leading-[1.6] font-medium text-neutral-tertiary">
            UpBid 는 경매 진행과 거래 연결까지만 도와드려요. 결제와 배송은
            판매자·낙찰자가 직접 진행합니다.
          </p>
        </Reveal>
      </div>
    </GuestShell>
  )
}
