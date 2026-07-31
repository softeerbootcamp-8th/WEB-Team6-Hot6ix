import { createFileRoute, Link } from '@tanstack/react-router'
import { Boxes, ChevronRight, Gavel, Receipt, Store } from 'lucide-react'

import { AppShell } from '@/components/layout/page-shell'
import { EmptyState, PageHeader } from '@/components/page-header'
import { MOCK_PRODUCTS, MOCK_ROOMS } from '@/mocks/data'
import { StatusBadge } from '@/components/status-badge'
import { requireMember } from '@/lib/route-guards'
import { useCurrentUser } from '@/lib/session'

export const Route = createFileRoute('/seller/')({
  beforeLoad: requireMember,
  component: SellerHomePage,
})

const MENUS = [
  {
    to: '/seller/profile/edit',
    icon: Store,
    title: '판매자 프로필',
    description: '가게명·SNS·연락처 관리',
  },
  {
    to: '/seller/products',
    icon: Boxes,
    title: '상품 관리',
    description: '상품 등록·수정·삭제',
  },
  {
    to: '/seller/rooms/new',
    icon: Gavel,
    title: '경매방 만들기',
    description: '방 정보·물품 편성·입찰 규칙',
  },
  {
    to: '/trades',
    icon: Receipt,
    title: '판매 결과',
    description: '낙찰 후보 관리와 거래 처리',
  },
] as const

function SellerHomePage() {
  const user = useCurrentUser()
  const profile = user?.sellerProfile ?? null

  if (!profile) {
    return (
      <AppShell title="판매자 정보">
        <PageHeader
          title="판매자 정보"
          description="판매자 프로필을 등록하면 경매방을 만들 수 있어요."
        />
        <div className="mt-8">
          <EmptyState
            icon={<Store className="size-8" />}
            title="아직 판매자 프로필이 없어요"
            description="가게명과 연락처를 등록하면 상품을 올리고 경매방을 열 수 있어요."
            hint="등록한 정보는 언제든 수정할 수 있습니다."
            action={
              <Link
                to="/seller/profile/new"
                className="inline-block rounded-2xl bg-primary px-6 py-3.5 text-card-title font-bold text-primary-foreground transition-opacity hover:opacity-90"
              >
                판매자 프로필 등록하기
              </Link>
            }
          />
        </div>
      </AppShell>
    )
  }

  const myRooms = MOCK_ROOMS.filter((room) => room.role === 'SELLER')
  const liveRooms = myRooms.filter((room) => room.status === 'LIVE')

  return (
    <AppShell title="판매자 정보">
      <PageHeader
        title="판매자 정보"
        description="가게 정보와 판매 관리 메뉴를 한곳에서 확인하세요."
      />

      <section className="mt-8 rounded-4xl border bg-card p-5 md:p-6">
        <div className="flex flex-wrap items-start gap-4">
          <span
            aria-hidden
            className="flex size-14 items-center justify-center rounded-full bg-brand-50 text-[20px] font-extrabold text-brand-500"
          >
            {profile.shopName.slice(0, 1)}
          </span>

          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="text-room-title font-bold text-foreground">
                {profile.shopName}
              </h2>
              <StatusBadge tone="success">판매자</StatusBadge>
            </div>
            <p className="mt-1.5 text-label font-medium text-neutral-tertiary">
              {profile.snsUrl || 'SNS 미등록'}
            </p>
            <p className="mt-1 text-caption font-normal text-neutral-muted">
              연락처 {profile.contact}
            </p>
          </div>

          <Link
            to="/seller/profile/edit"
            className="rounded-lg border px-4 py-2 text-label font-semibold text-neutral-secondary transition-colors hover:border-border-strong"
          >
            프로필 수정
          </Link>
        </div>

        <dl className="mt-6 grid grid-cols-3 gap-3">
          {[
            { label: '등록 상품', value: `${MOCK_PRODUCTS.length}개` },
            { label: '개설 경매방', value: `${myRooms.length}개` },
            { label: '진행 중', value: `${liveRooms.length}개` },
          ].map((stat) => (
            <div key={stat.label} className="rounded-2xl bg-surface-subtle p-4">
              <dt className="text-caption font-normal text-neutral-muted">
                {stat.label}
              </dt>
              <dd className="mt-1.5 text-body-strong font-semibold text-foreground">
                {stat.value}
              </dd>
            </div>
          ))}
        </dl>
      </section>

      <ul className="mt-4 grid gap-3 sm:grid-cols-2">
        {MENUS.map((menu) => (
          <li key={menu.to}>
            <Link
              to={menu.to}
              className="flex items-center gap-4 rounded-3xl border bg-card p-5 transition-colors hover:border-border-strong"
            >
              <span
                aria-hidden
                className="flex size-11 shrink-0 items-center justify-center rounded-full bg-brand-50 text-brand-500"
              >
                <menu.icon className="size-5" />
              </span>
              <span className="min-w-0 flex-1">
                <span className="block text-card-title font-bold text-foreground">
                  {menu.title}
                </span>
                <span className="mt-1 block text-caption font-normal text-neutral-tertiary">
                  {menu.description}
                </span>
              </span>
              <ChevronRight aria-hidden className="size-4 text-neutral-muted" />
            </Link>
          </li>
        ))}
      </ul>
    </AppShell>
  )
}
