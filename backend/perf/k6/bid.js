// 입찰 부하의 본체. 시나리오 1과 2가 이걸 그대로 쓰고, 물품이 몇 개인지만 다르다.
//
//   시나리오 1 (한 물품 몰기)   : 물품 1개.  findByIdForUpdate 행 락이 커밋까지 유지돼 한 줄로 선다
//   시나리오 2 (물품 분산·대조군): 물품 20개. 락이 안 겹치는 대신 커넥션 풀이나 스레드가 마른다
//
// 같은 코드로 둘을 재야 대비가 의미를 갖는다. 두 스크립트로 갈라 쓰면 어느 날 한쪽만 고쳐서
// 대조가 아니게 된다.

import http from 'k6/http'
import exec from 'k6/execution'
import { Counter } from 'k6/metrics'
import { BASE, ITEM_IDS, START_PRICE, BID_UNIT, agree, authHeaders, ensureSession } from './common.js'

// ── 왜 상태 코드로 세면 안 되는가 ────────────────────────────────────
// 입찰 거절이 전부 409 다. BID_AMOUNT_TOO_LOW(7004)도 409, CONCURRENT_BID_CONFLICT(7006)도
// 409 라, 상태 코드만 세면 "규칙대로 거절"과 "행 락이 못 막은 경합"이 한 칸에 섞인다.
// 앞쪽은 정상이고 뒤쪽은 그 자체가 발견이라 반드시 갈라야 한다.
//
// 그래서 응답 본문의 code 를 읽어 종류별로 센다. 본문이 80바이트쯤이라 부담이 없다.
// ─────────────────────────────────────────────────────────────────
const accepted = new Counter('bid_accepted')
const rejectedAmount = new Counter('bid_rejected_amount')       // 7004, 7005 — 격자·현재가 규칙
const rejectedAlreadyTop = new Counter('bid_rejected_already_top') // 7003
const rejectedClosed = new Counter('bid_rejected_closed')       // 7001, 7002
const concurrentConflict = new Counter('bid_concurrent_conflict') // 7006 — 나오면 그게 발견이다
const rejectedOther = new Counter('bid_rejected_other')         // 401·403 등. 나오면 세팅이 잘못된 것
const serverFailed = new Counter('bid_failed')                  // 5xx·타임아웃. 진짜 실패는 이것뿐

export function setupCheck() {
  if (ITEM_IDS.length === 0) {
    throw new Error('ITEM_IDS 가 비어 있다. run.sh 를 쓰거나 seed.sh 가 출력한 값을 넘겨야 한다.')
  }
}

/**
 * 입찰할 물품 목록을 밖에서 받는다.
 *
 * 시나리오 6(마감 + 입찰)은 시딩을 `--start none` 으로 하기 때문에 ITEM_IDS 가 비어 있고,
 * 물품은 CLOSE_ITEM_IDS 에 담겨 온다. 기본값을 두어 시나리오 1·2 는 그대로 쓴다.
 */
export function bidOnce(itemIds = ITEM_IDS) {
  // VU 마다 회원 하나. 세션이 없을 때만 로그인하고 약관에 동의한다.
  // __ITER === 0 으로 판단하면 안 된다 — 쿠키 항아리가 반복마다 비워져서 두 번째 반복부터
  // 401 이 된다 (common.js 의 ensureSession 설명 참고).
  if (ensureSession(`bidder-${__VU}`)) {
    agree()
  }

  const itemId = itemIds[Math.floor(Math.random() * itemIds.length)]

  // 격자 위의 금액을 매 반복 올린다.
  //
  // 금액 규칙이 두 개라 아무 숫자나 보내면 전부 INVALID_BID_UNIT 으로 거절된다.
  //   (1) 시작가 + 단위 x N 위에 있어야 한다
  //   (2) 현재가 + 단위 이상이어야 한다
  //
  // 번호를 __ITER 로 매기면 안 된다. 그건 VU 마다 따로 도는 카운터라서, VU 가 늘수록 빠른 VU 와
  // 느린 VU 의 진도 차이가 벌어지고 뒤처진 VU 는 자기 번호가 현재가를 영영 못 넘어 계속 거절된다.
  // 실측으로 VU 10 은 접수율 98% 인데 VU 80 은 0.5% 였다. 같은 시험이 아니게 되어 계단을
  // 나란히 못 놓는다.
  //
  // iterationInTest 는 VU 를 통틀어 0, 1, 2 로 이어지는 번호라 그 격차가 안 생긴다.
  // 접수율이 VU 에 따라 내려가기는 하는데, 그건 한 물품에 사람이 몰리면 실제로 그런 것이라
  // 인공물이 아니다.
  const amount = START_PRICE + BID_UNIT * (exec.scenario.iterationInTest + 1)

  const res = http.post(`${BASE}/auction-items/${itemId}/bids`, JSON.stringify({ amount }), {
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    // 전역 discardResponseBodies 를 여기서만 뒤집는다. code 를 읽어야 거절 종류를 가른다.
    responseType: 'text',
    tags: { name: 'bid' },
  })

  countResult(res)

  return res
}

function countResult(res) {
  if (res.status === 201) {
    accepted.add(1)
    return
  }

  if (res.status >= 500 || res.status === 0) {
    serverFailed.add(1)
    return
  }

  switch (errorCodeOf(res)) {
    case 7001:
    case 7002:
      rejectedClosed.add(1); break
    case 7003:
      rejectedAlreadyTop.add(1); break
    case 7004:
    case 7005:
      rejectedAmount.add(1); break
    case 7006:
      concurrentConflict.add(1); break
    default:
      rejectedOther.add(1)
  }
}

function errorCodeOf(res) {
  try {
    return res.json('code')
  } catch (e) {
    return null
  }
}
