import { User } from 'lucide-react'
import { useState } from 'react'

import { cn } from '@/lib/utils'
import { mockAvatarImage } from '@/mocks/images'

/**
 * 프로필 사진.
 *
 * API 가 붙으면 `src` 에 서버가 준 주소를 넘긴다. 그전까지는 닉네임·가게명을
 * 씨앗 삼아 목업 사진을 고른다. 못 받으면 사람 아이콘으로 돌아간다.
 */
export function ProfilePhoto({
  seed,
  src,
  size = 240,
  className,
  iconClassName = 'size-1/2',
}: {
  seed: string
  src?: string | null
  size?: number
  className?: string
  iconClassName?: string
}) {
  const [failed, setFailed] = useState(false)
  const url = src ?? mockAvatarImage(seed, size)

  return (
    <span
      aria-hidden
      className={cn(
        'flex items-center justify-center overflow-hidden text-brand-500',
        className,
      )}
    >
      {failed ? (
        <User className={iconClassName} />
      ) : (
        <img
          src={url}
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
