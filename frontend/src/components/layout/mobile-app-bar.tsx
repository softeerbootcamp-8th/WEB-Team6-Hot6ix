import { ChevronLeft } from 'lucide-react'
import { Link, useRouter } from '@tanstack/react-router'
import type { ReactNode } from 'react'

import { MobileNavDrawer } from '@/components/layout/mobile-nav-drawer'

/**
 * 모바일 상단 앱바.
 *
 * Figma 모바일 프레임은 세 가지 형태로 나뉜다.
 * - 로고형: 인증·진입 화면 (`logo` + `appbar_state`)
 * - 뒤로가기형: 상세 화면 (`back` + `nav_타이틀`)
 * - 메뉴형: 마이페이지 (`nav_타이틀` + `nav_hamburger`)
 */
export function MobileAppBar({
  title,
  showBack = false,
  showLogo = false,
  state,
  trailing,
  /** 로그인 화면처럼 이동할 곳이 없을 때만 끈다. */
  showNav = true,
}: {
  title?: string
  showBack?: boolean
  showLogo?: boolean
  state?: string
  trailing?: ReactNode
  showNav?: boolean
}) {
  const router = useRouter()

  return (
    <header className="sticky top-0 z-40 border-b bg-card">
      {/* 좌우 여백은 본문(ShellBody 의 px-5)과 같은 값을 쓴다. */}
      <div className="relative flex h-14 items-center px-5">
        {showBack && (
          <button
            type="button"
            onClick={() => router.history.back()}
            aria-label="뒤로 가기"
            className="-ml-2 flex size-9 items-center justify-center rounded-lg text-neutral-secondary transition-colors hover:bg-fill"
          >
            <ChevronLeft aria-hidden className="size-5" />
          </button>
        )}

        {showLogo && (
          <Link to="/" className="text-logo font-extrabold text-brand-500">
            UpBid
          </Link>
        )}

        {title && (
          <h1 className="absolute left-1/2 -translate-x-1/2 text-card-title font-bold text-foreground">
            {title}
          </h1>
        )}

        <div className="ml-auto flex items-center gap-1">
          {state && (
            <span className="rounded-full bg-fill px-3 py-1 text-caption font-semibold text-neutral-tertiary">
              {state}
            </span>
          )}
          {trailing}
          {showNav && <MobileNavDrawer />}
        </div>
      </div>
    </header>
  )
}
