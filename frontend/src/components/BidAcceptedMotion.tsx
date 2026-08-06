import { type CSSProperties } from 'react'

import acceptedAnimation from '../assets/bid-accepted-micro-v1/bid-accepted.png'
import acceptedFinal from '../assets/bid-accepted-micro-v1/bid-accepted-final.png'
import './BidAcceptedMotion.css'

export type BidAcceptedMotionProps = {
  /** 숫자는 px, 문자열은 CSS 길이 단위로 처리합니다. */
  size?: number | string
  /** 입찰 요청 ID를 바꾸면 APNG가 처음부터 다시 재생됩니다. */
  replayKey?: string | number
  className?: string
  /** 상태 문구를 별도로 제공한다면 빈 문자열을 유지합니다. */
  alt?: string
}

export function BidAcceptedMotion({
  size = 96,
  replayKey = 0,
  className = '',
  alt = '',
}: BidAcceptedMotionProps) {
  const style = {
    '--bid-accepted-size': typeof size === 'number' ? `${size}px` : size,
  } as CSSProperties

  return (
    <picture
      key={replayKey}
      className={`bid-accepted-motion ${className}`.trim()}
      style={style}
    >
      <source media="(prefers-reduced-motion: reduce)" srcSet={acceptedFinal} />
      <img
        className="bid-accepted-motion__image"
        src={acceptedAnimation}
        alt={alt}
        draggable={false}
      />
    </picture>
  )
}

export default BidAcceptedMotion
