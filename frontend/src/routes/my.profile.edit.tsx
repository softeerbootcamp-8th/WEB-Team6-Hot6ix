import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useEffect, useRef, useState, type FormEvent } from 'react'

import { AppShell } from '@/components/layout/page-shell'
import { Button } from '@/components/ui/button'
import { ImageUploadField } from '@/features/seller/components/image-upload-field'
import { TextField } from '@/components/ui/field'
import { requireMember } from '@/lib/route-guards'
import { sessionStore, useCurrentUser } from '@/lib/session'
import { toast } from '@/lib/toast'

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
  const user = useCurrentUser()

  const [nickname, setNickname] = useState(user?.nickname ?? '')
  const [error, setError] = useState<string | undefined>()
  const [saving, setSaving] = useState(false)

  // 저장 뒤 화면을 떠나므로 남은 목업 타이머는 언마운트 때 정리한다.
  const timer = useRef<number | null>(null)
  useEffect(
    () => () => {
      if (timer.current !== null) window.clearTimeout(timer.current)
    },
    [],
  )

  if (!user) return null

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()

    const trimmed = nickname.trim()
    if (trimmed.length < 2) {
      setError('닉네임은 2자 이상이어야 해요.')
      return
    }
    if (trimmed.length > 12) {
      setError('닉네임은 12자 이하로 입력해주세요.')
      return
    }

    setSaving(true)
    // TODO: PATCH /api/v1/users/me 연동 (현재 목업)
    timer.current = window.setTimeout(() => {
      sessionStore.signIn({ ...user, nickname: trimmed })
      toast.success('프로필을 저장했어요')
      void navigate({ to: '/my' })
    }, 500)
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
        onSubmit={handleSubmit}
        className="mt-4 grid gap-8 rounded-[20px] border bg-card p-6 md:p-8 lg:grid-cols-[280px_minmax(0,1fr)] lg:gap-12"
      >
        <ImageUploadField
          label="프로필 사진"
          uploadText="사진 변경"
          maxWidth={280}
        />

        <div className="flex flex-col">
          <div className="mb-6">
            <TextField
              label="닉네임"
              required
              hint="입찰 기록과 리더보드에 이 이름이 보여요. 2~12자."
              error={error}
              value={nickname}
              placeholder="닉네임을 입력해 주세요"
              onChange={(event) => {
                setNickname(event.target.value)
                setError(undefined)
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
