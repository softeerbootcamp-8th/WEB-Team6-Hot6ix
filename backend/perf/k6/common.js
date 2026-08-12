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
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)'],
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

  const res = http.post(`${BASE}/auth/dev-login?key=${encodeURIComponent(key)}`, null, {
    headers: devLoginHeaders(),
    tags: { name: 'dev-login' },
  })

  const cookie = res.cookies['SESSION']

  if (!cookie || cookie.length === 0) {
    throw new Error(`dev-login 이 SESSION 쿠키를 안 줬다 (status=${res.status}). ` +
      '로컬이면 perf 프로파일에 DevAuthController 가 올라왔는지, 배포면 DEV_LOGIN_TOKEN 을 ' +
      '앱과 여기(DEV_LOGIN_TOKEN 환경변수) 양쪽에 같은 값으로 줬는지 본다.')
  }

  sessionCookie = cookie[0].value

  return true
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
export function agree() {
  return http.post(`${BASE}/auction-rooms/share/${SHARE_CODE}/agreement`, null, {
    headers: authHeaders(),
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
