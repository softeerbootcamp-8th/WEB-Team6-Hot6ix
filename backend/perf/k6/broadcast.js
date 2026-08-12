// 8번 — SSE 브로드캐스트 A/B. 입찰 성능이 아니라 **전송 부하**를 재려는 스크립트다.
//
//   ./perf/run.sh --scenario 8 --rate 100 --items 40 --vus 40 --users 200 --sse 400
//
// ── 왜 1·2번으로는 못 재는가 ────────────────────────────────────────
// 브로드캐스트는 접수된 입찰에서만 나간다(DomainEventSseListener 가 BidPlaced 를 받는다).
// 그런데 계단 시나리오는 여러 VU 가 한 물품에 몰리므로 접수율이 통제되지 않는다. 실측으로
// 동기 계열이 121~153건/s, 비동기 계열이 18.6~21.3건/s 였다 — 비동기 쪽이 브로드캐스트를
// 7배 덜 한 것이라 그 둘의 처리량을 나란히 놓을 수가 없었다
// (perf/2026-08-11-sse-브로드캐스트-비동기.md 의 "이 측정이 답하지 못한 것").
//
// 여기서는 **요청 하나 = 접수 하나 = 브로드캐스트 하나**가 되게 만든다. 그러면 --rate 가
// 그대로 초당 브로드캐스트 수가 되고, 동기·비동기를 같은 전송 부하에서 비교할 수 있다.
//
// ── 접수율 100%를 만드는 방법 ───────────────────────────────────────
//   (1) VU 하나가 물품 하나를 독점한다. 다른 VU 가 그 물품에 안 들어오므로 현재가를 로컬
//       변수로 정확히 알 수 있고, 매번 금액을 읽으러 갈 필요가 없다. bid.js 는 입찰마다 상세를
//       GET 하는데(#246), 그러면 재려는 것과 무관한 조회가 요청의 절반을 차지한다.
//   (2) 계정 두 개를 교대로 쓴다. 같은 사람이 연달아 넣으면 ALREADY_TOP_BIDDER 로 거절된다.
//   (3) 시작 금액만 첫 반복에 한 번 읽고, 그 뒤로는 단위씩 올린다. 그 물품엔 이 VU 만
//       입찰하므로 격자가 안 어긋난다.
//
// 그래서 이 스크립트에서 **거절은 0이어야 한다.** 한 건이라도 있으면 전제가 깨진 것이고,
// 그 줄은 A/B 에 못 쓴다. run.sh 가 끝나고 확인해 준다.
//
// ── 이 스크립트가 재지 못하는 것 ────────────────────────────────────
// "실행기 스레드 2개로 충분한가"는 여기서 답이 안 나온다. 배경 SSE(scenario3.js)가 루프백에서
// 본문을 즉시 읽고 버리고 nginx 도 기본 버퍼링을 하므로 emitter.send() 가 절대 안 막힌다.
// 그러면 전송은 순수 CPU 작업이라 "큐가 밀린다"와 "코어가 모자라다"가 같은 말이 된다
// (2스레드 × 1초 ÷ 20.8µs ≈ 2코어 × 1초 ÷ 20.8µs). 느린 구독자를 주입해야 갈린다.
//
// 방 순서 보장(같은 방의 두 이벤트가 순서를 바꿔 도착하는지)도 못 본다. k6 가 SSE 본문을
// 버리기 때문이다.
//
// 볼 지표: upbid_sse_broadcast(전송 루프), upbid_sse_broadcast_queue(큐 대기),
//          upbid_sse_broadcast_caller_runs(0이 아니면 큐가 넘쳐 동기로 돌아간 것),
//          상태 코드 201 의 응답 분위 — 접속 수를 늘려도 이 값이 안 변하면 전송이 빠진 것이다

import http from 'k6/http'
import {
  BASE, ITEM_IDS, ROOMS, SHARE_CODE,
  agree, baseOptions, loginAs, summaryTo,
} from './common.js'
import { countResult } from './bid.js'

// ── 방을 하나만 쓴다 ────────────────────────────────────────────────
// 브로드캐스트는 방 단위고(sendBroadCast(name, roomId, data)), 배경 SSE 는 SHARE_CODE 하나에만
// 붙는다(scenario3.js). 방이 여럿이면 다른 방 입찰은 구독자가 없는 곳으로 나가서 전송 부하가
// --rate 보다 조용히 낮아진다. 그래서 한 방에 다 넣고 재고, 방이 여러 개로 오면 setup 이 멈춘다.
const ROOM = (ROOMS.length > 0 && ROOMS[0].itemIds.length > 0)
  ? ROOMS[0]
  : { code: SHARE_CODE, itemIds: ITEM_IDS }

