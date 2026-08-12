// 시나리오 스크립트 넷이 함께 쓰는 부분.
//
// 여기 있는 것을 각 스크립트가 제 손으로 다시 쓰면, 누구는 4xx 를 실패로 세고 누구는 안 세는
// 식으로 갈려서 다섯 명의 표를 못 합친다.

import http from 'k6/http'

export const BASE = __ENV.BASE_URL || 'http://nginx/api/v1'
export const ROOT = BASE.replace(/\/api\/v1$/, '')

export const VUS = Number(__ENV.VUS || 10)
export const DURATION = __ENV.DURATION || '3m'

export const SHARE_CODE = __ENV.SHARE_CODE || ''
export const ITEM_IDS = (__ENV.ITEM_IDS || '').split(',').filter(Boolean)

// ── 방이 여럿일 때 ────────────────────────────────────────────────
// 서비스 규칙이 방당 동시 진행 3개라, 물품을 여러 개 진행하려면 방을 나눠야 한다.
// seed.sh 가 --per-room 을 받으면 방을 여러 개 만들고 구매자 i 를 방 (i-1)%방수 에 붙인다.
//
// **VU 도 같은 규칙을 써야 한다.** 그 사람이 동의한 방이 아닌 곳에 입찰하면 전부
// TERMS_NOT_AGREED 로 거절되는데, 4xx 라서 표에서는 정상 거절처럼 보인다.
//
// 방이 하나면 목록에 값이 하나씩만 들어와서 예전과 똑같이 돈다.
const SHARE_CODES = (__ENV.SHARE_CODES || SHARE_CODE).split(',').filter(Boolean)
const ROOM_ITEM_GROUPS = (__ENV.ROOM_ITEM_IDS || '')
  .split(';').filter(Boolean)
  .map((g) => g.split(',').filter(Boolean))

export const ROOMS = SHARE_CODES.map((code, i) => ({
  code,
  itemIds: ROOM_ITEM_GROUPS[i] || [],
}))

/** 이 VU 가 맡은 방. seed.sh 의 배정 규칙과 같아야 한다. */
export function roomOfVu() {
  return ROOMS.length > 0
    ? ROOMS[(__VU - 1) % ROOMS.length]
    : { code: SHARE_CODE, itemIds: ITEM_IDS }
}

/** seed.sh 가 시딩한 총 구매자 수. 9번(방 입장 램프)이 방별 구매자 풀을 역산하는 데 쓴다. */
export const SEEDED_USERS = Number(__ENV.SEEDED_USERS || 0)

/**
 * roomIndex(0-based)에 배정된 구매자의 dev-login key 목록.
 *
 * seed.sh 의 라운드로빈 배정(`구매자 i → 방 (i-1) % 방수`)을 그대로 역산한 것이라, seed.sh 가
 * 바뀌면 여기도 같이 바뀌어야 한다. 새 env 를 추가하는 대신 이미 있는 SEEDED_USERS 와
 * ROOMS.length 로 계산하는 이유는, 이 값이 방마다 정확히 몇 명인지 세는 게 아니라 "이 방을
 * 담당한 사람이 누구누구인가"만 필요해서다.
 */
export function biddersOfRoom(roomIndex) {
  const roomCount = ROOMS.length
  const keys = []

  for (let i = roomIndex + 1; i <= SEEDED_USERS; i += roomCount) {
    keys.push(`bidder-${i}`)
  }

  return keys
}

export const START_PRICE = Number(__ENV.START_PRICE || 10000)
export const BID_UNIT = Number(__ENV.BID_UNIT || 1000)

// 4xx 를 실패로 세지 않는다.
//
// ALREADY_TOP_BIDDER, BID_AMOUNT_TOO_LOW, INVALID_BID_UNIT, ITEM_CLOSED 는 규칙대로 거절한
// 정상 동작이다. 기본값(2xx·3xx만 성공)을 그대로 두면 한 물품에 160명을 몰았을 때 실패율이
// 90% 로 찍히고, 그걸 보고 서버가 터졌다고 읽게 된다. 진짜 실패는 5xx 와 타임아웃뿐이다.
//
// options.responseCallback 으로 주면 안 된다. k6 1.x 가 모르는 필드라고 경고만 하고 무시한다
// (실측: 그 상태에서 http_req_failed 가 99.9% 로 찍혔다). 이 함수로 init 단계에 걸어야 먹는다.
http.setResponseCallback(http.expectedStatuses({ min: 200, max: 499 }))

