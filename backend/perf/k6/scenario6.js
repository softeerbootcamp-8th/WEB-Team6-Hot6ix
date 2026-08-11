// 시나리오 6 — 입장 폭주.
//
// 링크·QR 로 사람이 한꺼번에 들어올 때를 잰다. 한 반복 = 한 사람의 입장이다.
//
// 이 서비스는 링크·QR 로 퍼져서 방 자체는 작은데(물품 20, 참여자 200) 도착 속도만 튄다.
// 그래서 계단은 데이터 크기가 아니라 **동시 입장자 수(--vus)** 다. 물품당 입찰은
// --bids-per-item 으로 현실값에 고정해 두고 사람만 올린다.
//
// 입장 하나가 DB 를 세 번 탄다. 물품 목록은 그중에서도 쿼리가 둘인데, 리더보드(상위 3명)가
// 별도 API 가 아니라 **이 응답에 딸려 나가기 때문이다**(AuctionItemService.getSummaries).
// 그래서 무너질 후보가 둘이고 둘을 갈라 봐야 한다.
//   커넥션 풀   VU x 요청 3개가 pool(기본 10)을 넘어선다 -> hikaricp_connections_pending
//   리더보드    bids 를 GROUP BY + ROW_NUMBER 로 훑는다  -> items 태그의 p95
//
// 리더보드 몫은 --bids-per-item 0 을 한 줄 재서 뺄셈으로 분리한다. 현실 규모(물품당 200)
// 에서는 차이가 거의 없을 것으로 본다. 없다는 것도 근거가 있어야 말할 수 있는 결론이다.
//
//   ./perf/run.sh --scenario 6 --items 20 --bids-per-item 200 --vus 40
//   ./perf/run.sh --scenario 6 --items 20 --bids-per-item 0   --vus 40   # 대조군
//
// ── 왜 http.batch 인가 ─────────────────────────────────────────────
// 브라우저는 방에 들어갈 때 이 셋을 병렬로 쏜다(rooms.$shareCode.index.tsx 의
// useGetRoomByShareCode / useGetSummaries / useGetRecentEvents). 순차로 보내면 VU 하나가
// 입장 하나에 3배 시간을 써서, 같은 VU 수로도 서버가 보는 동시성이 1/3 이 된다.
//
// SSE 구독(/subscribe)은 여기 안 넣는다. 끝나지 않는 스트림이라 반복마다 열면 접속이
// 무한히 쌓이고, run.sh 의 응답 p95 계산도 그쪽으로 끌려간다(:640 주석 참고).
// 배경 접속이 필요하면 --sse 로 붙인다. 그게 run.sh 가 이미 하는 일이다.
// ────────────────────────────────────────────────────────────────

import http from 'k6/http'
import { Counter } from 'k6/metrics'
import { BASE, SHARE_CODE, agree, authHeaders, baseOptions, ensureSession, summaryTo } from './common.js'

// 입장 하나를 단위로 센다. 요청 셋 중 하나라도 5xx 면 그 사람의 입장은 실패다 —
// 물품 목록만 실패해도 화면은 빈 방으로 보이므로 "부분 성공"을 성공으로 세면 안 된다.
const entryOk = new Counter('entry_ok')
const entryFailed = new Counter('entry_failed') // 5xx·타임아웃. 진짜 실패는 이것뿐
const entryRejectedOther = new Counter('entry_rejected_other') // 401·403·404. 나오면 세팅이 잘못된 것

export const options = baseOptions()

export function setup() {
  if (!SHARE_CODE) {
    throw new Error('SHARE_CODE 가 비어 있다. run.sh 를 쓰거나 seed.sh 가 출력한 값을 넘겨야 한다.')
  }
}

export default function () {
  // VU 마다 회원 하나. bid.js 와 같은 규약이라 seed.sh 가 만들어 둔 bidder-N 을 그대로 쓴다.
  //
  // 조회 셋 중 물품 목록과 최근 이벤트는 인증이 필요 없지만 세션을 붙인다.
  // getRoomByShareCode 는 userId 를 받아 agreedToTerms·isOwner 를 채우므로, 비로그인으로
  // 재면 그 분기를 안 타서 실제 입장보다 가벼운 경로를 재게 된다.
  if (ensureSession(`bidder-${__VU}`)) {
    agree()
  }

  const headers = authHeaders()

  const responses = http.batch([
    ['GET', `${BASE}/auction-rooms/share/${SHARE_CODE}`, null, { headers, tags: { name: 'room' } }],
    ['GET', `${BASE}/auction-rooms/share/${SHARE_CODE}/auction-items`, null, { headers, tags: { name: 'items' } }],
    ['GET', `${BASE}/auction-rooms/share/${SHARE_CODE}/events`, null, { headers, tags: { name: 'events' } }],
  ])

  let failed = 0
  let rejected = 0

  for (const res of responses) {
    // status 0 은 타임아웃이나 연결 실패다. common.js 가 2xx~4xx 를 성공으로 쳐 두었으므로
    // http_req_failed 로는 안 잡히고, 여기서 세지 않으면 조용히 사라진다.
    if (res.status === 0 || res.status >= 500) {
      failed += 1
    } else if (res.status >= 400) {
      rejected += 1
    }
  }

  if (failed > 0) {
    entryFailed.add(1)
  } else if (rejected > 0) {
    entryRejectedOther.add(1)
  } else {
    entryOk.add(1)
  }
}

export function handleSummary(data) {
  return summaryTo(data)
}