// ── 빈 쿠키 항아리를 쓴다 ───────────────────────────────────────────
// k6 의 기본 항아리는 앞서 로그인한 사람의 SESSION 을 이후 요청에 자동으로 실어 보낸다.
// 이 스크립트는 VU 하나가 사람 둘을 만들어 교대로 쓰므로, 두 번째 사람의 요청에 첫 번째
// 사람의 쿠키가 함께 나가고 서버가 어느 세션인지 헷갈린다. bid.js 는 VU 당 사람이 하나라
// 이 일이 안 생겼다.
//
// **항아리를 하나 만들어 돌려쓰면 안 된다.** 그것도 응답의 Set-Cookie 를 그대로 담아서,
// 두 번째 로그인이 첫 번째 세션을 들고 가고 서버가 그 세션을 재사용해 버린다. 그러면 쿠키
// 두 개가 같은 값이 되어 같은 사람이 되고, 입찰이 전부 ALREADY_TOP_BIDDER 로 거절된다
// (실측: 401건 전량 7003).
//
// 그래서 요청마다 새 항아리를 준다. 그래야 **명시한 Cookie 헤더만** 나간다.
function freshJar() {
  return new http.CookieJar()
}

// --rate 를 주면 열린 모델(constant-arrival-rate), 안 주면 닫힌 모델이 된다. 여기서 막지 않는
// 이유는 **워밍업이 RATE=0 으로 이 스크립트를 돌리기 때문**이다(run.sh 의 RUN_RATE=0).
// 본 측정에 --rate 가 빠진 경우는 run.sh 가 컨테이너를 띄우기 전에 걸러 준다.
export const options = baseOptions()

/** 시딩이 이 시나리오가 요구하는 모양인지만 본다. 사람은 VU 가 제 손으로 만든다(아래). */
export function setup() {
  if (ROOM.itemIds.length === 0) {
    throw new Error('물품이 비어 있다. run.sh 를 쓰거나 seed.sh 가 출력한 값을 넘겨야 한다.')
  }

  if (ROOMS.length > 1) {
    throw new Error(`방이 ${ROOMS.length}개다. 배경 SSE 는 방 하나에만 붙어서 나머지 방의 ` +
      '브로드캐스트는 구독자 없는 곳으로 나간다. --per-room 0 으로 돌린다.')
  }
}

/** 그 물품에 넣을 첫 금액과 이후 올릴 단위. 왜 상세를 읽는지는 claimItem 에 적어 두었다. */
function startAmountOf(id, cookie) {
  const res = http.get(`${BASE}/auction-rooms/share/${ROOM.code}/auction-items/${id}`, {
    headers: { Cookie: `SESSION=${cookie}` },
    jar: freshJar(),
    // 전역 discardResponseBodies 를 여기서만 뒤집는다. 현재가를 읽어야 한다.
    responseType: 'text',
    tags: { name: 'item-detail' },
  })

  if (res.status !== 200) {
    throw new Error(`물품 ${id} 상세를 못 읽었다 (status=${res.status}). ` +
      '시딩이 끝났는지, 그 물품이 이 방 것인지 본다.')
  }

  const data = res.json('data')

  if (!data || typeof data.currentPrice !== 'number' || typeof data.bidIncrement !== 'number') {
    throw new Error(`물품 ${id} 상세에 currentPrice·bidIncrement 가 없다.`)
  }

  return { amount: data.currentPrice + data.bidIncrement, unit: data.bidIncrement }
}

// ── VU 마다 따로 사는 상태 ──────────────────────────────────────────
// preAllocatedVUs 와 maxVUs 가 같아야 한다(baseOptions 가 둘 다 VUS 로 준다). 다르면 측정 도중에
// VU 가 새로 생기면서 이 값들이 초기화되고, 그 VU 는 이미 오른 현재가 아래로 넣어 계속
// BID_AMOUNT_TOO_LOW 를 맞는다.
let itemId = null
let cookies = null
let nextAmount = 0
let unit = 0
let turn = 0
let reported = false

