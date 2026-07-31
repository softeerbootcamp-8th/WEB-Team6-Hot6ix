import { ChevronRight, LogOut } from 'lucide-react'
import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'

import { AppShell } from '@/components/layout/page-shell'
import { Button } from '@/components/ui/button'
import { PageHeader } from '@/components/page-header'
import { StatusBadge } from '@/components/status-badge'
import { requireMember } from '@/lib/route-guards'
import { sessionStore, useCurrentUser } from '@/lib/session'

export const Route = createFileRoute('/my/')({
  beforeLoad: requireMember,
  component: MyPage,
})

const TERMS_LINKS = [
  { to: '/terms/privacy', label: '개인정보처리방침' },
  { to: '/terms/service', label: '이용약관' },
] as const

function MyPage() {
  const navigate = useNavigate()
  const user = useCurrentUser()

  if (!user) return null

  const handleLogout = () => {
    // TODO: POST /api/v1/auth/logout 연동 (현재 목업)
    sessionStore.signOut()
    void navigate({ to: '/' })
  }

  return (
    <AppShell title="마이페이지" className="max-w-[720px]">
      <PageHeader
        title="마이페이지"
        description="프로필과 연결된 계정을 확인하고 관리할 수 있어요."
      />

      <section className="mt-6 rounded-4xl border bg-card p-5 md:p-6">
        <div className="flex items-center gap-4">
          <span
            aria-hidden
            className="flex size-16 items-center justify-center rounded-full bg-brand-50 text-[22px] font-extrabold text-brand-500"
          >
            {user.nickname.slice(0, 1)}
          </span>
          <div className="min-w-0 flex-1">
            <h2 className="text-room-title font-bold text-foreground">
              {user.nickname}
            </h2>
            <p className="mt-1 text-label font-medium text-neutral-tertiary">
              {user.sellerProfile
                ? user.sellerProfile.shopName
                : '판매자 프로필 미등록'}
            </p>
          </div>
          {user.sellerProfile && (
            <StatusBadge tone="success">판매자</StatusBadge>
          )}
        </div>
      </section>

      <section className="mt-4 rounded-4xl border bg-card p-5 md:p-6">
        <h2 className="text-card-title font-bold text-foreground">
          연결된 계정
        </h2>
        <dl className="mt-4 divide-y">
          <div className="flex items-center justify-between py-3.5">
            <dt className="text-label font-bold text-neutral-secondary">
              카카오 계정
            </dt>
            <dd className="text-body font-medium text-foreground">
              {user.kakaoEmail}
            </dd>
          </div>
          <div className="flex items-center justify-between py-3.5">
            <dt className="text-label font-bold text-neutral-secondary">
              전화번호
            </dt>
            <dd className="flex items-center gap-2 text-body font-medium text-foreground">
              {user.phone ?? '미인증'}
              {user.phone ? (
                <StatusBadge tone="success">인증 완료</StatusBadge>
              ) : (
                <StatusBadge tone="notice">인증 필요</StatusBadge>
              )}
            </dd>
          </div>
        </dl>
      </section>

      <section className="mt-4 rounded-4xl border bg-card p-5 md:p-6">
        <h2 className="text-card-title font-bold text-foreground">
          서비스 약관
        </h2>
        <ul className="mt-2 divide-y">
          {TERMS_LINKS.map((link) => (
            <li key={link.to}>
              <Link
                to={link.to}
                className="flex items-center justify-between py-3.5 text-label font-medium text-neutral-secondary transition-colors hover:text-foreground"
              >
                {link.label}
                <ChevronRight
                  aria-hidden
                  className="size-4 text-neutral-muted"
                />
              </Link>
            </li>
          ))}
        </ul>
      </section>

      <div className="mt-4 flex flex-col gap-2 sm:flex-row">
        <Button
          variant="outline"
          onClick={handleLogout}
          className="h-12 flex-1 gap-2 rounded-xl text-label font-bold"
        >
          <LogOut aria-hidden className="size-4" />
          로그아웃
        </Button>
        <Link
          to="/my/withdraw"
          className="flex h-12 flex-1 items-center justify-center rounded-xl border border-live/30 text-label font-semibold text-live transition-colors hover:bg-live-surface"
        >
          회원 탈퇴
        </Link>
      </div>
    </AppShell>
  )
}
