import { useState, type FormEvent } from 'react'

import { Button } from '@/components/ui/button'
import { TextAreaField, TextField } from '@/components/ui/field'
import { ImageUploadField } from '@/features/seller/components/image-upload-field'
import {
  ImageUploadError,
  useImageUpload,
} from '@/features/seller/use-image-upload'
import { formatPhoneNumber } from '@/lib/format'
import { toast } from '@/lib/toast'
import { PresignedUrlRequestDtoDomain } from '@/api/generated/model'
import type {
  SellerProfileCreateRequestDto,
  SellerProfileResponseDto,
} from '@/api/generated/model'

/** 폼이 들고 있는 입력값. 서버로 나갈 때 빈 칸은 아래 `toRequestBody` 가 걷어낸다. */
interface FormValues {
  storeName: string
  snsUrl: string
  storePhoneNumber: string
  storeDescription: string
}

interface FieldErrors {
  storeName?: string
  snsUrl?: string
  storePhoneNumber?: string
  storeDescription?: string
}

/** 서버 규칙: 2~30자이며 `< > " ' \ ;` 를 포함할 수 없다. */
const FORBIDDEN_IN_NAME = /[<>"'\\;]/
const DESCRIPTION_MAX = 100

/**
 * SNS 주소에 빠진 `https://` 를 채워 준다.
 *
 * 사람들은 `instagram.com/upbid` 처럼 스킴 없이 복사해 오는데, 서버가 `@URL` 로
 * 검사해서 스킴이 없으면 400 이다. 화면에서 붙여 주고 보낸다.
 */
function normalizeSnsUrl(value: string): string {
  const trimmed = value.trim()
  if (!trimmed) return ''

  return /^https?:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`
}

/** 점 있는 도메인이어야 통과. `@아이디` 같은 값이 새어 나가지 않게 막는다. */
function isValidSnsUrl(value: string): boolean {
  if (/\s/.test(value.trim())) return false

  try {
    return new URL(normalizeSnsUrl(value)).hostname.includes('.')
  } catch {
    return false
  }
}

function validate(values: FormValues): FieldErrors {
  const errors: FieldErrors = {}
  const storeName = values.storeName.trim()

  if (!storeName) {
    errors.storeName = '가게 이름을 입력해주세요.'
  } else if (storeName.length < 2 || storeName.length > 30) {
    errors.storeName = '가게 이름은 2~30자로 입력해주세요.'
  } else if (FORBIDDEN_IN_NAME.test(storeName)) {
    errors.storeName = `< > " ' \\ ; 문자는 쓸 수 없어요.`
  }

  if (values.snsUrl.trim() && !isValidSnsUrl(values.snsUrl)) {
    errors.snsUrl = 'instagram.com/아이디 처럼 사이트 주소를 입력해주세요.'
  }

  /*
   * **서버와 같은 규칙을 쓴다** (`SellerProfileCreateRequestDto` 의
   * `^[0-9-]{8,13}$`). 화면만 휴대폰(`01x`)으로 좁혀 두는 바람에, 서버는 받아주는
   * 가게 유선번호(`02-1234-5678`)를 화면이 먼저 막고 있었다. 가게 연락처라
   * 유선번호가 오히려 흔하다.
   */
  if (!/^[0-9-]{8,13}$/.test(values.storePhoneNumber.replace(/\s/g, ''))) {
    errors.storePhoneNumber = '숫자와 - 로 8~13자까지 입력할 수 있어요.'
  }

  if (values.storeDescription.trim().length > DESCRIPTION_MAX) {
    errors.storeDescription = `한 줄 소개는 ${DESCRIPTION_MAX}자까지 쓸 수 있어요.`
  }

  return errors
}

/**
 * 빈 선택 필드는 **키 자체를 빼고** 보낸다.
 *
 * 서버가 `snsUrl` 에 `.*\S.*` 패턴을 걸어 둬서 빈 문자열은 400 이다. null 은 통과한다.
 */
function toRequestBody(
  values: FormValues,
  storeImageUrl: string | undefined,
): SellerProfileCreateRequestDto {
  const optional = (value: string) => value.trim() || undefined

  return {
    storeName: values.storeName.trim(),
    storeImageUrl,
    snsUrl: optional(normalizeSnsUrl(values.snsUrl)),
    storePhoneNumber: optional(values.storePhoneNumber),
    storeDescription: optional(values.storeDescription),
  }
}

