import { ImageIcon } from 'lucide-react'
import { useState } from 'react'

import { cn } from '@/lib/utils'
import { mockProductImage } from '@/mocks/images'

/**
 * 상품 썸네일.
 *
 * 지금은 이름으로 목업 사진을 고른다. API 가 붙으면 `src` 에 서버가 준
 * `imageUrl` 을 넘기면 되고, 나머지 동작(로딩·실패 대체)은 그대로 쓴다.
 *
 * 사진을 못 받으면 원래 쓰던 회색 아이콘으로 돌아간다. 네트워크가 없어도
 * 배치가 무너지지 않아야 한다.
 */
export function ProductThumbnail({
  name,
  src,
  size = 480,
  className,
  iconClassName = 'size-6',
}: {
  name: string
  /** 서버가 준 이미지 주소. 없으면 목업 사진을 고른다. */
  src?: string | null
  /** 내려받을 사진 크기(px). 큰 영역은 크게 받는다. */
  size?: number
  className?: string
  iconClassName?: string
}) {
  const [state, setState] = useState<'loading' | 'ready' | 'failed'>('loading')
  const url = src ?? mockProductImage(name, size)

  return (
    <span
      aria-hidden
      className={cn('relative overflow-hidden', className)}
      // 배경색은 호출한 쪽 클래스를 그대로 쓴다.
    >
      {state !== 'failed' && (
        <img
          src={url}
          alt=""
          loading="lazy"
          decoding="async"
          onLoad={() => setState('ready')}
          onError={() => setState('failed')}
          className={cn(
            'absolute inset-0 size-full object-cover transition-opacity duration-300',
            state === 'ready' ? 'opacity-100' : 'opacity-0',
          )}
        />
      )}

      {state === 'loading' && (
        <span className="animate-skeleton absolute inset-0 size-full bg-fill" />
      )}

      {state === 'failed' && (
        <span className="flex size-full items-center justify-center">
          <ImageIcon className={iconClassName} />
        </span>
      )}
    </span>
  )
}
