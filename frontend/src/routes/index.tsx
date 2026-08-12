import { createFileRoute, redirect } from '@tanstack/react-router'

import { KakaoLoginButton } from '@/features/auth/components/kakao-login-button'
import { Reveal } from '@/components/reveal'
import { GuestShell } from '@/components/layout/page-shell'
import { useIsDesktop } from '@/hooks/use-media-query'
import { sessionStore } from '@/lib/session'
import linkQrMascot from '@/assets/landing-mascot-v1/link-qr.png'
import liveBiddingMascot from '@/assets/landing-mascot-v1/live-bidding.png'
import recordsMascot from '@/assets/landing-mascot-v1/records.png'

const KAKAO_CLIENT_ID = import.meta.env.VITE_KAKAO_CLIENT_ID as string
const KAKAO_REDIRECT_URI = import.meta.env.VITE_KAKAO_REDIRECT_URI as string

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

/**
 * 마스코트는 장식이라 `alt` 를 비워 스크린리더가 건너뛰게 한다. 카드의 제목과
 * 설명이 이미 같은 내용을 글로 말하고 있어서, 읽어 주면 같은 말이 두 번 나온다.
 */
const VALUE_POINTS = [
  {
    title: '링크·QR 로 참여',
    description: '초대 링크만 있으면 로그인 없이 둘러볼 수 있어요.',
    mascot: linkQrMascot,
  },
  {
    title: '실시간 입찰',
    description: '현재가와 순위가 바로 보여서 망설일 시간이 없어요.',
    mascot: liveBiddingMascot,
  },
  {
    /*
     * 결제를 대행하지 않는다는 안내는 로그인 카드 아래에 따로 있다. 여기는
     * 서비스를 쓸 이유를 적는 자리라 남는 기록을 내세운다.
     */
    title: '투명한 거래 기록',
    description:
      '입찰 순위와 낙찰가, 거래 진행 상태가 그대로 남아 언제든 다시 확인할 수 있어요.',
    mascot: recordsMascot,
  },
] as const

function LandingPage() {
  const { redirect: redirectTo } = Route.useSearch()
  /*
   * 마스코트를 어디에 둘지가 폭에 따라 아예 다르다. 데스크톱은 카드가 3열로
   * 좁아져 그림이 들어갈 자리가 없어서 제목 옆에 큰 것 한 장만 두고, 모바일은
   * 카드가 가로로 넓어 항목마다 한 장씩 넣는다.
   *
   * CSS 로 숨기지 않고 트리를 나눈다. `hidden` 은 그림을 내려받는 것까지
   * 막지 못해서, 데스크톱이 쓰지도 않을 카드용 그림 두 장을 받게 된다.
   */
  const isDesktop = useIsDesktop()

  /**
   * 카카오 OAuth 인증 페이지로 이동한다.
   * 백엔드 콜백(`/api/v1/oauth/kakao/callback`)이 code 를 받아 로그인을 처리하고
   * 프론트로 리다이렉트한다.
   *
   * redirect 파라미터가 있으면 OAuth state 에 담아 보낸다. 카카오가 콜백 때
   * state 를 그대로 돌려주면 백엔드가 이를 파싱해 로그인 후 해당 경로로 이동시킨다.
   */
  const handleKakaoLogin = () => {
    const state = redirectTo ? `&state=${encodeURIComponent(redirectTo)}` : ''
    const url = `https://kauth.kakao.com/oauth/authorize?response_type=code&client_id=${KAKAO_CLIENT_ID}&redirect_uri=${encodeURIComponent(KAKAO_REDIRECT_URI)}${state}`
    location.href = url
  }

  return (
    <GuestShell
      // 우측 상단 pill 을 안 그린다. 로그인 화면에서 "로그인"이라고 적힌 pill 이
      // 누를 수 있는 버튼처럼 보였다. 실제 로그인은 아래 카카오 버튼 하나뿐이다.
      // `requireMember` 가 돌려보내는 redirect 화면도 이 컴포넌트다.
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

          <div className="flex items-start justify-between gap-6">
            <Reveal
              as="h1"
              delay={60}
              className="mt-5 text-[26px] leading-[1.3] font-extrabold text-foreground md:text-[32px]"
            >
              입찰은 빠르게,
              <br />
              낙찰은 정확하게
            </Reveal>

            {isDesktop && (
              /*
               * 음수 여백으로 그림이 줄 높이를 밀지 않게 한다. 그냥 두면 160px
               * 짜리 그림이 제목 줄을 통째로 키워서, 제목과 아래 문단 사이가
               * 제목 두 줄만큼 더 벌어진다.
               */
              <Reveal delay={100} className="-mt-8 -mb-4 shrink-0">
                <img
                  src={liveBiddingMascot}
                  alt=""
                  aria-hidden
                  decoding="sync"
                  width={320}
                  height={320}
                  className="size-28 object-contain xl:size-32"
                />
              </Reveal>
            )}
          </div>

          <Reveal
            as="p"
            delay={120}
            className="mt-4 text-[14px] leading-[1.6] font-medium text-neutral-tertiary md:text-[15px]"
          >
            SNS 에 올린 물건을 경매방으로 만들어 공유하세요. 참여자는 링크로
            들어와 실시간으로 입찰하고, 낙찰되면 판매자와 바로 연결됩니다.
          </Reveal>

          <Reveal
            as="p"
            delay={180}
            className="mt-7 text-body-strong font-semibold text-neutral-secondary"
          >
            방 만들기부터 낙찰자 연결까지, 필요한 흐름만 담았습니다.
          </Reveal>

          {/*
            좁은 화면은 카드를 세로로 쌓고 목록 전체를 한 번에 띄운다. 카드마다
            지연을 주면 위에서 아래로 하나씩 떨어져 끊겨 보이고, 맨 아래 카드는
            첫 화면 밖이라 스크롤하기 전까지 빈 자리로 남는다.
          */}
          <Reveal
            as="ul"
            delay={220}
            className="mt-5 grid gap-3 lg:grid-cols-3 lg:gap-4"
          >
            {VALUE_POINTS.map((point, index) => (
              <li
                key={point.title}
                className="flex items-center gap-4 rounded-3xl border bg-surface-subtle p-4 lg:block lg:p-5"
              >
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-3 lg:block">
                    <span
                      aria-hidden
                      className="flex size-11 shrink-0 items-center justify-center rounded-full bg-brand-50 text-body-strong font-extrabold text-brand-500"
                    >
                      {index + 1}
                    </span>
                    <h2 className="text-[16px] font-extrabold text-foreground lg:mt-4">
                      {point.title}
                    </h2>
                  </div>
                  <p className="mt-3 text-[13px] leading-[1.6] font-medium text-neutral-tertiary lg:mt-2">
                    {point.description}
                  </p>
                </div>

                {!isDesktop && (
                  <img
                    src={point.mascot}
                    alt=""
                    aria-hidden
                    decoding="sync"
                    width={256}
                    height={256}
                    className="size-24 shrink-0 object-contain"
                  />
                )}
              </li>
            ))}
          </Reveal>
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
