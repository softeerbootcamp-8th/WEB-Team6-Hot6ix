// 0번 — 부하 발생기 한계 측정. 시나리오보다 먼저 돌린다.
//
// /actuator/health 에 시나리오와 똑같은 계단(10/20/40/80/160)을 걸어서 처리량이 평평해지는
// 지점을 찾는다. 여기는 DB 도 락도 안 타므로, 처리량이 안 오르면 그건 서버가 아니라
// k6 나 노트북이 한계라는 뜻이다.
//
// 이 지점 위쪽에서 나온 숫자는 전원이 안 믿기로 한다. 그래서 이후 모든 줄에 k6 CPU 를 같이 적는다.
//
//   ./perf/run.sh --scenario 0 --vus 160

import http from 'k6/http'
import { check } from 'k6'
import { ROOT, baseOptions, summaryTo } from './common.js'

export const options = baseOptions()

export default function () {
  const res = http.get(`${ROOT}/actuator/health`, { tags: { name: 'health' } })

  check(res, { 'health 200': (r) => r.status === 200 })
}

export function handleSummary(data) {
  return summaryTo(data)
}
