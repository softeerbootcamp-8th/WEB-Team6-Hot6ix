import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'

import { AppShell } from '@/components/layout/page-shell'
import { SellerProfileForm } from '@/features/seller/components/seller-profile-form'
import { requireMember } from '@/lib/route-guards'
import { toProfileErrorMessage } from '@/features/seller/profile-error'
import { toast } from '@/lib/toast'
import {
  getGetMyProfileQueryKey,
  useCreate,
} from '@/api/generated/판매자-프로필/판매자-프로필'

export const Route = createFileRoute('/seller/profile/new')({
  beforeLoad: requireMember,
  component: SellerProfileNewPage,
})

function SellerProfileNewPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const createProfile = useCreate()

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
          submitting={createProfile.isPending}
          onSubmit={(body) => {
            createProfile.mutate(
              { data: body },
              {
                // 서버가 등록한 뒤에만 성공으로 알린다.
                onSuccess: () => {
                  void queryClient.invalidateQueries({
                    queryKey: getGetMyProfileQueryKey(),
                  })
                  toast.success('판매자 프로필을 등록했어요')
                  void navigate({ to: '/seller' })
                },
                onError: (error) => {
                  const { title, description } = toProfileErrorMessage(error)
                  toast.error(title, { description })
                },
              },
            )
          }}
        />
      </div>
    </AppShell>
  )
}
