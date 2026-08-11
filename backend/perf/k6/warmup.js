// 워밍업 전용. 결과를 안 남긴다 (handleSummary 가 없다).
//
// 예전에는 run.sh 의 워밍업이 `sleep` 뿐이었다. 그러면 아무것도 안 덥혀져서 측정 구간
// 앞부분이 그대로 워밍업 구간이 된다. 실측으로 확인했다 — 시나리오 0(vus 160)에서 앞 40초가
// 정상 구간의 1/10 처리량이었고, 그 결과 구간 평균이 정상 상태보다 12% 낮게 찍혔다.
// 계단마다 콜드 구간이 차지하는 비중이 달라서 "두 배 올렸을 때 몇 배" 비율이 왜곡된다.
//
// ── 왜 읽기 전용만 때리는가 ────────────────────────────────────────
// 그 시나리오의 스크립트로 덥히면 안 된다. 입찰 스크립트는 금액을 격자 위에서 올리는데
// (bid.js 의 START_PRICE + BID_UNIT * (__ITER * VUS + __VU)), 워밍업이 current_price 를
// 올려놓으면 본 측정은 __ITER 0 부터 다시 시작해서 첫 입찰들이 전부
// BID_AMOUNT_TOO_LOW 로 거절된다. 마감 시나리오도 마찬가지로 물품을 미리 닫아 버린다.
//
// 읽기 전용 둘로도 공유 경로는 대부분 덥는다: 톰캣 커넥터, 필터 체인, 세션, JPA/Hibernate,
// Hikari 풀, JSON 직렬화. 입찰 전용 코드는 못 덥히지만 sleep 보다는 확실히 낫다.
// 시나리오 6(입장)은 이 둘이 곧 측정 대상이라 완전히 덥는다.
// ────────────────────────────────────────────────────────────────

import http from 'k6/http'
import { BASE, SHARE_CODE, baseOptions } from './common.js'

export const options = baseOptions()

export default function () {
  http.get(`${BASE}/auction-rooms/share/${SHARE_CODE}`, { tags: { name: 'warmup' } })
  http.get(`${BASE}/auction-rooms/share/${SHARE_CODE}/auction-items`, { tags: { name: 'warmup' } })
}
