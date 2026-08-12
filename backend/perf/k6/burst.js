// 시나리오 8 — 한 물품에 정확히 한 번씩 동시 입찰한다.
//
// 지속 처리량이 아니라 정합성 기준선이다. setup이 공통 출발 시각을 만들고, 각 VU는 그 전에
// 로그인·약관 동의·현재가 조회를 끝낸 뒤 POST만 같은 시각에 보낸다.

import { sleep } from 'k6'
import { summaryTo, VUS } from './common.js'
import { prepareBid, setupCheck, submitBid } from './bid.js'

const MODE = __ENV.BURST_MODE || 'same'
const PREPARE_SECONDS = Number(__ENV.BURST_PREPARE_SECONDS || 15)

if (MODE !== 'same' && MODE !== 'increasing') {
  throw new Error(`BURST_MODE는 same 또는 increasing이다 (받은 값: ${MODE})`)
}

export const options = {
  scenarios: {
    burst: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: 1,
      maxDuration: `${PREPARE_SECONDS + 30}s`,
      gracefulStop: '5s',
    },
  },
  discardResponseBodies: true,
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)'],
}

export function setup() {
  setupCheck()
  return { startAt: Date.now() + PREPARE_SECONDS * 1000 }
}

export default function (data) {
  const prepared = prepareBid()

  if (prepared === null) {
    return
  }

  const waitMs = data.startAt - Date.now()
  if (waitMs > 0) {
    sleep(waitMs / 1000)
  }

  // same은 동일 금액 중복 방지, increasing은 커밋 순서가 뒤집혀도 현재가가 역행하지 않는지 본다.
  const amount = MODE === 'same'
    ? prepared.currentPrice + prepared.bidIncrement
    : prepared.currentPrice + prepared.bidIncrement * __VU

  submitBid(prepared.itemId, amount)
}

export function handleSummary(data) {
  return summaryTo(data)
}
