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

        <nav
          aria-label="주요 메뉴"
          className="absolute left-1/2 flex h-9 -translate-x-1/2 items-center rounded-[18px] bg-[#f2f2f5]"
        >
          {NAV_ITEMS.map((item) => (
            <Link
              key={item.to}
              to={item.to}
              className={cn(
                'flex h-9 w-[115px] items-center justify-center rounded-[18px] text-[14px] transition-colors',
                'font-normal text-[#595959] hover:text-foreground',
                '[&.active]:font-semibold [&.active]:text-brand-500',
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

/** 비로그인 화면(랜딩·링크 입장)의 상단바. */
export function GuestHeader({ state = '비로그인' }: { state?: string }) {
  return (
    <header className="h-16 shrink-0 border-b bg-card">
      <div className="mx-auto flex h-full max-w-[1280px] items-center px-7">
        <Link to="/" className="text-logo font-extrabold text-brand-500">
          UpBid
        </Link>
        <span className="ml-auto rounded-full border border-brand-200 bg-brand-50 px-3.5 py-1.5 text-[13px] font-semibold text-neutral-tertiary">
          {state}
        </span>
      </div>
    </header>
  )
}
