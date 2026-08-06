import { useState, type CSSProperties } from 'react'

import endedFinal from '../assets/auction-cat-cel-v2/auction-cat-ended-final.png'
import soldFinal from '../assets/auction-cat-cel-v2/auction-cat-sold-final.png'
import { MOTION_SOURCE } from './motion-sources'
import './AuctionCloseAnimation.css'

export type AuctionCloseAnimationProps = {
  /** `sold`일 때 세 번째 타격 뒤에 빨간 낙찰 도장이 나타납니다. */
  outcome?: 'closed' | 'sold'
  /** 숫자는 px, 문자열은 CSS 길이 단위로 처리합니다. */
  size?: number | string
  /** 경매 ID나 종료 시각을 바꾸면 APNG 파일을 처음부터 다시 재생합니다. */
  replayKey?: string | number
  className?: string
  alt?: string
}

export function AuctionCloseAnimation({
  outcome = 'closed',
  size = 320,
  replayKey = 0,
  className = '',
  alt,
}: AuctionCloseAnimationProps) {
  /*
   * 낙찰 APNG 는 저장소가 아니라 CDN 에서 온다(`motion-sources.ts`). 못 받으면
   * 번들에 남아 있는 정지 이미지로 갈아탄다. 그냥 두면 CloudFront 가 없는
   * 파일에 index.html 을 200 으로 돌려주기 때문에 깨진 아이콘이 뜬다.
   */
  const [remoteFailed, setRemoteFailed] = useState(false)
  const style = {
    '--auction-close-size': typeof size === 'number' ? `${size}px` : size,
  } as CSSProperties
  const isSold = outcome === 'sold'
  /*
   * 유찰(`closed`)은 정지 이미지만 쓴다. 움직이는 6MB APNG 를 유찰까지 받게 하면
   * 저장소와 첫 재생 지연이 그만큼 늘어나는데, 유찰은 자주 나지도 않고 축하할
   * 일도 아니라서 연출까지 필요하지 않다고 봤다.
   */
  const remoteSource = remoteFailed ? soldFinal : MOTION_SOURCE.auctionSold
  const animationSource = isSold ? remoteSource : endedFinal
  const reducedMotionSource = isSold ? soldFinal : endedFinal
  const label = alt ?? (isSold ? '경매 낙찰 완료' : '경매 마감 완료')

  return (
    <picture
      key={`${replayKey}-${outcome}`}
      className={`auction-close-animation ${className}`.trim()}
      style={style}
    >
      <source
        media="(prefers-reduced-motion: reduce)"
        srcSet={reducedMotionSource}
      />
      <img
        className="auction-close-animation__image"
        src={animationSource}
        alt={label}
        draggable={false}
        onError={isSold ? () => setRemoteFailed(true) : undefined}
      />
    </picture>
  )
}

export default AuctionCloseAnimation
