import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'

import { AppShell } from '@/components/layout/page-shell'
import { ProfilePhoto } from '@/components/profile-photo'
import { cn } from '@/lib/utils'
import { requireMember } from '@/lib/route-guards'
import { sessionStore, useCurrentUser } from '@/lib/session'
import { useLogout } from '@/api/generated/인증/인증'

/**
 * 마이페이지·계정 (Figma `WEB-01 · 공통 · 마이페이지·계정`, 713:4755).
 *
 * 380 프로필 패널 + 24 간격 + 812 계정 패널, 패널 높이 420.
 */
export const Route = createFileRoute('/my/')({
  beforeLoad: requireMember,
  component: MyPage,
})

function MyPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const user = useCurrentUser()
  const { mutate: logout, isPending: isLoggingOut } = useLogout({
    mutation: {
      onSuccess: () => {
        queryClient.removeQueries({ queryKey: ['session'] })
        sessionStore.signOut()
        void navigate({ to: '/' })
      },
    },
  })

  if (!user) return null

  const handleLogout = () => logout()

  return (
    <AppShell title="마이페이지" className="max-w-[1280px]">
      {/*
       * 모바일(MOB-01)은 가로형 프로필 카드 + 연결된 계정 카드 +
       * 약관 카드(행 + chevron) + 버튼 두 개다. 웹 2패널과 구성이 달라 나눈다.
       */}
      <div className="md:hidden">
        <section className="flex items-center gap-4 rounded-[18px] border bg-card p-4">
          <ProfilePhoto
            seed={user.nickname}
            src={user.profileImageUrl}
            className="size-14 shrink-0 rounded-full border border-brand-300 bg-brand-50"
          />

          <div className="min-w-0 flex-1">
            <p className="truncate text-[18px] font-bold text-foreground">
              {user.nickname}
            </p>
            <p className="mt-1.5 truncate text-[12px] font-medium text-neutral-tertiary">
              프로필과 계정 정보를 확인하세요.
            </p>
          </div>

          <Link
            to="/my/profile/edit"
            className="ease-soft flex h-11 w-24 shrink-0 items-center justify-center rounded-xl border border-brand-500 bg-card text-[12px] font-medium text-brand-500 transition-all duration-150 hover:bg-brand-50 active:scale-95"
          >
            프로필 수정
          </Link>
        </section>

        <h2 className="mt-6 text-[14px] font-medium text-foreground">
          연결된 계정
        </h2>
        <dl className="mt-2 rounded-2xl border bg-card px-4">
          <div className="flex h-14 items-center justify-between gap-3 border-b">
            <dt className="text-[13px] font-medium text-neutral-tertiary">
              카카오 계정
            </dt>
            <dd className="text-[13px] font-medium text-result-won">연결됨</dd>
          </div>
          <div className="flex h-14 items-center justify-between gap-3">
            <dt className="text-[13px] font-medium text-neutral-tertiary">
              인증 전화번호
            </dt>
            <dd className="text-[13px] font-medium tabular-nums text-foreground">
              {user.phone ?? '미인증'}
            </dd>
          </div>
        </dl>

        <h2 className="mt-6 text-[14px] font-medium text-foreground">
          약관 및 계정 관리
        </h2>
        <nav className="mt-2 rounded-2xl border bg-card px-4">
          {[
            { to: '/terms/privacy' as const, label: '개인정보 처리방침' },
            { to: '/terms/service' as const, label: '서비스 이용약관' },
          ].map((entry, index) => (
            <Link
              key={entry.to}
              to={entry.to}
              className={cn(
                'ease-soft flex h-[52px] items-center justify-between gap-3 text-[13px] font-medium text-foreground transition-colors duration-150 active:text-brand-500',
                index === 0 && 'border-b',
              )}
            >
              {entry.label}
              <span aria-hidden className="text-[20px] text-neutral-muted">
                ›
              </span>
            </Link>
          ))}
        </nav>

        <div className="mt-7 flex gap-3">
          <button
            type="button"
            onClick={handleLogout}
            disabled={isLoggingOut}
            className="ease-soft h-13 flex-1 rounded-[14px] border bg-card text-[15px] font-bold text-foreground transition-all duration-150 hover:bg-fill active:scale-95 disabled:opacity-60"
          >
            {isLoggingOut ? '로그아웃 중…' : '로그아웃'}
          </button>
          <Link
            to="/my/withdraw"
            className="ease-soft flex h-13 flex-1 items-center justify-center rounded-[14px] border bg-card text-[15px] font-bold text-live transition-all duration-150 hover:bg-[#fff5f5] active:scale-95"
          >
            회원 탈퇴
          </Link>
        </div>
      </div>

      <div className="hidden md:block">
        <h1 className="text-[28px] font-bold text-foreground">마이페이지</h1>
        <p className="mt-2 text-[14px] font-medium text-neutral-tertiary">
          프로필과 연결된 계정 정보를 확인하세요.
        </p>

        <div className="mt-4 grid gap-6 lg:grid-cols-[380px_minmax(0,1fr)]">
          {/* 내 프로필 — 380×420 */}
          {/* 아래가 휑하지 않도록 남는 높이를 카드가 가져간다. */}
          <section className="flex flex-col rounded-[24px] border bg-card p-7 lg:h-[calc(100svh-15rem)] lg:min-h-[420px]">
            <h2 className="text-[18px] font-bold text-foreground">내 프로필</h2>

            <ProfilePhoto
              seed={user.nickname}
              src={user.profileImageUrl}
              size={320}
              className="mt-4 size-32 shrink-0 self-center rounded-full border border-brand-300 bg-brand-50"
            />

            <p className="mt-6 text-center text-[22px] font-bold text-foreground">
              {user.nickname}
            </p>

            <Link
              to="/my/profile/edit"
              className="ease-soft mt-auto flex h-16 items-center justify-center rounded-2xl border border-brand-300 bg-card text-[14px] font-bold text-brand-500 transition-all duration-150 hover:bg-brand-50 active:scale-[0.98]"
            >
              프로필 수정
            </Link>
          </section>

          {/* 연결된 계정 — 812×420 */}
          <section className="flex flex-col rounded-[24px] border bg-card p-7 lg:h-[calc(100svh-15rem)] lg:min-h-[420px]">
            <h2 className="text-[18px] font-bold text-foreground">
              연결된 계정
            </h2>

            <dl className="mt-4 rounded-2xl border bg-surface-subtle px-5">
              <div className="flex h-16 items-center justify-between gap-3 border-b">
                <dt className="text-[13px] font-medium text-neutral-tertiary">
                  카카오 계정
                </dt>
                <dd className="text-[14px] font-bold text-result-won">
                  연결됨
                </dd>
              </div>
              <div className="flex h-16 items-center justify-between gap-3">
                <dt className="text-[13px] font-medium text-neutral-tertiary">
                  인증 전화번호
                </dt>
                <dd className="text-[14px] font-bold tabular-nums text-foreground">
                  {user.phone ?? '미인증'}
                </dd>
              </div>
            </dl>

            <div className="mt-5 flex min-h-[72px] flex-wrap items-center gap-x-4 gap-y-2 rounded-2xl border bg-surface-subtle px-6 py-4">
              <p className="text-[16px] font-extrabold text-foreground">
                약관 및 계정 관리
              </p>
              <div className="ml-auto flex items-center gap-3.5">
                <Link
                  to="/terms/privacy"
                  className="text-[13px] font-bold text-brand-500 hover:underline"
                >
                  개인정보 처리방침
                </Link>
                <Link
                  to="/terms/service"
                  className="text-[13px] font-bold text-brand-500 hover:underline"
                >
                  서비스 이용약관
                </Link>
              </div>
            </div>

            <div className="mt-auto flex flex-wrap justify-end gap-3">
              <button
                type="button"
                onClick={handleLogout}
                disabled={isLoggingOut}
                className="ease-soft h-[52px] w-40 rounded-2xl border bg-card text-[14px] font-bold text-neutral-tertiary transition-all duration-150 hover:bg-fill active:scale-95 disabled:opacity-60"
              >
                {isLoggingOut ? '로그아웃 중…' : '로그아웃'}
              </button>
              <Link
                to="/my/withdraw"
                className="ease-soft flex h-[52px] w-40 items-center justify-center rounded-2xl border bg-card text-[13px] font-medium text-live transition-all duration-150 hover:bg-[#fff5f5] active:scale-95"
              >
                회원 탈퇴
              </Link>
            </div>
          </section>
        </div>
      </div>
    </AppShell>
  )
}
