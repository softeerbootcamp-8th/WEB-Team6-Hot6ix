import { ImagePlus } from 'lucide-react'
import { useState, type FormEvent } from 'react'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'
import type { Product } from '@/mocks/types'

export type ProductDraft = Pick<Product, 'name' | 'category' | 'description'>

const CATEGORIES = [
  '스니커즈',
  '아우터',
  '컬렉터블',
  '시계',
  '가방',
  '잡화',
  '기타',
] as const

interface FieldErrors {
  name?: string
  category?: string
  description?: string
}

function validate(values: ProductDraft): FieldErrors {
  const errors: FieldErrors = {}
  if (!values.name.trim()) errors.name = '상품명을 입력해주세요.'
  else if (values.name.trim().length > 40)
    errors.name = '상품명은 40자 이하로 입력해주세요.'
  if (!values.category) errors.category = '분류를 선택해주세요.'
  if (!values.description.trim())
    errors.description = '상태와 구성품을 적어주면 입찰이 잘 붙어요.'
  return errors
}

/** 상품 등록·수정 공용 폼. */
export function ProductForm({
  initial,
  submitLabel,
  onSubmit,
  onDelete,
  deletable = false,
}: {
  initial?: ProductDraft
  submitLabel: string
  onSubmit: (values: ProductDraft) => void
  onDelete?: () => void
  /** 경매가 시작된 상품은 수정·삭제할 수 없다. */
  deletable?: boolean
}) {
  const [values, setValues] = useState<ProductDraft>(
    initial ?? { name: '', category: '', description: '' },
  )
  const [errors, setErrors] = useState<FieldErrors>({})
  const [saving, setSaving] = useState(false)

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    const nextErrors = validate(values)
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length > 0) return

    setSaving(true)
    // TODO: POST/PUT /api/v1/products 연동 (현재 목업)
    window.setTimeout(() => {
      setSaving(false)
      onSubmit(values)
    }, 500)
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-6">
      <div className="flex flex-col gap-2">
        <span className="text-caption font-semibold text-neutral-secondary">
          상품 이미지
        </span>
        <button
          type="button"
          className="flex h-[160px] flex-col items-center justify-center gap-2 rounded-2xl border border-dashed bg-surface-subtle text-neutral-muted transition-colors hover:border-border-strong hover:text-neutral-tertiary"
        >
          <ImagePlus aria-hidden className="size-6" />
          <span className="text-caption font-medium">
            이미지를 올려주세요 (최대 5장)
          </span>
        </button>
      </div>

      <div className="flex flex-col gap-2">
        <Label
          htmlFor="product-name"
          className="text-caption font-semibold text-neutral-secondary"
        >
          상품명
        </Label>
        <Input
          id="product-name"
          value={values.name}
          placeholder="예) 한정판 조던 스니커즈"
          onChange={(event) => {
            setValues((prev) => ({ ...prev, name: event.target.value }))
            setErrors((prev) => ({ ...prev, name: undefined }))
          }}
          aria-invalid={errors.name !== undefined}
          aria-describedby="product-name-help"
        />
        <p
          id="product-name-help"
          className={cn(
            'text-caption font-normal',
            errors.name ? 'text-live' : 'text-neutral-muted',
          )}
        >
          {errors.name ?? '구매자에게 보이는 이름이에요. 40자까지 가능합니다.'}
        </p>
      </div>

      <fieldset className="flex flex-col gap-2">
        <legend className="mb-2 text-caption font-semibold text-neutral-secondary">
          분류
        </legend>
        <div className="flex flex-wrap gap-2">
          {CATEGORIES.map((category) => (
            <button
              key={category}
              type="button"
              aria-pressed={values.category === category}
              onClick={() => {
                setValues((prev) => ({ ...prev, category }))
                setErrors((prev) => ({ ...prev, category: undefined }))
              }}
              className={cn(
                'rounded-lg border px-3.5 py-2 text-label font-bold transition-colors',
                values.category === category
                  ? 'border-brand-400 bg-brand-50 text-brand-600'
                  : 'font-medium text-neutral-secondary hover:border-border-strong',
              )}
            >
              {category}
            </button>
          ))}
        </div>
        {errors.category && (
          <p className="text-caption font-normal text-live">
            {errors.category}
          </p>
        )}
      </fieldset>

      <div className="flex flex-col gap-2">
        <Label
          htmlFor="product-description"
          className="text-caption font-semibold text-neutral-secondary"
        >
          상품 설명
        </Label>
        <textarea
          id="product-description"
          rows={5}
          value={values.description}
          placeholder="사이즈, 사용감, 구성품처럼 입찰에 필요한 정보를 적어주세요."
          onChange={(event) => {
            setValues((prev) => ({ ...prev, description: event.target.value }))
            setErrors((prev) => ({ ...prev, description: undefined }))
          }}
          aria-invalid={errors.description !== undefined}
          aria-describedby="product-description-help"
          className="w-full rounded-xl border border-input bg-transparent px-4 py-3 text-body font-medium outline-none focus-visible:border-ring"
        />
        <p
          id="product-description-help"
          className={cn(
            'text-caption font-normal',
            errors.description ? 'text-live' : 'text-neutral-muted',
          )}
        >
          {errors.description ?? '자세할수록 입찰이 잘 붙어요.'}
        </p>
      </div>

      <Button type="submit" size="cta" disabled={saving}>
        {saving ? '저장 중…' : submitLabel}
      </Button>

      {onDelete && (
        <Button
          type="button"
          variant="ghost"
          disabled={!deletable}
          onClick={onDelete}
          className="h-11 text-label font-bold text-live hover:bg-live-surface hover:text-live"
        >
          {deletable ? '상품 삭제' : '경매 진행 중인 상품은 삭제할 수 없어요'}
        </Button>
      )}
    </form>
  )
}
