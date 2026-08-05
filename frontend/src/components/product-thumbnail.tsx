import { ImageIcon } from 'lucide-react'
import { useState } from 'react'

import { cn } from '@/lib/utils'

/**
 * 상품 썸네일.
 *
 * `src` 에 서버가 준 `imageUrl` 을 넘긴다. 주소가 없거나 내려받기에 실패하면
 * 회색 아이콘으로 떨어진다. 네트워크가 없어도 배치가 무너지지 않아야 한다.
 *
 * **주소가 없어도 목업 사진으로 떨어지지 않는다.** 예전에는 `src` 가 없으면
 * 상품 이름으로 목업 사진을 골랐다. 그래서 판매자가 사진을 올리지 않은 물품에
 * 관계없는 사진이 뜨고, 경매 중에 판매자가 올린 것과 다른 사진이 보였다(#138).
 * 비어 보이는 게 틀린 사진보다 낫다.
 */
export function ProductThumbnail({
  src,
  className,
  iconClassName = 'size-6',
}: {
  /** 서버가 준 이미지 주소. 없으면 회색 아이콘을 그린다. */
  src?: string | null
  className?: string
  iconClassName?: string
}) {
  const [state, setState] = useState<'loading' | 'ready' | 'failed'>('loading')

  return (
    <span
      aria-hidden
      className={cn('relative overflow-hidden', className)}
      // 배경색은 호출한 쪽 클래스를 그대로 쓴다.
    >
      {src && state !== 'failed' && (
        <img
          src={src}
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

      {src && state === 'loading' && (
        <span className="animate-skeleton absolute inset-0 size-full bg-fill" />
      )}

      {(!src || state === 'failed') && (
        <span className="flex size-full items-center justify-center">
          <ImageIcon className={iconClassName} />
        </span>
      )}
    </span>
  )
}
