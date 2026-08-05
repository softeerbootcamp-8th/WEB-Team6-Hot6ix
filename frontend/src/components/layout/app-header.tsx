import { Link } from '@tanstack/react-router'

import { cn } from '@/lib/utils'
import { useCurrentUser } from '@/lib/session'

/** Figma 상단바: 로고 + 가운데 pill 네비 + 우측 닉네임·아바타. 높이 64. */
const NAV_ITEMS = [
  { to: '/rooms', label: '내 경매방' },
  { to: '/seller', label: '판매자 정보' },
  { to: '/trades', label: '거래 내역' },
] as const

export function AppHeader() {
  const user = useCurrentUser()

  return (
    <header className="h-16 shrink-0 border-b bg-card">
      <div className="relative mx-auto flex h-full max-w-[1280px] items-center px-7">
        <Link to="/rooms" className="text-logo font-extrabold text-brand-500">
          UpBid
        </Link>

        {/*
         * 글자 규격을 화면 전체와 맞춘다. 예전에는 굵기 400에 회색 헥스를
         * 직접 박아 다른 메뉴·버튼과 다른 서체처럼 보였다.
         */}
        <nav
          aria-label="주요 메뉴"
          className="bg-fill absolute left-1/2 flex h-10 -translate-x-1/2 items-center rounded-[20px] p-1 font-sans"
        >
          {NAV_ITEMS.map((item) => (
            <Link
              key={item.to}
              to={item.to}
              className={cn(
                'ease-soft flex h-8 w-[112px] items-center justify-center rounded-[16px] text-[14px] font-semibold transition-all duration-150',
                'text-neutral-tertiary hover:text-neutral-secondary',
                '[&.active]:bg-card [&.active]:font-bold [&.active]:text-brand-500 [&.active]:shadow-sm',
              )}
            >
              {item.label}
            </Link>
          ))}
        </nav>

        {user && (
          <Link
            to="/my"
            className="ml-auto flex items-center gap-2.5 text-[13px] font-medium text-neutral-tertiary transition-colors hover:text-neutral-secondary"
          >
            {user.nickname} 님
            <span
              aria-hidden
              className="flex size-8 items-center justify-center rounded-full bg-brand-200 text-[12px] font-bold text-brand-600"
            >
              {user.nickname.slice(0, 1)}
            </span>
          </Link>
        )}
      </div>
    </header>
  )
}

/**
 * 비로그인 화면(랜딩·링크 입장)의 상단바.
 *
 * `state` 를 주지 않으면 우측 pill 을 그리지 않는다 — 로그인 화면에서는 그 자리가
 * 누를 수 있는 버튼처럼 보여서 뺐다(`MobileAppBar` 도 같은 규칙이다).
 */
export function GuestHeader({ state }: { state?: string }) {
  return (
    <header className="h-16 shrink-0 border-b bg-card">
      {/* 좌우 여백은 아래 카드(랜딩 컨테이너)와 같은 값을 쓴다. */}
      <div className="mx-auto flex h-full max-w-[1216px] items-center px-5 md:px-8">
        <Link to="/" className="text-logo font-extrabold text-brand-500">
          UpBid
        </Link>
        {state && (
          <span className="ml-auto rounded-full border border-brand-200 bg-brand-50 px-3.5 py-1.5 text-[13px] font-semibold text-neutral-tertiary">
            {state}
          </span>
        )}
      </div>
    </header>
  )
}
