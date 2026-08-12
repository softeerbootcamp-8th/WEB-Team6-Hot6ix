// 입찰 부하의 본체. 시나리오 1과 2가 이걸 그대로 쓰고, 물품이 몇 개인지만 다르다.
//
//   시나리오 1 (한 물품 몰기)   : 물품 1개.  findByIdForUpdate 행 락이 커밋까지 유지돼 한 줄로 선다
//   시나리오 2 (물품 분산·대조군): 물품 20개. 락이 안 겹치는 대신 커넥션 풀이나 스레드가 마른다
//
// 같은 코드로 둘을 재야 대비가 의미를 갖는다. 두 스크립트로 갈라 쓰면 어느 날 한쪽만 고쳐서
// 대조가 아니게 된다.
//
// 입찰 금액은 물품 상세를 읽어 현재가 + 단위로 만든다. 이유는 bidOnce 안에 적어 두었다.

import http from 'k6/http'
import { Counter } from 'k6/metrics'
import { BASE, ITEM_IDS, SHARE_CODE, agree, authHeaders, ensureSession } from './common.js'

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
  const prepared = prepareBid(itemIds)

  if (prepared === null) {
    return null
  }

  return submitBid(prepared.itemId, prepared.currentPrice + prepared.bidIncrement)
}

/**
 * 입찰 직전까지 필요한 로그인·동의·현재가 조회를 끝낸다.
 * burst 시나리오는 이 단계까지 각 VU가 마친 뒤 공통 시각에 POST만 동시에 보낸다.
 */
export function prepareBid(itemIds = ITEM_IDS) {
  // VU 마다 회원 하나. 세션이 없을 때만 로그인하고 약관에 동의한다.
  // __ITER === 0 으로 판단하면 안 된다 — 쿠키 항아리가 반복마다 비워져서 두 번째 반복부터
  // 401 이 된다 (common.js 의 ensureSession 설명 참고).
  if (ensureSession(`bidder-${__VU}`)) {
    agree()
  }

  const itemId = itemIds[Math.floor(Math.random() * itemIds.length)]

  // ── 왜 현재가를 읽고 나서 입찰하는가 ──────────────────────────────
  // 예전에는 VU 의 반복 횟수로 금액을 만들었다 (START_PRICE + BID_UNIT * (__ITER * VUS + __VU)).
  // 그러면 접수되는 건 항상 그 순간 제일 높은 금액이고 그게 currentPrice 가 되므로, 나머지
  // 전원이 그 아래로 떨어져 7004 로 거절된다. 실측으로 거절이 99.3 % 였다 (이슈 #246).
  //
  // 그 상태에서는 락을 잡고 쓰기까지 가는 경로를 0.7 % 만 타므로, 재는 것이 "입찰 접수 성능"이
  // 아니라 "입찰 거절 속도"가 된다.
  //
  // 실제 사용자는 화면의 현재가를 보고 그 위에 얹는다. 그래서 상세를 읽고 현재가 + 단위로 넣는다.
  // 요청이 GET + POST 로 두 배가 되므로 판정은 throughput 이 아니라 accepted_per_s 로 한다.
  // ─────────────────────────────────────────────────────────────────
  const detail = http.get(
    `${BASE}/auction-rooms/share/${SHARE_CODE}/auction-items/${itemId}`,
    {
      headers: authHeaders(),
      // 전역 discardResponseBodies 를 여기서만 뒤집는다. 현재가를 읽어야 한다.
      responseType: 'text',
      tags: { name: 'item-detail' },
    },
  )

  const item = itemOf(detail)

  // 상세를 못 읽으면 금액을 만들 수 없다. 아무 값이나 보내면 그 요청이 거절 통계에 섞여
  // 접수율을 흐린다. 이번 반복은 건너뛴다.
  if (item === null) {
    return null
  }

  return { itemId, currentPrice: item.currentPrice, bidIncrement: item.bidIncrement }
}

/** 준비가 끝난 입찰 POST를 보내고 기존과 같은 결과 counter에 기록한다. */
export function submitBid(itemId, amount) {
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

/**
 * 물품 상세에서 금액 계산에 필요한 둘만 꺼낸다.
 *
 * 200 이 아니거나 본문이 예상과 다르면 null 을 준다. 여기서 예외를 던지면 그 VU 의 반복이
 * 통째로 중단되어 부하가 조용히 줄어든다.
 */
function itemOf(res) {
  if (res.status !== 200) {
    return null
  }

  try {
    const data = res.json('data')

    if (!data || typeof data.currentPrice !== 'number' || typeof data.bidIncrement !== 'number') {
      return null
    }

    return data
  } catch (e) {
    return null
  }
}