/** 초당 도착 건수. 0 이면 닫힌 모델(constant-vus)로 돈다. run.sh 의 --rate 가 정한다. */
export const RATE = Number(__ENV.RATE || 0)

/**
 * 모든 시나리오가 공유하는 실행 형태. 계단 하나 = 실행 하나다.
 *
 * **--rate 를 주면 열린 모델로 바뀐다.** 기본은 예전과 같은 constant-vus 다.
 *
 * 닫힌 모델은 VU 가 응답을 받은 뒤에 다음 요청을 보내서, 서버가 느려지면 부하가 자동으로
 * 줄어든다. 큐가 쌓이는 모습과 붕괴 지점이 안 드러나고, "초당 몇 건까지 버티나"를 말할 수
 * 없다. 실제 사용자는 서버 응답을 기다려 주지 않는다.
 *
 * 부수 효과가 더 크다. vus 는 사람 수가 아니라서(생각하는 시간이 없어 한 줄이 여러 명 몫을
 * 한다) 발표에서 쓸 수 없는 단위인데, 도착률로 재면 "동시 참여자 200명이 평균 3초에 한 번
 * 입찰 = 초당 66건"으로 환산해 말할 수 있다.
 */
export function baseOptions(extra = {}) {
  return {
    scenarios: {
      main: RATE > 0
        ? {
            executor: 'constant-arrival-rate',
            rate: RATE,
            timeUnit: '1s',
            duration: DURATION,
            // 도착률을 채울 VU 가 모자라면 k6 가 요청을 못 보내면서 "서버가 못 받은 것"처럼
            // 보인다. VUS 를 여기 쓰는 이유고, run.sh 가 목표 rate 에 맞춰 넉넉히 넘긴다.
            preAllocatedVUs: VUS,
            maxVUs: VUS,
            gracefulStop: '15s',
          }
        : {
            executor: 'constant-vus',
            vus: VUS,
            duration: DURATION,
            gracefulStop: '15s',
          },
    },
    // 응답 본문을 안 들고 있는다. 물품 목록처럼 큰 응답을 VU 수백 개가 붙들면
    // 재는 대상이 서버가 아니라 k6 의 메모리가 된다.
    discardResponseBodies: true,
    ...extra,
  }
}

// ── 세션을 손으로 들고 다니는 이유 ──────────────────────────────────
// k6 의 쿠키 항아리는 VU 마다 하나지만 **반복이 끝날 때 비워진다.** 그래서 흔히 쓰는
//
//     if (__ITER === 0) { http.post('/auth/dev-login') }
//
// 형태는 첫 반복만 로그인 상태이고 두 번째 반복부터 전부 401 이 된다. 실측으로 확인했다
// (5 VU, 40초: 입찰 성공 1건, 나머지 28,187건 401).
//
// 401 은 4xx 라서 "정상 거절"로 세어지기 때문에 그래프만 봐서는 안 드러난다. 락 대기가
// NaN 이고 커넥션이 0인 걸 보고서야 알게 된다. 그러니 세션 값을 직접 들고 헤더로 붙인다.
// 모듈 스코프 변수는 VU 마다 따로 만들어지고 VU 가 사는 동안 남는다.
// ────────────────────────────────────────────────────────────────
let sessionCookie = null

/**
 * 배포에서는 dev-login 이 토큰을 아는 요청만 받는다 (#266). 로컬 perf 는 토큰이 없어서
 * 빈 객체가 나가고, 앱도 안 걸어 뒀으므로 지금까지와 똑같이 동작한다.
 */
function devLoginHeaders() {
  const token = __ENV.DEV_LOGIN_TOKEN

  return token ? { 'X-Dev-Login-Token': token } : {}
}

