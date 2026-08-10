// 시나리오 1 — 한 물품에 몰기.
//
// 물품 1개에 입찰자를 10 → 20 → 40 → 80 → 160 으로 올린다. 무너질 곳은 BidService 의
// findByIdForUpdate 다. 물품 행 락을 커밋까지 들고 있어서 같은 물품 입찰이 한 줄로 선다.
//
// 볼 지표: upbid_bid_lock_wait p95, Innodb_row_lock_waits 전후 차 (run.sh 가 같이 남긴다)
//
//   ./perf/run.sh --scenario 1 --vus 40

import { baseOptions, summaryTo } from './common.js'
import { bidOnce, setupCheck } from './bid.js'

export const options = baseOptions()

export function setup() {
  setupCheck()
}

export default function () {
  bidOnce()
}

export function handleSummary(data) {
  return summaryTo(data)
}
