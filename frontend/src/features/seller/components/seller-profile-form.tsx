import { useEffect, useRef, useState, type FormEvent } from 'react'

import { Button } from '@/components/ui/button'
import { TextAreaField, TextField } from '@/components/ui/field'
import { ImageUploadField } from '@/features/seller/components/image-upload-field'
import { formatPhoneNumber } from '@/lib/format'
import { mockAvatarImage } from '@/mocks/images'
import { toast } from '@/lib/toast'
import type { SellerProfile } from '@/lib/session'

interface FieldErrors {
  shopName?: string
  snsUrl?: string
  contact?: string
}

function validate(values: SellerProfile): FieldErrors {
  const errors: FieldErrors = {}

  if (!values.shopName.trim()) {
    errors.shopName = '가게 이름을 입력해주세요.'
  } else if (values.shopName.trim().length < 2) {
    errors.shopName = '가게 이름은 2자 이상이어야 해요.'
  }

  if (values.snsUrl.trim() && !/^https?:\/\//.test(values.snsUrl.trim())) {
    errors.snsUrl = 'http:// 또는 https:// 로 시작하는 주소를 입력해주세요.'
  }

  if (!/^01\d-?\d{3,4}-?\d{4}$/.test(values.contact.replace(/\s/g, ''))) {
    errors.contact = '연락 가능한 휴대폰 번호를 입력해주세요.'
  }

  return errors
}

const FIELDS = [
  {
    key: 'shopName' as const,
    label: '가게 이름',
    placeholder: '예: 오늘의 빈티지',
    hint: '구매자에게 보이는 이름이에요.',
    type: 'text',
    autoComplete: 'organization',
  },
  {
    key: 'snsUrl' as const,
    label: 'SNS 링크',
    placeholder: 'SNS 주소를 입력해 주세요',
    hint: '경매를 안내하는 채널이 있으면 적어주세요. (선택)',
    type: 'url',
    autoComplete: 'url',
  },
  {
    key: 'contact' as const,
    label: '연락처',
    placeholder: '010-1234-5678',
    hint: '낙찰자에게만 공개됩니다.',
    type: 'tel',
    autoComplete: 'tel',
  },
]

/**
 * 판매자 프로필 등록·수정 공용 폼.
 *
 * - `WEB-03 · 판매자 · 프로필 등록` (713:3736)
 * - `WEB-04 · 판매자 · 프로필 수정` (713:3760)
 *
 * 1216×560 카드 한 장. 왼쪽 280 대표 이미지 업로드 + 48 간격 +
 * 오른쪽 824 입력 열이다. 두 프레임은 문구와 채워진 값만 다르다.
 * 등록은 POST, 수정은 PUT(전체 교체)이라 필드 집합이 같다.
 *
 * **삭제 버튼은 두 프레임 어디에도 없어서 두지 않았다.**
 */
export function SellerProfileForm({
  initial,
  submitLabel,
  uploadText,
  onSubmit,
}: {
  initial?: SellerProfile
  submitLabel: string
  /** 이미지 칸 문구. 등록은 "이미지 업로드", 수정은 "프로필 이미지 변경". */
  uploadText: string
  onSubmit: (values: SellerProfile) => void
}) {
  const [values, setValues] = useState<SellerProfile>(
    initial ?? {
      shopName: '',
      snsUrl: '',
      contact: '',
      introduction: '',
      verified: false,
    },
  )
  const [errors, setErrors] = useState<FieldErrors>({})
  const [saving, setSaving] = useState(false)

  // 등록 성공 시 화면을 떠나므로, 남은 목업 타이머는 언마운트 때 정리한다.
  const saveTimer = useRef<number | null>(null)
  useEffect(
    () => () => {
      if (saveTimer.current !== null) window.clearTimeout(saveTimer.current)
    },
    [],
  )

  const update =
    (key: 'shopName' | 'snsUrl' | 'contact' | 'introduction') =>
    (value: string) => {
      // 연락처는 입력하는 동안 하이픈을 자동으로 넣어준다.
      const next = key === 'contact' ? formatPhoneNumber(value) : value
      setValues((prev) => ({ ...prev, [key]: next }))
      setErrors((prev) => ({ ...prev, [key]: undefined }))
    }

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    const nextErrors = validate(values)
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length > 0) return

    setSaving(true)
    // TODO: POST/PUT /api/v1/seller-profiles 연동 (현재 목업)
    // 대표 이미지 업로드 규격은 아직 API 명세에 없어 파일만 들고 있는다.
    saveTimer.current = window.setTimeout(() => {
      setSaving(false)
      toast.success('저장했어요')
      onSubmit(values)
    }, 600)
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="grid gap-8 rounded-[20px] border bg-card p-8 lg:grid-cols-[280px_minmax(0,1fr)] lg:gap-12"
    >
      {/* 대표 이미지 — 280×280 */}
      <ImageUploadField
        label="대표 이미지"
        uploadText={uploadText}
        maxWidth={280}
        // 등록된 프로필을 고칠 때는 지금 사진이 보여야 한다 (목업 사진).
        initialUrl={
          initial ? mockAvatarImage(initial.shopName, 560) : undefined
        }
      />

      {/* 입력 열 — 824 */}
      <div className="flex flex-col">
        {FIELDS.map((field) => (
          <div key={field.key} className="mb-6">
            <TextField
              label={field.label}
              required={field.key !== 'snsUrl'}
              hint={field.hint}
              error={errors[field.key]}
              type={field.type}
              autoComplete={field.autoComplete}
              maxLength={field.key === 'contact' ? 13 : undefined}
              placeholder={field.placeholder}
              value={values[field.key]}
              onChange={(event) => update(field.key)(event.target.value)}
            />
          </div>
        ))}

        <div className="mb-6">
          <TextAreaField
            label="한 줄 소개"
            hint="프로필 카드에 한 줄로 보여요."
            rows={3}
            placeholder="판매자와 상품을 소개해 주세요."
            value={values.introduction}
            onChange={(event) => update('introduction')(event.target.value)}
          />
        </div>

        <Button type="submit" variant="brand" size="form" disabled={saving}>
          {saving ? '저장 중…' : submitLabel}
        </Button>
      </div>
    </form>
  )
}
