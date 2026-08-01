import { createFileRoute, useNavigate } from '@tanstack/react-router'

import { AppShell } from '@/components/layout/page-shell'
import { EmptyState, PageHeader } from '@/components/page-header'
import { SellerProfileForm } from '@/features/seller/components/seller-profile-form'
import { requireMember } from '@/lib/route-guards'
import { sessionStore, useCurrentUser } from '@/lib/session'

/** 판매자 프로필 수정 (Figma `WEB-04 · 판매자 · 프로필 수정`, 713:3760). */
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
      <AppShell title="판매자 프로필" back className="max-w-[1280px]">
        <EmptyState
          title="등록된 판매자 프로필이 없어요"
          description="먼저 프로필을 등록해주세요."
        />
      </AppShell>
    )
  }

  return (
    <AppShell title="판매자 프로필 수정" back className="max-w-[1280px]">
      <PageHeader
        title="판매자 프로필 수정"
        description="구매자에게 공개되는 정보를 관리하세요."
      />

      <div className="mt-4">
        <SellerProfileForm
          initial={profile}
          submitLabel="수정 내용 저장"
          uploadText="프로필 이미지 변경"
          onSubmit={(values) => {
            // TODO: PUT /api/v1/seller-profiles/me 연동 (현재 목업)
            if (user) {
              sessionStore.signIn({ ...user, sellerProfile: values })
            }
            // 저장했으면 원래 보던 판매자 정보로 돌아간다.
            void navigate({ to: '/seller' })
          }}
        />
      </div>
    </AppShell>
  )
}
