import { useState } from 'react'

import { useCreatePresignedUrl } from '@/api/generated/업로드/업로드'
import { PresignedUrlRequestDtoDomain } from '@/api/generated/model'

/**
 * 고른 파일을 S3 에 올리고 저장할 주소를 돌려준다.
 *
 * 서버는 파일을 받지 않는다. 발급받은 `uploadUrl` 로 브라우저가 S3 에 직접 `PUT` 하고,
 * 폼은 `fileUrl` 만 `storeImageUrl` · `imageUrl` 로 실어 보낸다.
 */

/** 서버 `UploadService.EXTENSION_BY_CONTENT_TYPE` 과 같은 목록이다. 다른 값은 400 이 된다. */
const ALLOWED_CONTENT_TYPES = ['image/jpeg', 'image/png', 'image/webp']

export const IMAGE_ACCEPT = ALLOWED_CONTENT_TYPES.join(',')

/**
 * 5MB.
 *
 * 서버는 크기를 강제하지 않는다 — presigned PUT 은 브라우저가 S3 로 직접 보내므로
 * 우리 서버를 거치지 않는다. 그래서 이 값은 실수를 막는 안내이지 방어선이 아니다.
 */
export const MAX_IMAGE_BYTES = 5 * 1024 * 1024

export class ImageUploadError extends Error {}

/** 올릴 수 없는 파일이면 사용자에게 보여줄 문구를, 올릴 수 있으면 `null` 을 준다. */
export function checkImageFile(file: File): string | null {
  if (!ALLOWED_CONTENT_TYPES.includes(file.type)) {
    return 'JPG, PNG, WEBP 파일만 올릴 수 있어요.'
  }

  if (file.size > MAX_IMAGE_BYTES) {
    return '이미지는 5MB 까지 올릴 수 있어요.'
  }

  return null
}

export function useImageUpload(domain: PresignedUrlRequestDtoDomain) {
  const createPresignedUrl = useCreatePresignedUrl()
  const [uploading, setUploading] = useState(false)

  /** 올린 이미지의 주소를 돌려준다. 실패하면 `ImageUploadError` 를 던진다. */
  const uploadImage = async (file: File): Promise<string> => {
    const rejected = checkImageFile(file)
    if (rejected) throw new ImageUploadError(rejected)

    setUploading(true)

    try {
      const issued = await createPresignedUrl.mutateAsync({
        data: { domain, contentType: file.type },
      })

      const { uploadUrl, fileUrl } = issued.data ?? {}
      if (!uploadUrl || !fileUrl) {
        throw new ImageUploadError('업로드 주소를 받지 못했어요.')
      }

      // 공용 axios 인스턴스를 쓰지 않는다. 그쪽은 세션 쿠키를 싣는데(`withCredentials`),
      // S3 는 서명 말고 다른 인증 정보가 붙으면 403 으로 거절한다.
      // `Content-Type` 은 발급 요청에 보낸 값과 한 글자도 달라선 안 된다 — 서명에 묶여 있다.
      const response = await fetch(uploadUrl, {
        method: 'PUT',
        body: file,
        headers: { 'Content-Type': file.type },
        credentials: 'omit',
      })

      if (!response.ok) {
        if (import.meta.env.DEV) {
          console.error(
            'S3 업로드 실패',
            response.status,
            await response.text(),
          )
        }
        throw new ImageUploadError('이미지를 올리지 못했어요.')
      }

      return fileUrl
    } finally {
      setUploading(false)
    }
  }

  return { uploadImage, uploading }
}
