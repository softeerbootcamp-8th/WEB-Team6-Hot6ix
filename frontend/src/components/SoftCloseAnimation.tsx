import { useState, type CSSProperties } from 'react'

import extendedFinal from '../assets/soft-close-cat-v4/soft-close-final.png'
import { MOTION_SOURCE } from './motion-sources'
import './SoftCloseAnimation.css'

export type SoftCloseAnimationProps = {
  /** 소프트클로즈 정책으로 추가된 시간(분)입니다. */
  extensionMinutes: number
  /** 숫자는 px, 문자열은 CSS 길이 단위로 처리합니다. */
  size?: number | string
  /** 경매 ID나 연장 횟수를 바꾸면 APNG가 처음부터 다시 재생됩니다. */
  replayKey?: string | number
  className?: string
  alt?: string
  /** `false`면 애니메이션만 표시합니다. */
  showLabel?: boolean
}

export function SoftCloseAnimation({
  extensionMinutes,
  size = 320,
  replayKey = 0,
  className = '',
  alt,
  showLabel = true,
}: SoftCloseAnimationProps) {
  /* CDN 에서 못 받으면 번들의 정지 이미지로 갈아탄다 (`motion-sources.ts`). */
  const [remoteFailed, setRemoteFailed] = useState(false)
  const safeMinutes = Math.max(0, Math.floor(extensionMinutes))
  const style = {
    '--soft-close-size': typeof size === 'number' ? `${size}px` : size,
  } as CSSProperties
  const imageAlt = alt ?? `입찰 마감 시간이 ${safeMinutes}분 연장되었습니다`

  return (
    <figure
      key={`${replayKey}-${safeMinutes}`}
      className={`soft-close-animation ${className}`.trim()}
      style={style}
      aria-label={`${safeMinutes}분 연장`}
    >
      <picture className="soft-close-animation__picture">
        <source
          media="(prefers-reduced-motion: reduce)"
          srcSet={extendedFinal}
        />
        <img
          className="soft-close-animation__image"
          src={remoteFailed ? extendedFinal : MOTION_SOURCE.softCloseExtended}
          alt={imageAlt}
          draggable={false}
          onError={() => setRemoteFailed(true)}
        />
      </picture>
      {showLabel && (
        <figcaption className="soft-close-animation__label" aria-hidden="true">
          <strong>
            <span className="soft-close-animation__minutes">
              +{safeMinutes}분
            </span>
            연장
          </strong>
          <span>새 마감 시간이 적용됐어요</span>
        </figcaption>
      )}
    </figure>
  )
}

export default SoftCloseAnimation
