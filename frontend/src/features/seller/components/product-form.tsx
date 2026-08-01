import { useEffect, useRef, useState, type FormEvent } from 'react'

import { Button } from '@/components/ui/button'
import { SelectField, TextAreaField, TextField } from '@/components/ui/field'
import { ImageUploadField } from '@/features/seller/components/image-upload-field'
import type { Product } from '@/mocks/types'

export type ProductDraft = Pick<
  Product,
  'name' | 'category' | 'description' | 'productUrl'
>

const CATEGORIES = [
  '스니커즈',
  '아우터',
  '컬렉터블',
  '시계',
  '가방',
  '잡화',
  '카메라',
  '기타',
] as const

interface FieldErrors {
  name?: string
  category?: string
  productUrl?: string
  description?: string
}

function validate(values: ProductDraft): FieldErrors {
  const errors: FieldErrors = {}

  if (!values.name.trim()) errors.name = '상품명을 입력해주세요.'
  else if (values.name.trim().length > 40)
    errors.name = '상품명은 40자 이하로 입력해주세요.'

  if (!values.category) errors.category = '분류를 선택해주세요.'

  const url = values.productUrl?.trim() ?? ''
  if (url && !/^https?:\/\//.test(url))
    errors.productUrl = 'http:// 또는 https:// 로 시작하는 주소를 입력해주세요.'

  if (!values.description.trim())
    errors.description = '상태와 구성품을 적어주면 입찰이 잘 붙어요.'

  return errors
}

/**
 * 상품 등록·수정 공용 폼.
 *
 * - `WEB-06 · 판매자 · 상품 등록` (713:3862)
 * - `WEB-07 · 판매자 · 상품 수정` (713:3883)
 *
 * 1216×560 카드 한 장. 왼쪽 360 이미지 업로드 + 40 간격 + 오른쪽 752 입력 열
 * (상품명 52 / 참고 링크 52 / 상품 설명 126 / 버튼 56)이다. 두 프레임은
 * 문구와 채워진 값만 다르고 레이아웃은 같다.
 *
 * **분류는 Figma 두 프레임 어디에도 없다.** 그런데 상품 관리(WEB-05)가
 * 상품명 아래에 분류를 보여주므로 값을 받을 곳이 필요해 남겨 두었다.
 */
export function ProductForm({
  initial,
  submitLabel,
  uploadText,
  onSubmit,
}: {
  initial?: ProductDraft
  submitLabel: string
  /** 이미지 칸 문구. 등록은 "이미지 업로드", 수정은 "상품 이미지 변경". */
  uploadText: string
  onSubmit: (values: ProductDraft) => void
}) {
  const [values, setValues] = useState<ProductDraft>(
    initial ?? { name: '', category: '', description: '', productUrl: '' },
  )
  const [errors, setErrors] = useState<FieldErrors>({})
  const [saving, setSaving] = useState(false)

  // 저장 성공 시 화면을 떠나므로, 남은 목업 타이머는 언마운트 때 정리한다.
  const saveTimer = useRef<number | null>(null)
  useEffect(
    () => () => {
      if (saveTimer.current !== null) window.clearTimeout(saveTimer.current)
    },
    [],
  )

  const update = (key: keyof ProductDraft) => (value: string) => {
    setValues((prev) => ({ ...prev, [key]: value }))
    setErrors((prev) => ({ ...prev, [key]: undefined }))
  }

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    const nextErrors = validate(values)
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length > 0) return

    setSaving(true)
    // TODO: POST/PUT /api/v1/products 연동 (현재 목업)
    saveTimer.current = window.setTimeout(() => {
      setSaving(false)
      onSubmit(values)
    }, 500)
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="grid gap-8 rounded-[20px] border bg-card p-8 lg:grid-cols-[360px_minmax(0,1fr)] lg:gap-10"
    >
      <ImageUploadField
        label="상품 이미지"
        uploadText={uploadText}
        maxWidth={360}
      />

      <div className="flex flex-col">
        <div className="mb-6">
          <TextField
            label="상품명"
            required
            hint="구매자에게 보이는 이름이에요. 40자까지 가능합니다."
            error={errors.name}
            value={values.name}
            placeholder="상품명을 입력해 주세요"
            onChange={(event) => update('name')(event.target.value)}
          />
        </div>

        {/* Figma 에 없는 칸. 상품 관리 표가 분류를 보여줘서 값을 받아둔다. */}
        <div className="mb-6">
          <SelectField
            label="분류"
            required
            error={errors.category}
            value={values.category}
            onChange={(event) => update('category')(event.target.value)}
          >
            <option value="">분류를 선택해 주세요</option>
            {CATEGORIES.map((category) => (
              <option key={category} value={category}>
                {category}
              </option>
            ))}
          </SelectField>
        </div>

        <div className="mb-6">
          <TextField
            label="참고 링크"
            hint="상품 정보를 확인할 수 있는 주소가 있으면 적어주세요. (선택)"
            error={errors.productUrl}
            type="url"
            value={values.productUrl ?? ''}
            placeholder="상품 정보를 확인할 수 있는 URL"
            onChange={(event) => update('productUrl')(event.target.value)}
          />
        </div>

        <div className="mb-6">
          <TextAreaField
            label="상품 설명"
            required
            hint="자세할수록 입찰이 잘 붙어요."
            error={errors.description}
            rows={4}
            className="h-[126px]"
            value={values.description}
            placeholder="상품의 상태와 특징을 입력해 주세요."
            onChange={(event) => update('description')(event.target.value)}
          />
        </div>

        <Button
          type="submit"
          variant="brand"
          size="cta"
          className="mt-auto"
          disabled={saving}
        >
          {saving ? '저장 중…' : submitLabel}
        </Button>
      </div>
    </form>
  )
}