const FIELDS = [
  {
    key: 'storeName' as const,
    label: '가게 이름',
    placeholder: '예: 오늘의 빈티지',
    hint: '구매자에게 보이는 이름이에요. 2~30자.',
    type: 'text',
    autoComplete: 'organization',
    maxLength: 30,
  },
  {
    key: 'snsUrl' as const,
    label: 'SNS 링크',
    placeholder: 'instagram.com/upbid',
    hint: '경매를 안내하는 채널이 있으면 적어주세요. (선택)',
    // `url` 로 두면 브라우저 기본 검증이 https:// 없는 입력을 먼저 막아버려서,
    // 스킴을 붙여 주는 처리가 실행될 기회조차 없다.
    type: 'text',
    autoComplete: 'url',
    maxLength: undefined,
  },
  {
    key: 'storePhoneNumber' as const,
    label: '연락처',
    placeholder: '02-1234-5678',
    hint: '낙찰자에게만 공개됩니다.',
    type: 'tel',
    autoComplete: 'tel',
    maxLength: 13,
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
 * **대표 이미지 삭제 버튼은 Figma 두 프레임에 없지만 넣었다.** 한 번 올린 사진을
 * 뺄 방법이 없으면 다른 사진으로 덮는 것 말고는 되돌릴 수가 없다(#213).
 *
 * 폼은 값과 검증을 맡는다. 프로필을 어느 API 로 보낼지는 화면이 정한다.
 *
 * **대표 이미지 업로드만 예외로 폼이 직접 부른다.** 고른 파일을 들고 있는 게 폼이고,
 * 4개 화면이 저마다 올리면 같은 코드가 네 벌이 된다. 저장 버튼을 누르면 먼저 S3 에 올리고,
 * 받은 주소를 `storeImageUrl` 에 넣어 `onSubmit` 으로 넘긴다.
 *
 * 파일을 새로 고르지 않았으면 조회로 받은 `initial.storeImageUrl` 을 그대로 되돌려 보낸다.
 * 수정은 PUT 전체 교체라 이 값을 빼면 서버에서 `null` 로 덮어써 기존 사진이 지워진다.
 */
export function SellerProfileForm({
  initial,
  submitLabel,
  uploadText,
  submitting,
  onSubmit,
}: {
  initial?: SellerProfileResponseDto
  submitLabel: string
  /** 이미지 칸 문구. 등록은 "이미지 업로드", 수정은 "프로필 이미지 변경". */
  uploadText: string
  submitting: boolean
  onSubmit: (body: SellerProfileCreateRequestDto) => void
}) {
  const [values, setValues] = useState<FormValues>({
    storeName: initial?.storeName ?? '',
    snsUrl: initial?.snsUrl ?? '',
    storePhoneNumber: initial?.storePhoneNumber ?? '',
    storeDescription: initial?.storeDescription ?? '',
  })
  const [errors, setErrors] = useState<FieldErrors>({})
  const [imageFile, setImageFile] = useState<File | null>(null)
  /*
   * 이미지 삭제 버튼을 눌렀는지. 새 파일을 고른 것과 다르다 — 지운 뒤에 파일을
   * 골랐다가 다시 지우면 여기는 true 로 남아야 "지움"이 유지된다.
   */
  const [imageRemoved, setImageRemoved] = useState(false)

  const { uploadImage, uploading } = useImageUpload(
    PresignedUrlRequestDtoDomain.SELLER_PROFILE,
  )

  const update = (key: keyof FormValues) => (value: string) => {
    // 연락처는 입력하는 동안 하이픈을 자동으로 넣어준다.
    const next = key === 'storePhoneNumber' ? formatPhoneNumber(value) : value
    setValues((prev) => ({ ...prev, [key]: next }))
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
    let storeImageUrl = imageRemoved
      ? undefined
      : (initial?.storeImageUrl ?? undefined)

    if (imageFile) {
      try {
        storeImageUrl = await uploadImage(imageFile)
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

    onSubmit(toRequestBody(values, storeImageUrl))
  }

  return (
    <form
      onSubmit={(event) => void handleSubmit(event)}
      className="grid grid-cols-[minmax(0,1fr)] gap-8 rounded-[20px] border bg-card p-8 lg:grid-cols-[280px_minmax(0,1fr)] lg:gap-12"
    >
      {/* 대표 이미지 — 280×280 */}
      <ImageUploadField
        label="대표 이미지"
        uploadText={uploadText}
        maxWidth={280}
        onFileChange={setImageFile}
        // 등록된 프로필을 고칠 때는 지금 사진이 보여야 한다.
        // 올린 사진이 없으면 비운다. 목업 사진을 대신 넣으면 판매자가 올린 적
        // 없는 사진을 "지금 사진"으로 보게 된다.
        initialUrl={initial?.storeImageUrl ?? undefined}
        onRemove={() => setImageRemoved(true)}
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
              maxLength={field.maxLength}
              placeholder={field.placeholder}
              value={values[field.key]}
              onChange={(event) => update(field.key)(event.target.value)}
            />
          </div>
        ))}

        <div className="mb-6">
          <TextAreaField
            label="한 줄 소개"
            hint={`프로필 카드에 한 줄로 보여요. ${DESCRIPTION_MAX}자까지.`}
            error={errors.storeDescription}
            rows={3}
            maxLength={DESCRIPTION_MAX}
            placeholder="판매자와 상품을 소개해 주세요."
            value={values.storeDescription}
            onChange={(event) => update('storeDescription')(event.target.value)}
          />
        </div>

        <Button
          type="submit"
          variant="brand"
          size="form"
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
