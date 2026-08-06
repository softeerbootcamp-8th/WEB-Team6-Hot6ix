import { preload } from 'react-dom'

import { MOTION_SOURCE } from '@/components/motion-sources'

/**
 * 라이브 경매방에서 쓰는 APNG 를 미리 받아둔다.
 *
 * `<img>` 는 그려질 때 받기 시작한다. 마감이나 연장은 예고 없이 일어나는데
 * 그 순간에 몇 MB 를 받기 시작하면 연출이 다 끝나도록 그림이 안 나온다.
 * 방에 들어온 뒤 한가할 때 미리 받아두면 정작 필요한 순간에는 캐시에서 꺼내 쓴다.
 *
 * 무거운 셋은 저장소가 아니라 CDN 에서 온다(`motion-sources.ts`). 미리 받아두는
 * 일이 더 중요해졌다. 처음 요청하는 사람은 오리진까지 다녀와야 하기 때문이다.
 *
 * `fetchPriority: 'low'` 를 주는 이유: 방에 들어오자마자 필요한 것은 물품 목록과
 * 실시간 연결이다. 연출용 이미지가 그 요청과 대역폭을 다투면 안 된다.
 *
 * 유찰과 입찰 접수는 정지 이미지(100KB 이하)라 미리 받을 만큼 무겁지 않아 뺐다.
 */
export function preloadLiveMotion() {
  for (const href of [
    MOTION_SOURCE.auctionStart,
    MOTION_SOURCE.auctionSold,
    MOTION_SOURCE.softCloseExtended,
  ]) {
    preload(href, { as: 'image', fetchPriority: 'low' })
  }
}
