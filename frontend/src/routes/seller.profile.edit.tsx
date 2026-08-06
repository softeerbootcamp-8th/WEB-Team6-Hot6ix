import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { AppShell } from '@/components/layout/page-shell'
import { Button } from '@/components/ui/button'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { EmptyState, PageHeader } from '@/components/page-header'
import { RouteError, RoutePending } from '@/components/route-states'
import { SellerProfileForm } from '@/features/seller/components/seller-profile-form'
import { requireMember } from '@/lib/route-guards'
import { toProfileErrorMessage } from '@/features/seller/profile-error'
import { toast } from '@/lib/toast'
import { useMySellerProfile } from '@/features/seller/use-my-seller-profile'
import {
  getGetMyProfileQueryKey,
  useDelete,
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
                /*
                 * 폼이 만든 값을 그대로 보낸다. 여기서 `storeImageUrl` 을 조회 값으로
                 * 덮으면 방금 올린 주소가 지워져서, 저장은 성공하는데 사진만 안 바뀐다(#213).
                 *
                 * PUT 전체 교체라 값을 빼면 서버가 지우는 건 맞다. 그건 폼이 막는다 —
                 * 파일을 새로 안 고르면 조회로 받은 주소를 그대로 되돌려 넣는다
                 * (`seller-profile-form.tsx:204`).
                 */
                data: body,
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

      <DeleteProfileSection />
    </AppShell>
  )
}

/**
 * 판매자 프로필 삭제 (이슈 #141).
 *
 * 판매자 홈이 아니라 수정 화면 아래에만 둔다 — 홈은 매번 지나는 자리라 실수로 누르기 쉽다.
 *
 * 삭제한 뒤 따로 "미등록" 분기를 만들지 않는다. `useMySellerProfile()` 이 404 를 이미
 * "아직 등록하지 않았다" 로 갈라 주므로, 쿼리를 무효화하고 `/seller` 로 보내면 미등록
 * 화면이 저절로 나온다.
 *
 * 진행 중인 경매방이 있으면 서버가 409(3003)로 거절한다. 그 상태를 화면에서 미리 알 수
 * 없어(방 목록을 여기서 받지 않는다) 버튼을 잠그지 않고 응답으로 안내한다.
 */
function DeleteProfileSection() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [confirming, setConfirming] = useState(false)
  const deleteProfile = useDelete()

  return (
    <section className="mt-8 rounded-[20px] border border-live/40 bg-card p-6">
      <h2 className="text-[15px] font-bold text-foreground">
        판매자 프로필 삭제
      </h2>
      <p className="mt-2 text-[13px] leading-[1.6] font-medium text-neutral-tertiary">
        삭제하면 구매자에게 가게 정보가 보이지 않아요. 등록한 상품과 경매방은
        남아 있고, 다시 등록하면 그대로 돌아와요.
      </p>

      <Button
        type="button"
        variant="dangerOutline"
        className="mt-4 h-11 w-full rounded-xl text-[13px] font-bold md:w-[148px]"
        onClick={() => setConfirming(true)}
      >
        프로필 삭제
      </Button>

      <ConfirmDialog
        open={confirming}
        title="판매자 프로필을 삭제할까요?"
        description="구매자에게 가게 이름·연락처가 더 이상 보이지 않아요. 진행 중인 경매방이 있으면 삭제할 수 없어요."
        confirmLabel="삭제"
        tone="danger"
        pending={deleteProfile.isPending}
        onCancel={() => setConfirming(false)}
        onConfirm={() => {
          deleteProfile.mutate(undefined, {
            // 서버가 지운 뒤에만 성공으로 알린다.
            onSuccess: () => {
              void queryClient.invalidateQueries({
                queryKey: getGetMyProfileQueryKey(),
              })
              setConfirming(false)
              toast.success('판매자 프로필을 삭제했어요')
              void navigate({ to: '/seller' })
            },
            onError: (mutationError) => {
              const { title, description } =
                toProfileErrorMessage(mutationError)
              setConfirming(false)
              toast.error(title, { description })
            },
          })
        }}
      />
    </section>
  )
}
