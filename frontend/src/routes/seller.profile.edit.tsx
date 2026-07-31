import { createFileRoute, useNavigate } from '@tanstack/react-router'

import { AppShell } from '@/components/layout/page-shell'
import { EmptyState, PageHeader } from '@/components/page-header'
import { SellerProfileForm } from '@/features/seller/components/seller-profile-form'
import { requireMember } from '@/lib/route-guards'
import { sessionStore, useCurrentUser } from '@/lib/session'

export const Route = createFileRoute('/seller/profile/edit')({
  beforeLoad: requireMember,
  component: SellerProfileEditPage,
})

function SellerProfileEditPage() {
  const navigate = useNavigate()
  const user = useCurrentUser()
  const profile = user?.sellerProfile ?? null

  if (!profile) {
    return (
      <AppShell title="판매자 프로필" back className="max-w-[608px]">
        <EmptyState
          title="등록된 판매자 프로필이 없어요"
          description="먼저 프로필을 등록해주세요."
        />
      </AppShell>
    )
  }

  return (
    <AppShell title="판매자 프로필 수정" back className="max-w-[608px]">
      <PageHeader
        title="판매자 프로필 수정"
        description="변경한 내용은 구매자에게 바로 보입니다."
      />

      <section className="mt-6 rounded-4xl border bg-card p-6 md:p-8">
        <SellerProfileForm
          initial={profile}
          submitLabel="저장하기"
          onSubmit={(values) => {
            // TODO: PUT /api/v1/seller-profiles/me 연동 (현재 목업)
            if (user) {
              sessionStore.signIn({ ...user, sellerProfile: values })
            }
          }}
          onDelete={() => {
            // TODO: DELETE /api/v1/seller-profiles/me 연동 (현재 목업)
            if (user) {
              sessionStore.signIn({ ...user, sellerProfile: null })
            }
            void navigate({ to: '/seller' })
          }}
        />
      </section>
    </AppShell>
  )
}
