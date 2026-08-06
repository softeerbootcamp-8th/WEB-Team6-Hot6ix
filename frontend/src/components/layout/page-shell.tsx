import { useRouter } from '@tanstack/react-router'
import { ChevronLeft } from 'lucide-react'
import type { ReactNode } from 'react'

import { AppHeader, GuestHeader } from '@/components/layout/app-header'
import { MobileAppBar } from '@/components/layout/mobile-app-bar'
import { cn } from '@/lib/utils'

interface ShellProps {
  children: ReactNode
  /** 모바일 앱바 가운데 제목. 웹 상단바에는 쓰이지 않는다. */
  title?: string
  /** 하위 화면 표시. 모바일 앱바와 데스크톱 본문 상단에 뒤로가기를 노출한다. */
  back?: boolean
  /** 본문 최대 폭을 없애고 화면 전체를 쓴다 (라이브 화면 등). */
  fullWidth?: boolean
  className?: string
}

/**
 * 모바일에서만 두는 아래 여백.
 *
 * 내용이 화면에 딱 맞는 화면은 스크롤할 거리가 없어 손가락에 아무 반응이
 * 없다. 한 번 쓸어내릴 만큼(64px)만 여유를 둬서 화면이 굳어 보이지 않게 한다.
 * 데스크톱은 그대로 둔다.
 */
function MobileScrollRoom() {
  return <div aria-hidden className="h-16 md:hidden" />
}

function ShellBody({
  children,
  back,
  fullWidth,
  className,
}: Pick<ShellProps, 'children' | 'back' | 'fullWidth' | 'className'>) {
  return (
    <main
      className={cn(
        'relative mx-auto w-full px-5 py-6 md:px-7 md:py-10',
        !fullWidth && 'max-w-[1280px]',
        className,
      )}
    >
      {/*
        모바일은 앱바가 뒤로가기를 들고 있지만 데스크톱 헤더에는 없다.
        `back` 을 선언한 하위 화면은 데스크톱에서도 돌아갈 길이 있어야 한다.
      */}
      {back && <DesktopBackLink />}
      {children}
    </main>
  )
}

/**
 * 데스크톱에서만 보이는 뒤로가기. 브라우저 히스토리를 그대로 한 칸 되돌린다.
 *
 * **본문 흐름에서 빼내 위쪽 여백 안에 띄운다.** 흐름 안에 두면 제목과 상단
 * 버튼이 그만큼 아래로 밀려서, 뒤로가기가 없는 화면(판매자 정보 등)과 나란히
 * 오갈 때 헤더 높이가 어긋난다.
 */
function DesktopBackLink() {
  const router = useRouter()

  return (
    <button
      type="button"
      onClick={() => void router.history.back()}
      className="ease-soft absolute top-1.5 left-7 hidden items-center gap-1 rounded-lg py-1 pr-2 text-[13px] font-semibold text-neutral-tertiary transition-colors duration-150 hover:text-foreground md:inline-flex"
    >
      <ChevronLeft aria-hidden className="size-4" />
      뒤로
    </button>
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
      <ShellBody back={back} fullWidth={fullWidth} className={className}>
        {children}
      </ShellBody>
      <MobileScrollRoom />
    </div>
  )
}

/**
 * 비로그인으로 볼 수 있는 화면(랜딩·링크 입장·약관)의 골격.
 *
 * `state` 는 우측 상단 pill 문구다. **주지 않으면 pill 자체를 안 그린다** — 로그인
 * 화면처럼 누를 게 없는 자리에 버튼처럼 보이는 걸 두지 않으려는 것이다.
 */
export function GuestShell({
  children,
  title,
  back,
  fullWidth,
  className,
  state,
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
      <ShellBody back={back} fullWidth={fullWidth} className={className}>
        {children}
      </ShellBody>
      <MobileScrollRoom />
    </div>
  )
}
