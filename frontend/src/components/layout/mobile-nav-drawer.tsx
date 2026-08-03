import { Link, useNavigate, useRouterState } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import {
  ChevronRight,
  Gavel,
  LogOut,
  Menu,
  Receipt,
  Store,
  User,
  X,
} from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'

import { ProfilePhoto } from '@/components/profile-photo'
import { cn } from '@/lib/utils'
import { sessionStore, useSession } from '@/lib/session'
import { useLogout } from '@/api/generated/인증/인증'

/**
 * 모바일 섹션 이동 서랍.
 *
 * 데스크톱 상단바에는 `내 경매방 / 판매자 정보 / 거래 내역` 알약이 있는데
 * 모바일 앱바에는 그 자리가 없어서, 한번 상세 화면에 들어가면
 * **뒤로가기 말고는 다른 섹션으로 갈 방법이 없었다.**
 *
 * 앱바 오른쪽 햄버거로 열고, 오른쪽에서 밀려 나온다.
 * 위쪽은 내 프로필, 가운데는 섹션 카드, 아래는 약관·로그아웃이다.
 */
const NAV_ITEMS: {
  to: string
  label: string
  desc: string
  icon: ReactNode
}[] = [
  {
    to: '/rooms',
    label: '내 경매방',
    desc: '참여·개설한 경매방',
    icon: <Gavel className="size-[18px]" />,
  },
  {
    to: '/seller',
    label: '판매자 정보',
    desc: '프로필·상품·경매방 개설',
    icon: <Store className="size-[18px]" />,
  },
  {
    to: '/trades',
    label: '거래 내역',
    desc: '낙찰 결과와 거래 처리',
    icon: <Receipt className="size-[18px]" />,
  },
  {
    to: '/my',
    label: '마이페이지',
    desc: '계정과 약관',
    icon: <User className="size-[18px]" />,
  },
]

export function MobileNavDrawer() {
  const [open, setOpen] = useState(false)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const session = useSession()
  const pathname = useRouterState({
    select: (state) => state.location.pathname,
  })
  const { mutate: logout, isPending: isLoggingOut } = useLogout({
    mutation: {
      onSuccess: () => {
        queryClient.removeQueries({ queryKey: ['session'] })
        sessionStore.signOut()
        setOpen(false)
        void navigate({ to: '/' })
      },
    },
  })

  // 화면을 옮기면 서랍을 닫는다. 열어둔 채로 남으면 뒤 화면을 가린다.
  useEffect(() => setOpen(false), [pathname])

  // 열려 있는 동안 뒤 화면이 스크롤되지 않게 한다.
  useEffect(() => {
    if (!open) return
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = previous
    }
  }, [open])

  // 게스트는 갈 수 있는 곳이 없다.
  if (session.status !== 'member') return null

  const user = session.user

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-label="메뉴 열기"
        aria-expanded={open}
        className="ease-soft flex size-9 items-center justify-center rounded-lg text-neutral-secondary transition-all duration-150 hover:bg-fill active:scale-90"
      >
        <Menu aria-hidden className="size-5" />
      </button>

      {open && (
        <div className="fixed inset-0 z-50">
          <button
            type="button"
            aria-label="메뉴 닫기"
            onClick={() => setOpen(false)}
            className="absolute inset-0 bg-neutral-strong/45 backdrop-blur-[2px]"
          />

          <nav
            aria-label="섹션 이동"
            className="animate-drawer absolute inset-y-0 right-0 flex w-[300px] flex-col rounded-l-3xl bg-card shadow-2xl"
          >
            {/* 프로필 — 브랜드면으로 시작해 서랍 전체의 성격을 잡는다 */}
            <div className="shrink-0 rounded-tl-3xl bg-brand-50 px-5 py-5">
              {/* 로고는 앱바마다 두지 않고 여기서 한 번만 보여준다. */}
              <div className="flex items-center">
                <Link
                  to="/rooms"
                  className="text-logo font-extrabold text-brand-500"
                >
                  UpBid
                </Link>

                <button
                  type="button"
                  onClick={() => setOpen(false)}
                  aria-label="메뉴 닫기"
                  className="ease-soft -mr-2 ml-auto flex size-9 shrink-0 items-center justify-center rounded-full text-neutral-tertiary transition-all duration-150 hover:bg-card active:scale-90"
                >
                  <X aria-hidden className="size-5" />
                </button>
              </div>

              <div className="mt-4 flex items-center gap-3">
                <ProfilePhoto
                  seed={user.nickname}
                  className="size-12 shrink-0 rounded-full border border-brand-300 bg-card"
                />

                <p className="min-w-0 flex-1 truncate text-[16px] font-extrabold text-foreground">
                  {user.nickname}
                </p>
              </div>
            </div>

            <ul className="min-h-0 flex-1 space-y-1.5 overflow-y-auto p-3">
              {NAV_ITEMS.map((item, index) => {
                const active = pathname.startsWith(item.to)

                return (
                  <li key={item.to}>
                    <Link
                      to={item.to}
                      style={{ animationDelay: `${index * 40}ms` }}
                      className={cn(
                        'animate-rise ease-soft flex items-center gap-3 rounded-2xl border px-3 py-3 transition-all duration-150 active:scale-[0.99]',
                        active
                          ? 'border-brand-300 bg-brand-50'
                          : 'border-transparent hover:bg-fill',
                      )}
                    >
                      <span
                        aria-hidden
                        className={cn(
                          'flex size-10 shrink-0 items-center justify-center rounded-xl',
                          active
                            ? 'bg-brand-500 text-white'
                            : 'bg-fill text-neutral-tertiary',
                        )}
                      >
                        {item.icon}
                      </span>

                      <span className="min-w-0 flex-1">
                        <span
                          className={cn(
                            'block truncate text-[14px] font-bold',
                            active ? 'text-brand-600' : 'text-foreground',
                          )}
                        >
                          {item.label}
                        </span>
                        <span className="mt-0.5 block truncate text-[11px] font-medium text-neutral-tertiary">
                          {item.desc}
                        </span>
                      </span>

                      <ChevronRight
                        aria-hidden
                        className={cn(
                          'size-4 shrink-0',
                          active ? 'text-brand-500' : 'text-neutral-muted',
                        )}
                      />
                    </Link>
                  </li>
                )
              })}
            </ul>

            <div className="shrink-0 border-t px-5 py-4">
              <div className="flex items-center gap-3 text-[11px] font-medium text-neutral-tertiary">
                <Link to="/terms/privacy" className="hover:underline">
                  개인정보 처리방침
                </Link>
                <span aria-hidden className="text-neutral-muted">
                  ·
                </span>
                <Link to="/terms/service" className="hover:underline">
                  이용약관
                </Link>
              </div>

              <button
                type="button"
                onClick={() => logout()}
                disabled={isLoggingOut}
                className="ease-soft mt-3 flex h-10 w-full items-center justify-center gap-1.5 rounded-xl border bg-card text-[13px] font-bold text-neutral-secondary transition-all duration-150 hover:bg-fill active:scale-[0.98] disabled:opacity-60"
              >
                <LogOut aria-hidden className="size-4" />
                {isLoggingOut ? '로그아웃 중…' : '로그아웃'}
              </button>
            </div>
          </nav>
        </div>
      )}
    </>
  )
}
