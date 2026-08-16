// 11번 — 종료된 경매방 결과 조회. AuctionResultCache 전후 비교용 (#329).
//
// 시나리오 9(enter.js)와 10(scenario10.js, SSE dispatch 부하)이 이미 있어서 11을 쓴다.
// 재는 대상도 다르다 — 9는 방 공개 조회 둘(입장 폭주), 이건 종료된 방의 결과 조회 하나다.
//
//   GET /auction-rooms/share/{shareCode}/results
//
// guest 와 member 를 나눠 재는 이유: 캐시가 히트해도 로그인한 요청은 내 순위를 채우려고
// findMyRanksInRoom 을 매번 한 번 더 부른다(AuctionRoomService.withMyRanks). guest 는
// 히트하면 DB를 아예 안 본다. 하나로 섞어 재면 "히트해도 여전히 쿼리가 남는다"는 사실이
// 평균 뒤에 숨는다.
//
//   ./perf/run.sh --scenario 11 --results-auth guest  --rate 200 --vus 200
//   ./perf/run.sh --scenario 11 --results-auth member --rate 200 --vus 200
//
// 볼 것: p95, throughput_req_per_s, select_per_result(=Com_select 증가량/요청수).
// --result-cache 가 구현된 뒤에는 off/on 한 값만 바꿔 이 값들이 어떻게 바뀌는지 본다.

import http from 'k6/http'
import { Counter } from 'k6/metrics'
import { BASE, SHARE_CODE, VUS, baseOptions, ensureSession, authHeaders, summaryTo } from './common.js'

const AUTH_MODE = __ENV.RESULTS_AUTH === 'member' ? 'member' : 'guest'

// ── 4xx 를 세는 이유 ──────────────────────────────────────────────
// 결과 조회는 인증이 필요 없는 공개 API 라(@GuestAllowed) 거절 규칙이 없다. 여기 4xx 가
// 찍히면 전부 세팅 실수다 — 공유 코드가 틀렸거나 방이 없어졌거나다. common.js 가 4xx 를
// 성공으로 세기 때문에(입찰의 정상 거절을 실패로 안 세려고 그렇게 뒀다) 그래프만 봐서는
// 안 드러난다. run.sh 가 이 값을 읽어 경고한다 (enter.js 의 enter_4xx 와 같은 이유).
const resultsOk = new Counter('results_ok')
const results4xx = new Counter('results_4xx')     // 0이어야 한다. 크면 그 실행은 못 쓴다
const resultsFailed = new Counter('results_failed') // 5xx·타임아웃

export const options = baseOptions()

/**
 * 결과 조회 URL. AuctionRoomService.getResults 가 shareCode 로만 방을 찾으므로
 * 로그인 여부는 헤더로만 갈리고 경로는 하나다.
 */
function resultsUrl() {
  return `${BASE}/auction-rooms/share/${SHARE_CODE}/results`
}

/**
 * 본 측정 전에 시딩이 실제로 결과를 볼 수 있는 상태를 만들었는지 확인한다.
 *
 * seed.sh --close-room 이 방을 못 닫았는데 3분을 그냥 재면, guest 응답은 어차피 200이라
 * 겉으로는 멀쩡해 보이고 캐시도 "OPEN 방은 저장하지 않는다" 규칙 때문에 조용히 미스만
 * 쌓인다. hit rate 가 왜 0인지 나중에서야 알게 되는 사고를 여기서 막는다.
 */
export function setup() {
  if (!SHARE_CODE) {
    throw new Error('SHARE_CODE 가 없다. run.sh 를 쓰거나 seed.sh --close-room 이 출력한 값을 넘겨야 한다.')
  }

  const guestRes = http.get(resultsUrl(), { responseType: 'text', tags: { name: 'results-setup' } })

  if (guestRes.status !== 200) {
    throw new Error(`결과 조회가 200이 아니다 (status=${guestRes.status}). 공유 코드나 seed.sh 실행을 본다.`)
  }

  const body = guestRes.json()

  if (!body || !body.data || body.data.status !== 'CLOSED') {
    const status = body && body.data && body.data.status
    throw new Error(
      `경매방이 CLOSED 가 아니다 (status=${status}). seed.sh 를 --close-room 없이 돌렸거나 ` +
      '방 종료가 실패한 것이다 — 이 상태로는 캐시가 아예 저장되지 않는다.')
  }

  if (AUTH_MODE === 'member') {
    // bidder-1 은 seed.sh --close-room 이 물품마다 넣는 입찰의 첫 번째 입찰자라 반드시
    // 낙찰 후보로 남는다. 로그인 없이 만든 guestRes 와 달리 여기서만 세션을 새로 받는다.
    const loginRes = http.post(`${BASE}/auth/dev-login?key=bidder-1`, null, { tags: { name: 'dev-login-setup' } })
    const cookie = loginRes.cookies['SESSION']

    if (!cookie || cookie.length === 0) {
      throw new Error(`dev-login 이 SESSION 쿠키를 안 줬다 (status=${loginRes.status}).`)
    }

    const memberRes = http.get(resultsUrl(), {
      headers: { Cookie: `SESSION=${cookie[0].value}` },
      responseType: 'text',
      tags: { name: 'results-setup' },
    })
    const memberBody = memberRes.status === 200 ? memberRes.json() : null
    const hasRank = memberBody && memberBody.data.items.some((item) => item.myRank !== null)

    if (!hasRank) {
      throw new Error(
        'bidder-1 이 어느 물품에도 myRank 가 없다. seed.sh --bids-per-item 이 0이거나 ' +
        '시딩과 실행 사이에 다른 방을 본 것이다 — member 모드가 재는 "히트해도 남는 ' +
        '쿼리 한 번"을 검증할 수 없다.')
    }
  }
}

function count(res) {
  if (res.status >= 200 && res.status < 300) {
    resultsOk.add(1)
  } else if (res.status >= 400 && res.status < 500) {
    results4xx.add(1)
  } else {
    // status 0 은 타임아웃이나 연결 실패다.
    resultsFailed.add(1)
  }
}

export default function () {
  if (AUTH_MODE === 'member') {
    // VU 마다 다른 회원이어야 한다. 전원이 같은 회원이면 세션 하나를 공유하는 셈이라
    // 로그인 경로의 실제 부하(세션 조회·경합)가 재현되지 않는다. dev-login 은 처음 보는
    // key 도 그 자리에서 회원으로 만들어 주므로, VU 수가 시딩한 구매자 수를 넘어도 죽지
    // 않는다 — 결과 조회는 약관 동의를 보지 않아 새로 생긴 회원이어도 그대로 조회된다.
    ensureSession(`bidder-${((__VU - 1) % VUS) + 1}`)
  }

  // responseType 을 안 준다 — 본문을 안 읽으므로 전역 discardResponseBodies 그대로 둔다.
  // 응답이 VU 수백 개에 붙들리면 재는 대상이 서버가 아니라 k6 의 메모리가 된다.
  const res = http.get(resultsUrl(), {
    headers: AUTH_MODE === 'member' ? authHeaders() : undefined,
    tags: { name: 'results' },
  })

  count(res)
}

export function handleSummary(data) {
  return summaryTo(data)
}
