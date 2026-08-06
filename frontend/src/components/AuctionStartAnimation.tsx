import { useState, type CSSProperties } from 'react'

import startFinal from '../assets/auction-start-cat-v1/auction-start-final.png'
import { MOTION_SOURCE } from './motion-sources'
import './AuctionStartAnimation.css'

export type AuctionStartAnimationProps = {
  /** 숫자는 px, 문자열은 CSS 길이 단위로 처리합니다. */
  size?: number | string
  /** 경매 ID나 시작 시각이 바뀌면 APNG가 처음부터 다시 재생됩니다. */
  replayKey?: string | number
  className?: string
  alt?: string
  /** `false`면 손종 애니메이션만 표시합니다. */
  showLabel?: boolean
}

export function AuctionStartAnimation({
  size = 320,
  replayKey = 0,
  className = '',
  alt = '경매 시작 알림',
  showLabel = true,
}: AuctionStartAnimationProps) {
  /* CDN 에서 못 받으면 번들의 정지 이미지로 갈아탄다 (`motion-sources.ts`). */
  const [remoteFailed, setRemoteFailed] = useState(false)
  const style = {
    '--auction-start-size': typeof size === 'number' ? `${size}px` : size,
  } as CSSProperties

  return (
    <figure
      key={replayKey}
      className={`auction-start-animation ${className}`.trim()}
      style={style}
      role="status"
      aria-live="polite"
      aria-label="경매가 시작되었습니다"
    >
      <picture className="auction-start-animation__picture">
        <source media="(prefers-reduced-motion: reduce)" srcSet={startFinal} />
        <img
          className="auction-start-animation__image"
          src={remoteFailed ? startFinal : MOTION_SOURCE.auctionStart}
          alt={showLabel ? '' : alt}
          draggable={false}
          onError={() => setRemoteFailed(true)}
        />
      </picture>
      {showLabel && (
        <figcaption
          className="auction-start-animation__label"
          aria-hidden="true"
        >
          <span className="auction-start-animation__live">LIVE</span>
          <strong>경매 시작</strong>
        </figcaption>
      )}
    </figure>
  )
}

export default AuctionStartAnimation
