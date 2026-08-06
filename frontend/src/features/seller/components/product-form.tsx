import { useState, type FormEvent } from 'react'

import { Button } from '@/components/ui/button'
import { TextAreaField, TextField } from '@/components/ui/field'
import { ImageUploadField } from '@/features/seller/components/image-upload-field'
import {
  ImageUploadError,
  useImageUpload,
} from '@/features/seller/use-image-upload'
import { cn } from '@/lib/utils'
import { toast } from '@/lib/toast'
import { PresignedUrlRequestDtoDomain } from '@/api/generated/model'
import type {
  ProductCreateRequestDto,
  ProductResponseDto,
} from '@/api/generated/model'

/** 폼이 들고 있는 입력값. 서버로 나갈 때 빈 칸은 아래 `toRequestBody` 가 걷어낸다. */
interface FormValues {
  name: string
  referenceUrl: string
  description: string
}

interface FieldErrors {
  name?: string
  referenceUrl?: string
  description?: string
}

/** 서버 규칙: 상품명·설명 모두 `< > " ' \ ;` 를 포함할 수 없다. */
const FORBIDDEN = /[<>"'\\;]/
const NAME_MAX = 30
const DESCRIPTION_MAX = 100

/**
 * 참고 링크에 빠진 `https://` 를 채워 준다.
 *
 * 서버가 `@URL` 로 검사해서 스킴이 없으면 400 이다. 판매자 프로필의 SNS 링크와
 * 같은 처리다.
 */
function normalizeUrl(value: string): string {
  const trimmed = value.trim()
  if (!trimmed) return ''

  return /^https?:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`
}

/** 점 있는 도메인이어야 통과. 주소가 아닌 값이 새어 나가지 않게 막는다. */
function isValidUrl(value: string): boolean {
  if (/\s/.test(value.trim())) return false

  try {
    return new URL(normalizeUrl(value)).hostname.includes('.')
  } catch {
    return false
  }
}

function validate(values: FormValues): FieldErrors {
  const errors: FieldErrors = {}
  const name = values.name.trim()

  if (!name) {
    errors.name = '상품명을 입력해주세요.'
  } else if (name.length > NAME_MAX) {
    errors.name = `상품명은 ${NAME_MAX}자 이하로 입력해주세요.`
  } else if (FORBIDDEN.test(name)) {
    errors.name = `< > " ' \\ ; 문자는 쓸 수 없어요.`
  }

  if (values.referenceUrl.trim() && !isValidUrl(values.referenceUrl)) {
    errors.referenceUrl = 'example.com/item 처럼 사이트 주소를 입력해주세요.'
  }

  const description = values.description.trim()

  if (!description) {
    errors.description = '상태와 구성품을 적어주면 입찰이 잘 붙어요.'
  } else if (description.length > DESCRIPTION_MAX) {
    errors.description = `상품 설명은 ${DESCRIPTION_MAX}자까지 쓸 수 있어요.`
  } else if (FORBIDDEN.test(description)) {
    errors.description = `< > " ' \\ ; 문자는 쓸 수 없어요.`
  }

  return errors
}

/**
 * 빈 선택 필드는 **키 자체를 빼고** 보낸다.
 *
 * 서버가 `referenceUrl` 에 `.*\S.*` 패턴을 걸어 둬서 빈 문자열은 400 이다.
 */
