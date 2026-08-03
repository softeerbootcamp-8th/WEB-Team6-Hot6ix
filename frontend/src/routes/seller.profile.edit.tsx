import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'

import { AppShell } from '@/components/layout/page-shell'
import { EmptyState, PageHeader } from '@/components/page-header'
import { RouteError, RoutePending } from '@/components/route-states'
import { SellerProfileForm } from '@/features/seller/components/seller-profile-form'
import { requireMember } from '@/lib/route-guards'
import { toProfileErrorMessage } from '@/features/seller/profile-error'
import { toast } from '@/lib/toast'
import { useMySellerProfile } from '@/features/seller/use-my-seller-profile'
import {
  getGetMyProfileQueryKey,
  useUpdate,
} from '@/api/generated/판매자-프로필/판매자-프로필'

/** 판매자 프로필 수정 (Figma `WEB-04 · 판매자 · 프로필 수정`, 713:3760). */
export const Route = createFileRoute('/seller/profile/edit')({
  beforeLoad: requireMember,
  component: SellerProfileEditPage,
})

function SellerProfileEditPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { profile, isPending, notFound, isError, error, refetch } =
    useMySellerProfile()
  const updateProfile = useUpdate()

  if (isPending) return <RoutePending />

  if (isError && error) {
    return <RouteError error={error} reset={() => void refetch()} />
  }

  if (notFound || !profile) {
    return (
      <AppShell title="판매자 프로필" back className="max-w-[1280px]">
        <EmptyState
          title="등록된 판매자 프로필이 없어요"
          description="먼저 프로필을 등록해주세요."
          action={
            <Link
              to="/seller/profile/new"
              className="ease-soft inline-block rounded-2xl bg-brand-500 px-6 py-3.5 text-[15px] font-bold text-white transition-all duration-150 hover:opacity-90 active:scale-[0.98]"
            >
              판매자 프로필 등록하기
            </Link>
          }
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
          submitting={updateProfile.isPending}
          onSubmit={(body) => {
            updateProfile.mutate(
              {
                // PUT 은 전체 교체다. 폼이 다루지 않는 대표 이미지를 그대로
                // 돌려보내지 않으면 저장할 때마다 서버 값이 지워진다.
                data: { ...body, storeImageUrl: profile.storeImageUrl },
              },
              {
                // 서버가 저장한 뒤에만 성공으로 알린다.
                onSuccess: () => {
                  void queryClient.invalidateQueries({
                    queryKey: getGetMyProfileQueryKey(),
                  })
                  toast.success('수정 내용을 저장했어요')
                  // 저장했으면 원래 보던 판매자 정보로 돌아간다.
                  void navigate({ to: '/seller' })
                },
                onError: (mutationError) => {
                  const { title, description } =
                    toProfileErrorMessage(mutationError)
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
