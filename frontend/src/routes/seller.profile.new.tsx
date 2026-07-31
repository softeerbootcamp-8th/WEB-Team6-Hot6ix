import { createFileRoute, useNavigate } from '@tanstack/react-router'

import { AppShell } from '@/components/layout/page-shell'
import { PageHeader } from '@/components/page-header'
import { SellerProfileForm } from '@/features/seller/components/seller-profile-form'
import { requireMember } from '@/lib/route-guards'
import { sessionStore, useCurrentUser } from '@/lib/session'

export const Route = createFileRoute('/seller/profile/new')({
  beforeLoad: requireMember,
  component: SellerProfileNewPage,
})

function SellerProfileNewPage() {
  const navigate = useNavigate()
  const user = useCurrentUser()

  return (
    <AppShell title="판매자 프로필 등록" back className="max-w-[608px]">
      <PageHeader
        title="판매자 프로필 등록"
        description="가게 정보를 등록하면 상품을 올리고 경매방을 열 수 있어요."
      />

      <section className="mt-6 rounded-4xl border bg-card p-6 md:p-8">
        <SellerProfileForm
          submitLabel="등록하기"
          onSubmit={(values) => {
            // TODO: POST /api/v1/seller-profiles 연동 (현재 목업)
            if (user) {
              sessionStore.signIn({ ...user, sellerProfile: values })
            }
            void navigate({ to: '/seller' })
          }}
        />
      </section>
    </AppShell>
  )
}
