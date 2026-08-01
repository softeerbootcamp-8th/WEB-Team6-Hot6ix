import type { ReactNode } from 'react'

import { AppHeader, GuestHeader } from '@/components/layout/app-header'
import { MobileAppBar } from '@/components/layout/mobile-app-bar'
import { cn } from '@/lib/utils'

interface ShellProps {
  children: ReactNode
  /** 모바일 앱바 가운데 제목. 웹 상단바에는 쓰이지 않는다. */
  title?: string
  /** 모바일 앱바에 뒤로가기를 노출한다. */
  back?: boolean
  /** 본문 최대 폭을 없애고 화면 전체를 쓴다 (라이브 화면 등). */
  fullWidth?: boolean
  className?: string
}

function ShellBody({
  children,
  fullWidth,
  className,
}: Pick<ShellProps, 'children' | 'fullWidth' | 'className'>) {
  return (
    <main
      className={cn(
        'mx-auto w-full px-5 py-6 md:px-7 md:py-10',
        !fullWidth && 'max-w-[1280px]',
        className,
      )}
    >
      {children}
    </main>
  )
}

/**
 * 로그인 화면 공통 골격.
 *
 * Figma 는 같은 화면을 웹(1280)·모바일(375) 두 프레임으로 그려두었다.
 * 라우트를 나누지 않고 `md` 기준으로 상단 크롬만 바꾼다.
 */
export function AppShell({
  children,
  title,
  back,
  fullWidth,
  className,
}: ShellProps) {
  return (
    <div className="min-h-svh bg-background">
      <div className="md:hidden">
        {/* 로고는 앱바가 아니라 햄버거 서랍 안에 둔다. */}
        <MobileAppBar
          title={title}
          showBack={back}
          showLogo={!back && !title}
        />
      </div>
      <div className="hidden md:block">
        <AppHeader />
      </div>
      <ShellBody fullWidth={fullWidth} className={className}>
        {children}
      </ShellBody>
    </div>
  )
}

/** 비로그인으로 볼 수 있는 화면(랜딩·링크 입장·약관)의 골격. */
export function GuestShell({
  children,
  title,
  back,
  fullWidth,
  className,
  state = '비로그인',
}: ShellProps & { state?: string }) {
  return (
    <div className="min-h-svh bg-background">
      <div className="md:hidden">
        <MobileAppBar
          title={title}
          showBack={back}
          showLogo={!back}
          state={state}
        />
      </div>
      <div className="hidden md:block">
        <GuestHeader state={state} />
      </div>
      <ShellBody fullWidth={fullWidth} className={className}>
        {children}
      </ShellBody>
    </div>
  )
}
