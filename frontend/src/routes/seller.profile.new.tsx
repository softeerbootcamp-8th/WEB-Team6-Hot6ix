import { createFileRoute, useNavigate } from '@tanstack/react-router'

import { AppShell } from '@/components/layout/page-shell'
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
    <AppShell title="판매자 프로필 등록" back className="max-w-[1280px]">
      <div className="hidden md:block">
        <h1 className="text-[28px] font-extrabold text-foreground">
          판매자 프로필 등록
        </h1>
        <p className="mt-2 text-[14px] font-medium text-neutral-tertiary">
          판매를 시작하기 전에 기본 정보를 등록해 주세요.
        </p>
      </div>

      <div className="mt-4">
        <SellerProfileForm
          submitLabel="등록하기"
          uploadText="이미지 업로드"
          onSubmit={(values) => {
            // TODO: POST /api/v1/seller-profiles 연동 (현재 목업)
            if (user) {
              sessionStore.signIn({ ...user, sellerProfile: values })
            }
            void navigate({ to: '/seller' })
          }}
        />
      </div>
    </AppShell>
  )
}