/**
 * dev-login 으로 세션을 받는다. key 가 곧 회원 하나라 VU 마다 다른 key 를 준다.
 *
 * key 없이 부르면 전원이 같은 회원이 되어 두 번째 입찰부터 ALREADY_TOP_BIDDER 로 거절된다.
 *
 * @return 이번에 새로 로그인했으면 true, 이미 세션이 있었으면 false
 */
export function ensureSession(key) {
  if (sessionCookie !== null) {
    return false
  }

  sessionCookie = loginAs(key)

  return true
}

/**
 * dev-login 으로 세션 쿠키 값을 받아 돌려준다. <b>모듈 상태를 건드리지 않는다.</b>
 *
 * {@link ensureSession} 은 "VU 하나 = 사람 하나"를 전제로 하는데, 8번(브로드캐스트)은 한
 * 물품에 두 사람을 교대로 붙여야 해서 — 같은 사람이 연달아 넣으면 ALREADY_TOP_BIDDER 다 —
 * VU 하나가 쿠키를 여럿 들어야 한다. 그래서 반환형으로 갈라 두었다.
 */
export function loginAs(key, jar = null) {
  const res = http.post(`${BASE}/auth/dev-login?key=${encodeURIComponent(key)}`, null, {
    headers: devLoginHeaders(),
    // 빈 항아리를 주면 앞사람 세션이 딸려 나가지 않는다. 자세한 이유는 broadcast.js 참고.
    jar: jar || undefined,
    tags: { name: 'dev-login' },
  })

  // 값이 빈 Set-Cookie 는 세션을 지우는 쪽이다. 남은 것 중 마지막이 이번에 발급된 세션이다.
  const values = (res.cookies['SESSION'] || [])
    .map((c) => c.value)
    .filter((v) => v && v.length > 0)

  if (values.length === 0) {
    throw new Error(`dev-login 이 SESSION 쿠키를 안 줬다 (status=${res.status}). ` +
      '로컬이면 perf 프로파일에 DevAuthController 가 올라왔는지, 배포면 DEV_LOGIN_TOKEN 을 ' +
      '앱과 여기(DEV_LOGIN_TOKEN 환경변수) 양쪽에 같은 값으로 줬는지 본다.')
  }

  return values[values.length - 1]
}

/** 로그인한 사람으로 요청하려면 이걸 거쳐야 한다. 세션이 없으면 그냥 비로그인 요청이 된다. */
export function authHeaders(extra) {
  const headers = extra ? Object.assign({}, extra) : {}

  if (sessionCookie !== null) {
    headers.Cookie = `SESSION=${sessionCookie}`
  }

  return headers
}

/**
 * 방 약관에 동의한다. 이 행이 없으면 입찰이 TERMS_NOT_AGREED 로 전부 거절된다.
 *
 * seed.sh 가 이미 만들어 두지만 여기서도 한 번 부른다. 스크립트만 따로 돌려 보는 사람이
 * 원인을 모른 채 전부 거절당하는 것을 막기 위해서다. 같은 사람이 여러 번 불러도 안전하다.
 */
export function agree(shareCode = SHARE_CODE, cookie = null, jar = null) {
  return http.post(`${BASE}/auction-rooms/share/${shareCode}/agreement`, null, {
    // cookie 를 주면 그 사람으로 동의한다. 모듈 세션(ensureSession)을 안 쓰는 8번용이다.
    headers: cookie ? { Cookie: `SESSION=${cookie}` } : authHeaders(),
    jar: jar || undefined,
    tags: { name: 'agreement' },
  })
}

/**
 * 결과 폴더에 k6 요약을 남긴다. run.sh 가 여기서 accepted/rejected/failed 를 뽑아
 * index.csv 에 적는다.
 */
export function summaryTo(data) {
  const runId = __ENV.RUN_ID
  const out = { stdout: '' }

  if (runId) {
    out[`/results/${runId}/summary.json`] = JSON.stringify(data, null, 2)
  }

  return out
}
