import { type CSSProperties } from 'react'

import acceptedFinal from '../assets/bid-accepted-micro-v1/bid-accepted-final.png'
import './BidAcceptedMotion.css'

export type BidAcceptedMotionProps = {
  /** 숫자는 px, 문자열은 CSS 길이 단위로 처리합니다. */
  size?: number | string
  className?: string
  /** 상태 문구를 별도로 제공한다면 빈 문자열을 유지합니다. */
  alt?: string
}

/**
 * 입찰 접수 표시. **움직이지 않는다.**
 *
 * 원래는 0.855초짜리 APNG 였는데 정지 이미지(마지막 프레임)로 바꿨다. 이 표시가
 * 들어가는 자리가 토스트인데, 토스트는 문구를 읽을 시간을 주려고 4초 떠 있다.
 * 그림을 한 번만 재생시키면 나머지 3초가 멎어 있고, 계속 돌게 두면 체크 표시가
 * 네 번 넘게 반복돼서 입찰이 여러 번 된 것처럼 보였다.
 *
 * 카드 위 연출(시작·연장·마감)과 달리 여기는 아이콘 자리라 44px 로 작고, 움직여서
 * 얻는 것보다 반복돼서 잃는 것이 크다고 봤다. 체크가 이미 찍힌 그림이라 어색하지
 * 않다. 움직이는 원본은 저장소에서 뺐다(`assets/animation/` 원본 폴더에 있다).
 */
export function BidAcceptedMotion({
  size = 96,
  className = '',
  alt = '',
}: BidAcceptedMotionProps) {
  const style = {
    '--bid-accepted-size': typeof size === 'number' ? `${size}px` : size,
  } as CSSProperties

  return (
    <picture
      className={`bid-accepted-motion ${className}`.trim()}
      style={style}
    >
      <img
        className="bid-accepted-motion__image"
        src={acceptedFinal}
        alt={alt}
        draggable={false}
      />
    </picture>
  )
}

export default BidAcceptedMotion
