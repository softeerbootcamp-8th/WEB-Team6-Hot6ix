import { useEffect, useState } from 'react'

import {
  IMAGE_ACCEPT,
  checkImageFile,
} from '@/features/seller/use-image-upload'
import { toast } from '@/lib/toast'
import { cn } from '@/lib/utils'

/**
 * 이미지 업로드 칸.
 *
 * Figma 프로필 등록(280×280)과 상품 등록(360×360)이 같은 모양을 쓴다.
 * 정사각형 파란 박스에 가운데 문구 하나뿐이다.
 *
 * 파일을 실제로 올리는 건 이 칸이 아니라 폼이다. 여기서는 고른 파일을 미리 보여주고
 * `onFileChange` 로 부모에게 넘긴다. 올릴 수 없는 파일은 넘기기 전에 여기서 막는다 —
 * 저장 버튼을 누른 뒤에 알려주면 사용자가 두 번 일하게 된다.
 *
 * 미리보기 URL 은 파일이 바뀌거나 화면을 떠날 때 반드시 해제한다.
 */
/** Tailwind 가 클래스 문자열을 통째로 훑기 때문에 조립하지 않고 다 적어 둔다. */
const MAX_WIDTH_CLASS = {
  240: 'max-w-[240px]',
  280: 'max-w-[280px]',
  360: 'max-w-[360px]',
} as const

export function ImageUploadField({
  label,
  uploadText,
  maxWidth,
  initialUrl,
  onFileChange,
  onRemove,
  disabled = false,
}: {
  label: string
  /** 비어 있을 때 박스 가운데에 뜨는 문구. 수정 화면은 "상품 이미지 변경". */
  uploadText: string
  /**
   * Figma 박스 한 변. 프로필 280, 상품 360, 경매방 커버 240.
   *
   * 커버도 `aspect-square` 를 그대로 쓴다. 커버가 실제로 보이는 자리가 전부
   * 정사각형이라(방 카드 `size-[124px]`, join 화면 `size-16`) 업로드 칸만 가로로
   * 길면 판매자가 자기 사진이 어떻게 잘릴지 모르고 올리게 된다.
   */
  maxWidth: 240 | 280 | 360
  /** 이미 등록된 이미지. 목업 단계에서는 더미 사진이 들어온다. */
  initialUrl?: string
  /** 고른 파일. 지우면 `null`. 넘기지 않으면 미리보기만 한다. */
  onFileChange?: (file: File | null) => void
  /** 기존 이미지를 삭제할 때 호출된다. `onRemove`가 있을 때만 삭제 버튼이 표시된다. */
  onRemove?: () => void
  /**
   * 고를 수 없게 잠근다. 지금 이미지는 그대로 보여준다 — 감추면 무엇이 걸려
   * 있는지조차 못 보게 된다. 바꾸기·지우기 버튼만 사라진다.
   */
  disabled?: boolean
}) {
  const [file, setFile] = useState<File | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [removed, setRemoved] = useState(false)

  const select = (next: File | null) => {
    if (next) {
      const rejected = checkImageFile(next)
      if (rejected) {
        toast.error(rejected)
        return
      }
    }

    setFile(next)
    onFileChange?.(next)
  }

  const handleRemove = () => {
    setRemoved(true)
    onRemove?.()
  }

  useEffect(() => {
    if (!file) {
      setPreviewUrl(null)
      return
    }
    const url = URL.createObjectURL(file)
    setPreviewUrl(url)
    return () => URL.revokeObjectURL(url)
  }, [file])

  const displayUrl = previewUrl ?? (removed ? null : initialUrl)

  return (
    <div>
      <p className="text-[14px] font-bold text-foreground">{label}</p>

      <label
        className={cn(
          'ease-soft mt-2.5 flex aspect-square w-full items-center justify-center overflow-hidden rounded-2xl border border-brand-300 bg-brand-50 transition-colors duration-150',
          MAX_WIDTH_CLASS[maxWidth],
          disabled
            ? 'cursor-not-allowed opacity-60'
            : 'cursor-pointer hover:bg-brand-200',
        )}
      >
        {displayUrl ? (
          <img
            src={displayUrl}
            alt="선택한 이미지 미리보기"
            className="size-full object-cover"
          />
        ) : (
          <span className="text-[16px] font-bold text-brand-500">
            {uploadText}
          </span>
        )}
        <input
          type="file"
          // 서버가 받는 형식만 고를 수 있게 한다. 파일 선택창의 안내일 뿐이라
          // 사용자가 뚫고 들어와도 위 checkImageFile 이 한 번 더 막는다.
          accept={IMAGE_ACCEPT}
          disabled={disabled}
          className="sr-only"
          onChange={(event) => select(event.target.files?.[0] ?? null)}
        />
      </label>

      {!disabled && file && (
        <button
          type="button"
          onClick={() => select(null)}
          className="mt-3 text-[13px] font-bold text-neutral-tertiary hover:underline"
        >
          이미지 지우기
        </button>
      )}

      {!disabled && onRemove && initialUrl && !file && !removed && (
        <button
          type="button"
          onClick={handleRemove}
          className="mt-3 text-[13px] font-bold text-neutral-tertiary hover:underline"
        >
          이미지 삭제
        </button>
      )}
    </div>
  )
}
