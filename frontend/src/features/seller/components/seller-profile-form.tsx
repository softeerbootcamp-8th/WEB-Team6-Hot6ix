import { useState, type FormEvent } from 'react'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'
import type { SellerProfile } from '@/lib/session'

interface FieldErrors {
  shopName?: string
  snsUrl?: string
  contact?: string
}

function validate(values: SellerProfile): FieldErrors {
  const errors: FieldErrors = {}

  if (!values.shopName.trim()) {
    errors.shopName = '가게명을 입력해주세요.'
  } else if (values.shopName.trim().length < 2) {
    errors.shopName = '가게명은 2자 이상이어야 해요.'
  }

  if (values.snsUrl.trim() && !/^https?:\/\//.test(values.snsUrl.trim())) {
    errors.snsUrl = 'http:// 또는 https:// 로 시작하는 주소를 입력해주세요.'
  }

  if (!/^01\d-?\d{3,4}-?\d{4}$/.test(values.contact.replace(/\s/g, ''))) {
    errors.contact = '연락 가능한 휴대폰 번호를 입력해주세요.'
  }

  return errors
}

/**
 * 판매자 프로필 등록·수정 공용 폼.
 *
 * 등록은 POST, 수정은 PUT(전체 교체)이라 같은 필드 집합을 쓴다.
 */
export function SellerProfileForm({
  initial,
  submitLabel,
  onSubmit,
  onDelete,
}: {
  initial?: SellerProfile
  submitLabel: string
  onSubmit: (values: SellerProfile) => void
  onDelete?: () => void
}) {
  const [values, setValues] = useState<SellerProfile>(
    initial ?? { shopName: '', snsUrl: '', contact: '' },
  )
  const [errors, setErrors] = useState<FieldErrors>({})
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)

  const update = (key: keyof SellerProfile) => (value: string) => {
    setValues((prev) => ({ ...prev, [key]: value }))
    setErrors((prev) => ({ ...prev, [key]: undefined }))
    setSaved(false)
  }

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    const nextErrors = validate(values)
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length > 0) return

    setSaving(true)
    // TODO: POST/PUT /api/v1/seller-profiles 연동 (현재 목업)
    window.setTimeout(() => {
      setSaving(false)
      setSaved(true)
      onSubmit(values)
    }, 600)
  }

  const FIELDS = [
    {
      key: 'shopName' as const,
      label: '가게명',
      placeholder: '예) 승민이네 빈티지',
      hint: '구매자에게 보이는 이름이에요.',
      type: 'text',
      autoComplete: 'organization',
    },
    {
      key: 'snsUrl' as const,
      label: 'SNS 주소',
      placeholder: 'https://instagram.com/upbid',
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

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-6">
      {FIELDS.map((field) => (
        <div key={field.key} className="flex flex-col gap-2">
          <Label
            htmlFor={field.key}
            className="text-caption font-semibold text-neutral-secondary"
          >
            {field.label}
          </Label>
          <Input
            id={field.key}
            type={field.type}
            autoComplete={field.autoComplete}
            placeholder={field.placeholder}
            value={values[field.key]}
            onChange={(event) => update(field.key)(event.target.value)}
            aria-invalid={errors[field.key] !== undefined}
            aria-describedby={`${field.key}-help`}
          />
          <p
            id={`${field.key}-help`}
            className={cn(
              'text-caption font-normal',
              errors[field.key] ? 'text-live' : 'text-neutral-muted',
            )}
          >
            {errors[field.key] ?? field.hint}
          </p>
        </div>
      ))}

      {saved && (
        <p
          role="status"
          className="rounded-xl bg-success-surface px-4 py-3 text-label font-bold text-success"
        >
          저장했어요.
        </p>
      )}

      <Button type="submit" size="cta" disabled={saving}>
        {saving ? '저장 중…' : submitLabel}
      </Button>

      {onDelete && (
        <Button
          type="button"
          variant="ghost"
          onClick={onDelete}
          className="h-11 text-label font-bold text-live hover:bg-live-surface hover:text-live"
        >
          판매자 프로필 삭제
        </Button>
      )}
    </form>
  )
}
