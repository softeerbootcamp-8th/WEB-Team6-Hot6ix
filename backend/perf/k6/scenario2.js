// 시나리오 2 — 물품 분산 (시나리오 1의 대조군).
//
// 계단은 시나리오 1과 똑같이 두고 물품만 20개로 흩는다. 락이 안 겹치면 처리량이 오르는지
// 보는 게 목적이다. 오르면 범인은 락이고, 그대로면 락이 아니라 다른 데가 막힌 것이다.
// 이때 무너질 곳은 커넥션 풀이나 톰캣 스레드다.
//
// 볼 지표: hikaricp_connections_pending (active 만 봐서는 줄 서 있는지 모른다)
//
// 방당 동시 진행 물품이 서비스 규칙으로 3개라, perf 프로파일에서만 그 값을 올려 둔다
// (application-perf.yaml 의 upbid.auction.max-in-progress-per-room). 이 시나리오로 잰
// 숫자에는 "제한을 푼 상태"라고 note.md 에 적는다.
//
//   ./perf/run.sh --scenario 2 --vus 40

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