/**
 * 이 VU 가 맡을 물품과 사람 둘을 준비한다. 첫 반복에서 한 번만 돈다.
 *
 * <b>로그인을 setup 에서 하면 안 된다.</b> setup 에서 받은 세션 쿠키로 입찰하면 전부 401
 * (1005 "로그인이 필요합니다")이 된다 — 실측으로 401건 전량이 그랬다. bid.js 가 VU 안에서
 * 로그인하는 것(ensureSession)과 같은 자리에서 해야 한다.
 *
 * 시작 금액을 상세에서 읽는 이유: 시작가로 못 박으면 워밍업이 이미 올려 둔 현재가 아래로
 * 넣게 되어 따라잡을 때까지 BID_AMOUNT_TOO_LOW 를 맞는다. 현재가 + 단위는 입찰이 없을
 * 때도(현재가 = 시작가) 하한을 넘고 격자 위에 있어 항상 안전하다.
 */
function claimItem() {
  // 본 측정에서는 VU 와 물품이 1:1 이라 나머지 연산이 그대로 제 물품을 가리킨다(run.sh 가
  // --vus 와 --items 를 같게 강제한다). 워밍업은 5 VU 로 도는데, 그때만 앞쪽 물품 몇 개에
  // 겹쳐 들어간다. 데우는 게 목적이라 겹쳐도 상관없다.
  const index = (__VU - 1) % ROOM.itemIds.length

  itemId = ROOM.itemIds[index]

  // 물품마다 사람 둘. seed.sh 가 만든 bidder-N 을 그대로 쓰고, 모자라면 dev-login 이 그 자리에서
  // 만든다. 어느 쪽이든 동의를 한 번 더 시킨다 — 방이 하나면 이미 되어 있어 그냥 통과한다.
  cookies = [`bidder-${index * 2 + 1}`, `bidder-${index * 2 + 2}`].map((key) => {
    const cookie = loginAs(key, freshJar())
    agree(ROOM.code, cookie, freshJar())
    return cookie
  })

  const start = startAmountOf(itemId, cookies[0])

  nextAmount = start.amount
  unit = start.unit
}

export default function () {
  if (cookies === null) {
    claimItem()
  }

  const res = http.post(`${BASE}/auction-items/${itemId}/bids`, JSON.stringify({ amount: nextAmount }), {
    headers: { Cookie: `SESSION=${cookies[turn]}`, 'Content-Type': 'application/json' },
    jar: freshJar(),
    // 전역 discardResponseBodies 를 여기서만 뒤집는다. code 를 읽어야 거절 종류를 가른다.
    responseType: 'text',
    tags: { name: 'bid' },
  })

  countResult(res)

  if (res.status === 201) {
    nextAmount += unit
    turn ^= 1
    return
  }

  // ── 아래는 본 측정에서 안 밟혀야 하는 길이다 ──────────────────────
  // 밟히면 그 자체가 "전제가 깨졌다"는 신호고 카운터에 이미 남았다. 그래도 상태를 고쳐 두는
  // 이유는 한 번의 어긋남이 남은 3분을 통째로 거절로 만들지 않게 하기 위해서다. 안 고치면
  // 같은 금액·같은 사람으로 영원히 다시 보낸다. (워밍업에서는 VU 가 겹쳐서 정상적으로 밟힌다.)
  // 첫 한 건만 본문째 남긴다. 거절이 0이어야 하는 스크립트라, 깨졌을 때 "무엇이 거절했나"를
  // 로그에서 바로 읽을 수 있어야 한다. 안 남기면 카운터가 "그 밖의 거절 601건"까지만 알려주고
  // 원인을 찾으러 실행을 한 번 더 해야 한다.
  if (!reported) {
    reported = true
    console.error(`[8번] 거절: status=${res.status} body=${String(res.body).slice(0, 200)}`)
  }

  const code = errorCodeOf(res)

  if (code === 7003) {
    turn ^= 1                // 이미 최고 입찰자 — 다음 사람 차례로 넘긴다
  } else if (code === 7004 || code === 7005) {
    nextAmount += (unit || 1)  // 격자가 뒤처졌다 — 한 칸 올려 따라잡는다
  }
}

function errorCodeOf(res) {
  try {
    return res.json('code')
  } catch (e) {
    return null
  }
}

export function handleSummary(data) {
  return summaryTo(data)
}
