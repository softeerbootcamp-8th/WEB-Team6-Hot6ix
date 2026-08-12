// 9번 — 방 입장 램프 + SSE 브로드캐스트. 방 20개(기본, --rooms)에 유저를 점진적으로 입장시키고,
// 입장이 만드는 브로드캐스트와 입찰이 만드는 브로드캐스트를 함께(또는 순서대로) 잰다.
//
//   ./perf/run.sh --scenario 9 --rooms 20 --users 2000 --rate 1000 --ramp 1m30s
//
// ── 왜 새 시나리오가 필요한가 ────────────────────────────────────────
// RoomSseManager.subscribe() 는 연결이 하나 붙을 때마다 그 방에 이미 붙어 있는 전원에게
// PARTICIPANT_COUNT_UPDATED 를 브로드캐스트한다. 유저가 순차적으로 들어오는 과정 자체가
// 브로드캐스트 부하인데, 접속을 한 번에 다 붙이는 3번 시나리오로는 이걸 못 잰다.
//
// 그래서 실행기(executor) 둘을 하나의 k6 실행 안에 둔다. 배경 SSE 를 별도 컨테이너로
// 미리 띄우고 붙은 뒤에야 측정을 시작하는 --sse 방식은 여기서는 안 맞는다 — 그러면 입장
// 브로드캐스트가 측정 구간 밖에서 다 끝나버린다. 그래서 이 실행 전체(램프 포함)를 그대로
// 측정 구간으로 잡는다(run.sh 쪽에서 별도 보정을 하지 않는다).
//
//   audience  ramping-vus. 0명 → --users 까지 --ramp 시간에 걸쳐 올리고, 이후 --duration
//             만큼 유지한다. VU 하나 = SSE 구독 연결 하나. (VU-1) % 방수 로 방에 흩는다.
//   director  constant-arrival-rate. 초당 --rate 건. --bid-timing 에 따라 램프가 끝난
//             뒤(after-ramp, 기본) 또는 램프와 동시에(overlap) 시작한다.
//
// ── 입찰 리젝트를 최대한 줄이는 방법 (물품 전담 워커 방식은 버렸다) ──────
// 처음에는 8번(broadcast.js)의 "물품 하나 = 워커(VU) 하나" 기법을 다중 방으로 그대로
// 확장하려 했다. VU 번호로 `(VU-1) % 물품수` 를 계산해 물품을 영구히 나눠 갖게 하는
// 방식이다. **그런데 실측으로 이게 이 스크립트에서는 안전하지 않다는 걸 확인했다.**
//
// audience(ramping-vus)와 director(constant-arrival-rate)를 한 k6 실행 안에 같이 두면,
// k6 가 두 실행기의 VU 번호를 하나의 공유 풀에서 나눠 준다 — director 가 항상 연속된
// 번호 블록을 받는다는 보장이 없다. 실측 로그에서 서로 다른 물리 VU 셋(예: 5, 17, 23)이
// 전부 `(VU-1) % 6` 계산이 같은 값으로 나와 같은 물품을 "내가 전담한다"고 각자 착각했고,
// 그 결과 서로 다른 로컬 가격을 추적하다가 리젝트가 33%까지 나왔다(2방·6물품·rate 20 기준
// 접수 402 / 거절 199). 8번은 시나리오가 하나뿐이라 이 문제가 없었다.
//
// 그래서 **로컬 상태로 물품을 독점하는 방식을 버리고, 매 요청마다 서버 상태를 그대로
// 읽어 쓴다.**
//
//   (1) 매번 물품 하나를 무작위로 고르고(전체 물품 중에서 — 방마다 물품 수가 같으므로
//       평균적으로 방별 배분도 고르게 된다), 상세를 다시 읽어 **현재가**를 얻는다.
//       currentPrice + bidIncrement 로 다음 입찰가를 만든다(bid.js#bidOnce 와 같은 이유).
//   (2) 상세 응답의 leaderboard[0].nickname 이 **현재 최고 입찰자를 그대로 알려준다.**
//       DevAuthService#nicknameOf 가 dev-login key 를 그대로 닉네임으로 쓰기 때문에
//       (자릿수 제한 안에서) 별도 매핑 없이 "이 사람이 지금 최고가다"를 바로 안다.
//       그 방 구매자 풀(common.js#biddersOfRoom) 중 이 사람만 빼고 무작위로 다음
//       입찰자를 고른다 — ALREADY_TOP_BIDDER(7003)를 이렇게 줄인다.
//
// 이 방식은 로컬 상태가 전혀 없어서 VU 번호가 어떻게 배정되든 안전하다. 다만 **완벽한
// 보장은 아니다** — 상세를 읽은 시점과 입찰을 보내는 시점 사이에 다른 요청이 끼어들면
// 여전히 거절될 수 있다. 요구사항이 "0으로 강제"가 아니라 "최대한 줄이고, 난 만큼은
// 확인 가능하게"라 이 정도면 된다.
//
// **리젝트를 0으로 강제하지 않는다.** 8번과 달리 리젝트가 나와도 실행을 실패로 표시하지
// 않는다. bid.js 의 기존 카운터가 그대로 summary.json → index.csv → note.md 에 찍히는
// 것으로 충분하다.
//
// ── 워밍업 ──────────────────────────────────────────────────────────
// run.sh 가 RUN_ID 를 비운 채로 이 스크립트를 짧게 한 번 더 돌려서(1·2·6·7·8번과 같은
// 그룹) 데운다. 이때는 audience(SSE 램프)를 빼고 director 만 constant-vus(닫힌 모델)로
// 돌려 입찰 API 경로(JIT·DB 커넥션 풀)만 데운다 — SSE 는 순수 대기라 데울 코드 경로가
// 없고, 워밍업마다 SEEDED_USERS 까지 다시 램프하면 시간 대부분을 거기 쓰게 된다.
//
// 그래서 **입찰 경로는 데워지지만 SSE 구독·PARTICIPANT_COUNT_UPDATED 브로드캐스트 경로는
// 안 데워진다.** 측정 구간 초반 그 브로드캐스트의 p95 에 콜드스타트 영향이 섞일 수 있다.
//
// 볼 지표: upbid_sse_broadcast(입장·입찰 브로드캐스트 모두 여기 잡힌다), upbid_sse_connections,
//          upbid_sse_heartbeat, 상태 코드 201 의 응답 분위, dropped_iterations(--rate 가
//          방당 물품 3개의 실제 처리 한계를 넘었는지)

