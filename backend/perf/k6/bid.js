// 입찰 부하의 본체. 시나리오 1과 2가 이걸 그대로 쓰고, 물품이 몇 개인지만 다르다.
//
//   시나리오 1 (한 물품 몰기)   : 물품 1개.  findByIdForUpdate 행 락이 커밋까지 유지돼 한 줄로 선다
//   시나리오 2 (물품 분산·대조군): 물품 20개. 락이 안 겹치는 대신 커넥션 풀이나 스레드가 마른다
//
// 같은 코드로 둘을 재야 대비가 의미를 갖는다. 두 스크립트로 갈라 쓰면 어느 날 한쪽만 고쳐서
// 대조가 아니게 된다.

import http from 'k6/http'
import { Counter } from 'k6/metrics'
import { BASE, ITEM_IDS, START_PRICE, BID_UNIT, VUS, agree, authHeaders, ensureSession } from './common.js'

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

export function bidOnce() {
  // VU 마다 회원 하나. 세션이 없을 때만 로그인하고 약관에 동의한다.
  // __ITER === 0 으로 판단하면 안 된다 — 쿠키 항아리가 반복마다 비워져서 두 번째 반복부터
  // 401 이 된다 (common.js 의 ensureSession 설명 참고).
  if (ensureSession(`bidder-${__VU}`)) {
    agree()
  }

  const itemId = ITEM_IDS[Math.floor(Math.random() * ITEM_IDS.length)]

  // 격자 위의 금액을 매 반복 올린다.
  //
  // 금액 규칙이 두 개라 아무 숫자나 보내면 전부 INVALID_BID_UNIT 으로 거절된다.
  //   (1) 시작가 + 단위 x N 위에 있어야 한다
  //   (2) 현재가 + 단위 이상이어야 한다
  // VU 수만큼 건너뛰며 올리면 VU 끼리 같은 금액을 안 쓰고, 반복이 돌수록 현재가를 따라간다.
  const amount = START_PRICE + BID_UNIT * (__ITER * VUS + __VU)

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
