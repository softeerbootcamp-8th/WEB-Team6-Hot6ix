import { useEffect, useState } from 'react'

import { cn } from '@/lib/utils'

/**
 * 이미지 업로드 칸.
 *
 * Figma 프로필 등록(280×280)과 상품 등록(360×360)이 같은 모양을 쓴다.
 * 정사각형 파란 박스에 가운데 문구 하나뿐이다.
 *
 * 업로드 API 규격이 아직 정해지지 않아 고른 파일을 미리보기만 한다.
 * 미리보기 URL 은 파일이 바뀌거나 화면을 떠날 때 반드시 해제한다.
 */
export function ImageUploadField({
  label,
  uploadText,
  maxWidth,
  initialUrl,
}: {
  label: string
  /** 비어 있을 때 박스 가운데에 뜨는 문구. 수정 화면은 "상품 이미지 변경". */
  uploadText: string
  /** Figma 박스 한 변. 프로필 280, 상품 360. */
  maxWidth: 280 | 360
  /** 이미 등록된 이미지. 목업 단계에서는 더미 사진이 들어온다. */
  initialUrl?: string
}) {
  const [file, setFile] = useState<File | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)

  useEffect(() => {
    if (!file) {
      setPreviewUrl(null)
      return
    }
    const url = URL.createObjectURL(file)
    setPreviewUrl(url)
    return () => URL.revokeObjectURL(url)
  }, [file])

  return (
    <div>
      <p className="text-[14px] font-bold text-foreground">{label}</p>

      <label
        className={cn(
          'ease-soft mt-2.5 flex aspect-square w-full cursor-pointer items-center justify-center overflow-hidden rounded-2xl border border-brand-300 bg-brand-50 transition-colors duration-150 hover:bg-brand-200',
          maxWidth === 280 ? 'max-w-[280px]' : 'max-w-[360px]',
        )}
      >
        {(previewUrl ?? initialUrl) ? (
          <img
            src={previewUrl ?? initialUrl}
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
          accept="image/*"
          className="sr-only"
          onChange={(event) => setFile(event.target.files?.[0] ?? null)}
        />
      </label>

      {file && (
        <button
          type="button"
          onClick={() => setFile(null)}
          className="mt-3 text-[13px] font-bold text-neutral-tertiary hover:underline"
        >
          이미지 지우기
        </button>
      )}
    </div>
  )
}