function toRequestBody(
  values: FormValues,
  imageUrl: string | undefined,
): ProductCreateRequestDto {
  const optional = (value: string) => value.trim() || undefined

  return {
    name: values.name.trim(),
    imageUrl,
    referenceUrl: optional(normalizeUrl(values.referenceUrl)),
    description: optional(values.description),
  }
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
 * 폼은 값과 검증을 맡는다. 상품을 어느 API 로 보낼지는 화면이 정한다.
 *
 * **상품 이미지 업로드만 예외로 폼이 직접 부른다.** 고른 파일을 들고 있는 게 폼이고,
 * 4개 화면이 저마다 올리면 같은 코드가 네 벌이 된다. 저장 버튼을 누르면 먼저 S3 에 올리고,
 * 받은 주소를 `imageUrl` 에 넣어 `onSubmit` 으로 넘긴다.
 *
 * 파일을 새로 고르지 않았으면 조회로 받은 `initial.imageUrl` 을 그대로 되돌려 보낸다.
 * 수정은 PUT 전체 교체라 이 값을 빼면 서버에서 `null` 로 덮어써 기존 사진이 지워진다.
 */
export function ProductForm({
  initial,
  submitLabel,
  uploadText,
  submitting,
  className,
  onSubmit,
}: {
  initial?: ProductResponseDto
  submitLabel: string
  /** 이미지 칸 문구. 등록은 "이미지 업로드", 수정은 "상품 이미지 변경". */
  uploadText: string
  submitting: boolean
  /** 카드 겉모양을 바꾼다. 모달 안에서는 테두리·여백이 겹쳐서 지운다. */
  className?: string
  onSubmit: (body: ProductCreateRequestDto) => void
}) {
  const [values, setValues] = useState<FormValues>({
    name: initial?.name ?? '',
    referenceUrl: initial?.referenceUrl ?? '',
    description: initial?.description ?? '',
  })
  const [errors, setErrors] = useState<FieldErrors>({})
  const [imageFile, setImageFile] = useState<File | null>(null)
  /*
   * 이미지 삭제 버튼을 눌렀는지. 새 파일을 고른 것과 다르다 — 지운 뒤에 파일을
   * 골랐다가 다시 지우면 여기는 true 로 남아야 "지움"이 유지된다.
   */
  const [imageRemoved, setImageRemoved] = useState(false)

  const { uploadImage, uploading } = useImageUpload(
    PresignedUrlRequestDtoDomain.PRODUCT,
  )

  const update = (key: keyof FormValues) => (value: string) => {
    setValues((prev) => ({ ...prev, [key]: value }))
    setErrors((prev) => ({ ...prev, [key]: undefined }))
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    const nextErrors = validate(values)
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length > 0) return

    /*
     * 파일을 새로 고르지 않았으면 지금 사진 주소를 그대로 되돌려 보낸다.
     * 지우기를 눌렀으면 undefined 로 둔다 — JSON 에서 키가 빠지고 서버가 null 로
     * 읽어 사진을 지운다.
     */
    let imageUrl = imageRemoved ? undefined : (initial?.imageUrl ?? undefined)

    if (imageFile) {
      try {
        imageUrl = await uploadImage(imageFile)
      } catch (error) {
        toast.error(
          error instanceof ImageUploadError
            ? error.message
            : '이미지를 올리지 못했어요.',
          { description: '잠시 뒤에 다시 시도해 주세요.' },
        )
        return
      }
    }

    onSubmit(toRequestBody(values, imageUrl))
  }

  return (
    <form
      onSubmit={(event) => void handleSubmit(event)}
      className={cn(
        'grid gap-8 rounded-[20px] border bg-card p-8 lg:grid-cols-[360px_minmax(0,1fr)] lg:gap-10',
        className,
      )}
    >
      <ImageUploadField
        label="상품 이미지"
        uploadText={uploadText}
        maxWidth={360}
        onFileChange={setImageFile}
        // 등록된 상품을 고칠 때는 지금 사진이 보여야 한다.
        // 올린 사진이 없으면 비운다. 목업 사진을 대신 넣으면 판매자가 올린 적
        // 없는 사진을 "지금 사진"으로 보게 된다.
        initialUrl={initial?.imageUrl ?? undefined}
        onRemove={() => setImageRemoved(true)}
      />

      <div className="flex flex-col">
        <div className="mb-6">
          <TextField
            label="상품명"
            required
            hint={`구매자에게 보이는 이름이에요. ${NAME_MAX}자까지 가능합니다.`}
            error={errors.name}
            value={values.name}
            maxLength={NAME_MAX}
            placeholder="상품명을 입력해 주세요"
            onChange={(event) => update('name')(event.target.value)}
          />
        </div>

        <div className="mb-6">
          <TextField
            label="참고 링크"
            hint="상품 정보를 확인할 수 있는 주소가 있으면 적어주세요. (선택)"
            error={errors.referenceUrl}
            // `url` 로 두면 브라우저 기본 검증이 https:// 없는 입력을 먼저 막아버려서,
            // 스킴을 붙여 주는 처리가 실행될 기회조차 없다.
            type="text"
            autoComplete="url"
            value={values.referenceUrl}
            placeholder="example.com/item"
            onChange={(event) => update('referenceUrl')(event.target.value)}
          />
        </div>

        <div className="mb-6">
          <TextAreaField
            label="상품 설명"
            required
            hint={`자세할수록 입찰이 잘 붙어요. ${DESCRIPTION_MAX}자까지.`}
            error={errors.description}
            rows={4}
            maxLength={DESCRIPTION_MAX}
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
          disabled={submitting || uploading}
        >
          {uploading
            ? '이미지 올리는 중…'
            : submitting
              ? '저장 중…'
              : submitLabel}
        </Button>
      </div>
    </form>
  )
}