import http from 'k6/http'
import { check } from 'k6'
import {
  BASE, DURATION, RATE, ROOMS, SEEDED_USERS, VUS,
  agree, biddersOfRoom, loginAs, summaryTo,
} from './common.js'
import { countResult } from './bid.js'

const RAMP = __ENV.RAMP || '1m30s'
const BID_TIMING = __ENV.BID_TIMING || 'after-ramp'

// SSE 연결을 붙들고 있을 넉넉한 타임아웃. 실제 필요한 값(램프+유지)과 정확히 맞출 필요는
// 없다 — 3번 시나리오도, run.sh 의 배경 SSE 도 실제 측정 길이와 무관하게 넉넉한 고정값을 쓴다.
const HOLD = __ENV.SSE_HOLD || '30m'

// 물품을 방 순서대로 펼친 목록. director 가 매 요청마다 이 중 하나를 무작위로 고른다.
const FLAT_ITEMS = []
const ITEM_ROOM_INDEX = []

ROOMS.forEach((room, roomIndex) => {
  room.itemIds.forEach((itemId) => {
    FLAT_ITEMS.push(itemId)
    ITEM_ROOM_INDEX.push(roomIndex)
  })
})

// run.sh 는 워밍업 호출에서 RUN_ID 를 비운다(summaryTo 가 그걸 보고 요약 파일을 안 남긴다).
// 같은 신호로 "지금이 워밍업인지"를 안다.
const IS_WARMUP = !__ENV.RUN_ID

// 워밍업이 데우려는 건 입찰 API 경로(JIT·DB 커넥션 풀)다. audience(SSE 램프)는 뺀다 —
// 3번 시나리오도 원래 실부하 워밍업 대상이 아니고(순수 대기라 데울 코드 경로가 없다),
// 여기서 SEEDED_USERS 까지 매번 새로 램프하면 워밍업 시간의 대부분을 SSE 연결에 쓰게 된다.
//
// director 는 RATE=0(워밍업은 항상 닫힌 모델, common.js#baseOptions 와 같은 이유)일 때
// constant-arrival-rate 를 못 쓰므로 constant-vus 로 갈아탄다.
const directorScenario = RATE > 0
  ? {
      executor: 'constant-arrival-rate',
      exec: 'director',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: VUS,
      maxVUs: VUS,
      // after-ramp: 램프가 끝난 뒤부터. overlap: 램프와 동시(t=0)부터. 워밍업은 램프 자체가
      // 없으니 바로 시작한다.
      startTime: IS_WARMUP || BID_TIMING === 'overlap' ? '0s' : RAMP,
      gracefulStop: '15s',
    }
  : {
      executor: 'constant-vus',
      exec: 'director',
      vus: VUS,
      duration: DURATION,
      gracefulStop: '15s',
    }

export const options = {
  scenarios: IS_WARMUP
    ? { director: directorScenario }
    : {
        audience: {
          executor: 'ramping-vus',
          exec: 'audience',
          startVUs: 0,
          stages: [
            { duration: RAMP, target: SEEDED_USERS },
            { duration: DURATION, target: SEEDED_USERS },
          ],
          gracefulRampDown: '5s',
          gracefulStop: '15s',
        },
        director: directorScenario,
      },
  discardResponseBodies: true,
}

