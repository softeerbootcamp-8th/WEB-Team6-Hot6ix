import { User } from 'lucide-react'
import { useState, type ReactNode } from 'react'

import { cn } from '@/lib/utils'

/**
 * 프로필 사진.
 *
 * `src` 에 서버가 준 주소를 넘긴다 (`profileImageUrl` · `storeImageUrl`).
 * 주소가 없거나 내려받기에 실패하면 사람 아이콘으로 떨어진다.
 *
 * **주소가 없어도 목업 사진으로 떨어지지 않는다.** 예전에는 닉네임·가게명을
 * 씨앗 삼아 목업 사진을 골랐다. 그래서 사진을 올린 적 없는 사람에게도
 * 얼굴 사진이 붙어 있었다(#139).
 */
export function ProfilePhoto({
  src,
  className,
  iconClassName = 'size-1/2',
  fallback,
}: {
  /** 서버가 준 이미지 주소. 없으면 사람 아이콘을 그린다. */
  src?: string | null
  className?: string
  iconClassName?: string
  /**
   * 주소가 없거나 실패했을 때 아이콘 대신 그릴 것.
   *
   * 상단바는 예전부터 닉네임 첫 글자를 보여줬다. 사진을 붙이면서 아이콘으로
   * 바꾸면 사진을 안 올린 사람의 화면이 지금보다 나빠져서 열어 뒀다(#213).
   */
  fallback?: ReactNode
}) {
  const [failed, setFailed] = useState(false)

  return (
    <span
      aria-hidden
      className={cn(
        'flex items-center justify-center overflow-hidden text-brand-500',
        className,
      )}
    >
      {!src || failed ? (
        (fallback ?? <User className={iconClassName} />)
      ) : (
        <img
          src={src}
          alt=""
          loading="lazy"
          decoding="async"
          onError={() => setFailed(true)}
          className="size-full object-cover"
        />
      )}
    </span>
  )
}
