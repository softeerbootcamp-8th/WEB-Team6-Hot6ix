import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'

import { AppShell } from '@/components/layout/page-shell'
import { Button } from '@/components/ui/button'
import { ImageUploadField } from '@/features/seller/components/image-upload-field'
import { TextField } from '@/components/ui/field'
import { requireMember } from '@/lib/route-guards'
import { sessionStore, useCurrentUser } from '@/lib/session'
import { toast } from '@/lib/toast'
import { useUpdateMe } from '@/api/generated/유저/유저'
import {
  ImageUploadError,
  useImageUpload,
} from '@/features/seller/use-image-upload'
import { PresignedUrlRequestDtoDomain } from '@/api/generated/model'

/**
 * 내 프로필 수정.
 *
 * **판매자 프로필(`/seller/profile/edit`)과 다른 화면이다.**
 * 여기는 서비스 전체에서 쓰는 내 계정 정보(닉네임·프로필 사진)를 고친다.
 * 판매자 프로필은 가게 이름·SNS·연락처처럼 "파는 사람"으로서의 정보다.
 * 판매자가 아니어도 이 화면은 쓸 수 있다.
 */
export const Route = createFileRoute('/my/profile/edit')({
  beforeLoad: requireMember,
  component: MyProfileEditPage,
})

function MyProfileEditPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const user = useCurrentUser()

  const [nickname, setNickname] = useState(user?.nickname ?? '')
  const [profileFile, setProfileFile] = useState<File | null>(null)
  const [profileImageUrl, setProfileImageUrl] = useState<string | null>(
    user?.profileImageUrl ?? null,
  )
  const [nicknameError, setNicknameError] = useState<string | undefined>()

  const { uploadImage, uploading } = useImageUpload(
    PresignedUrlRequestDtoDomain.USER_PROFILE,
  )
  const updateMe = useUpdateMe()

  const saving = uploading || updateMe.isPending

  if (!user) return null

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()

    const trimmed = nickname.trim()
    if (trimmed.length < 2) {
      setNicknameError('닉네임은 2자 이상이어야 해요.')
      return
    }
    if (trimmed.length > 10) {
      setNicknameError('닉네임은 10자 이하로 입력해주세요.')
      return
    }

    let finalImageUrl: string | null = profileImageUrl

    if (profileFile) {
      try {
        finalImageUrl = await uploadImage(profileFile)
      } catch (err) {
        if (err instanceof ImageUploadError) {
          toast.error(err.message)
        } else {
          toast.error('이미지를 올리지 못했어요.')
        }
        return
      }
    }

    updateMe.mutate(
      { data: { nickname: trimmed, profileImageUrl: finalImageUrl } },
      {
        onSuccess(response) {
          const updated = response.data
          if (updated) {
            sessionStore.signIn({
              ...user,
              nickname: updated.nickname ?? user.nickname,
              profileImageUrl: updated.profileImageUrl ?? null,
            })
          }
          // 루트 beforeLoad 가 ['session', 'me'] 캐시로 sessionStore 를 덮어쓰므로
          // 이 키를 stale 로 만들어 다음 navigation 에서 서버 재요청이 일어나게 한다.
          void queryClient.invalidateQueries({ queryKey: ['session', 'me'] })
          toast.success('프로필을 저장했어요')
          void navigate({ to: '/my' })
        },
        onError() {
          toast.error('저장에 실패했어요. 다시 시도해 주세요.')
        },
      },
    )
  }

  return (
    <AppShell title="내 프로필 수정" back className="max-w-[1280px]">
      <div className="hidden md:block">
        <h1 className="text-[28px] font-extrabold text-foreground">
          내 프로필 수정
        </h1>
        <p className="mt-2 text-[14px] font-medium text-neutral-tertiary">
          경매방에서 다른 참여자에게 보이는 정보예요.
        </p>
      </div>

      <form
        onSubmit={(e) => void handleSubmit(e)}
        className="mt-4 grid gap-8 rounded-[20px] border bg-card p-6 md:p-8 lg:grid-cols-[280px_minmax(0,1fr)] lg:gap-12"
      >
        <ImageUploadField
          label="프로필 사진"
          uploadText="사진 변경"
          maxWidth={280}
          initialUrl={user.profileImageUrl ?? undefined}
          onFileChange={setProfileFile}
          onRemove={() => setProfileImageUrl(null)}
        />

        <div className="flex flex-col">
          <div className="mb-6">
            <TextField
              label="닉네임"
              required
              hint="입찰 기록과 리더보드에 이 이름이 보여요. 2~10자."
              error={nicknameError}
              value={nickname}
              placeholder="닉네임을 입력해 주세요"
              onChange={(event) => {
                setNickname(event.target.value)
                setNicknameError(undefined)
              }}
            />
          </div>

          {/* 카카오·전화번호는 여기서 못 바꾼다. 마이페이지에서 상태만 본다. */}
          <div className="mb-6 rounded-2xl border bg-surface-subtle px-5 py-4">
            <p className="text-[12px] font-semibold text-neutral-tertiary">
              연결된 계정
            </p>
            <p className="mt-2 text-[13px] font-medium text-foreground">
              카카오 계정과 인증 전화번호는 바꿀 수 없어요.
            </p>
          </div>

          <Button
            type="submit"
            variant="brand"
            size="form"
            className="mt-auto"
            disabled={saving}
          >
            {saving ? '저장 중…' : '저장하기'}
          </Button>
        </div>
      </form>
    </AppShell>
  )
}