export function setup() {
  if (ROOMS.length === 0 || FLAT_ITEMS.length === 0) {
    throw new Error('방/물품이 비어 있다. run.sh 를 쓰거나 seed.sh 가 출력한 값을 넘겨야 한다.')
  }

  const badRoom = ROOMS.findIndex((r) => r.itemIds.length !== 3)

  if (badRoom !== -1) {
    throw new Error(`방 ${badRoom}의 물품이 ${ROOMS[badRoom].itemIds.length}개다(3개여야 한다). ` +
      '이 시나리오는 방당 물품 3개(운영 규칙)를 전제로 한다 — --per-room 3 으로 시딩됐는지 본다.')
  }

  if (SEEDED_USERS < ROOMS.length * 2) {
    throw new Error(`--users(${SEEDED_USERS})가 방 수(${ROOMS.length})의 2배보다 적다. ` +
      '방마다 최소 2명은 있어야 "최고 입찰자 제외" 로직이 매번 다른 사람을 고를 수 있다.')
  }

  if (BID_TIMING !== 'after-ramp' && BID_TIMING !== 'overlap') {
    throw new Error(`BID_TIMING 값이 이상하다: ${BID_TIMING} (after-ramp 또는 overlap 이어야 한다)`)
  }
}

/**
 * SSE 구독을 붙들고 있는다. 3번 시나리오와 같은 기법을 다중 방으로 확장한 것.
 * subscribe 는 @GuestAllowed 라 로그인 없이 붙는다.
 */
export function audience() {
  const room = ROOMS[(__VU - 1) % ROOMS.length]

  const res = http.get(`${BASE}/auction-rooms/share/${room.code}/subscribe`, {
    headers: { Accept: 'text/event-stream' },
    timeout: HOLD,
    responseType: 'none',
    tags: { name: 'sse-subscribe' },
  })

  check(res, {
    '연결이 측정 끝까지 유지됨': (r) => r.status === 200 || r.status === 0,
  })
}

/** key → 세션 쿠키. 이 워커가 한 번이라도 골랐던 계정은 다시 로그인하지 않는다. */
const sessions = new Map()

function freshJar() {
  return new http.CookieJar()
}

function sessionFor(key, roomCode) {
  if (sessions.has(key)) {
    return sessions.get(key)
  }

  const cookie = loginAs(key, freshJar())

  agree(roomCode, cookie, freshJar())
  sessions.set(key, cookie)

  return cookie
}

/** 현재 최고 입찰자(있으면)를 제외하고 이 방 구매자 풀에서 무작위로 고른다. */
function pickBidder(pool, excludeKey) {
  if (excludeKey === null || pool.length <= 1) {
    return pool[Math.floor(Math.random() * pool.length)]
  }

  let key

  do {
    key = pool[Math.floor(Math.random() * pool.length)]
  } while (key === excludeKey)

  return key
}

/**
 * 물품 상세에서 금액 계산에 필요한 값만 꺼낸다. bid.js#itemOf 와 같은 이유 —
 * 200 이 아니거나 모양이 다르면 null 을 준다. 여기서 예외를 던지면 부하가 조용히 준다.
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

export function director() {
  const index = Math.floor(Math.random() * FLAT_ITEMS.length)
  const itemId = FLAT_ITEMS[index]
  const roomIndex = ITEM_ROOM_INDEX[index]
  const room = ROOMS[roomIndex]
  const pool = biddersOfRoom(roomIndex)

  // 상세 조회는 @GuestAllowed 라 로그인 없이 읽는다.
  const detail = http.get(`${BASE}/auction-rooms/share/${room.code}/auction-items/${itemId}`, {
    responseType: 'text',
    tags: { name: 'item-detail' },
  })

  const item = itemOf(detail)

  if (item === null) {
    countResult(detail)
    return
  }

  // 방금 읽은 상세가 알려주는 실제 최고 입찰자. 로컬 상태를 안 믿고 매번 이 값을 쓴다.
  const currentTop = (item.leaderboard && item.leaderboard.length > 0)
    ? item.leaderboard[0].nickname
    : null

  const bidderKey = pickBidder(pool, currentTop)
  const cookie = sessionFor(bidderKey, room.code)
  const amount = item.currentPrice + item.bidIncrement

  const res = http.post(`${BASE}/auction-items/${itemId}/bids`, JSON.stringify({ amount }), {
    headers: { Cookie: `SESSION=${cookie}`, 'Content-Type': 'application/json' },
    jar: freshJar(),
    responseType: 'text',
    tags: { name: 'bid' },
  })

  countResult(res)
}

export function handleSummary(data) {
  return summaryTo(data)
}
