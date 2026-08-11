// 0번 — 부하 발생기 한계 측정. 시나리오보다 먼저 돌린다.
//
// /actuator/health 에 시나리오와 똑같은 계단(10/20/40/80/160)을 걸어서 처리량이 평평해지는
// 지점을 찾는다. 처리량이 안 오르면 그건 서버 로직이 아니라 k6 나 노트북이 한계라는 뜻이다.
//
// ── health 는 "아무 일도 안 하는 주소"가 아니다 ────────────────────
// 예전 주석에 "여기는 DB 도 락도 안 탄다"고 적혀 있었는데 사실이 아니다. Spring Boot
// Actuator 의 기본 db health indicator 가 요청마다 DataSource 검증 쿼리를 날려서
// 커넥션을 하나씩 잡는다 (application-perf.yaml 에 이걸 끄는 설정이 없다).
//
// 실측(vus 160): 이 구간에 health 말고 다른 트래픽이 없는데도 hikari_active 가 평균 5.3,
// 최대 10(풀 상한)이었고 pending 이 68 까지 튀었다. 그러니 여기서 나온 처리량은
// "순수 부하 발생기 상한"이 아니라 "health + DB 왕복의 상한"이고, 진짜 k6 상한은 이보다
// 높다. 낮게 잡히는 쪽이라 안전한 방향으로 틀리는 것이라 그대로 쓴다.
//
// 시나리오 0 에서 pending 이 보이는 것을 서비스 문제로 읽지 않는다. 그건 health 몫이다.
// ────────────────────────────────────────────────────────────────
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
