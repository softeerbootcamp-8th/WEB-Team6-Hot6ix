// 9번 — 입장 폭주. 링크와 QR 을 뿌린 직후 사람들이 방을 여는 순간이다.
//
// 이 서비스는 링크로 들어오는 서비스라 조회가 고르게 깔리지 않는다. 판매자가 SNS 에 링크를
// 올리는 순간 한꺼번에 몰리고, 그때 나가는 요청은 입찰이 아니라 방 페이지의 조회 둘이다.
//
//   GET /auction-rooms/share/{shareCode}
//   GET /auction-rooms/share/{shareCode}/auction-items
//
// 한 반복 = 방문자 한 명이다. 화면이 두 요청을 이어서 보내므로 여기서도 이어서 보낸다.
// 둘을 따로 재고 싶어서 태그를 나눠 달았다 — 캐시를 붙이면 둘의 효과가 다르다.
//
//   ./perf/run.sh --scenario 9 --rate 200
//
// 볼 것: 두 조회의 p95 와 처리량, 그때의 hikaricp_connections_pending 과 conn_acquire_p95.
// 조회가 병목으로 안 잡히면 캐시를 붙이지 않는다 (#320). 캐시는 무효화라는 숙제가 새로
// 생기는 일이라 필요할 때만 붙인다.
//
// ── 시나리오 3 과 다르다 ──────────────────────────────────────────
// 3 도 같은 방의 share/{code}/subscribe 를 때리지만 그건 SSE 연결을 붙들어 두는 용도다.
// 끝나지 않는 스트림이라 응답 시간에 안 잡히고, 재는 것도 "몇 명까지 붙나"다. 여기서 재는
// 것은 끝나는 요청의 응답 시간과 처리량이라 성격이 다르다.
//
// ── 로그인하지 않는다 ─────────────────────────────────────────────
// 두 경로 다 @GuestAllowed 라 세션 없이 열리고, 링크로 처음 들어온 사람이 실제로 그 상태다.
// 로그인을 끼우면 재는 대상에 세션 발급과 조회가 섞인다.
//
// 대신 **로그인한 사람에게만 붙는 참여자 동의 조회는 이 실행에 안 잡힌다.**
// getRoomByShareCode 가 viewerUserId 가 있을 때만 auction_participants 를 한 번 더 읽는다.

import http from 'k6/http'
import { check } from 'k6'
import { Counter } from 'k6/metrics'
import { BASE, VUS, DURATION, RATE, SHARE_CODE, baseOptions, roomOfVu, summaryTo } from './common.js'

// ── 4xx 를 세는 이유 ──────────────────────────────────────────────
// 공개 조회에는 거절 규칙이 없다. 그래서 여기 4xx 가 찍히면 전부 세팅 실수다 — 공유 코드를
// 잘못 넘겼거나 방이 지워졌거나다. 그런데 common.js 가 4xx 를 성공으로 세기 때문에(입찰의
// 정상 거절을 실패로 안 세려고 그렇게 뒀다) 그래프만 봐서는 안 드러나고, 서버가 404 를
// 아주 빨리 주는 만큼 처리량과 p95 는 오히려 좋아 보인다.
//
// run.sh 가 이 값을 읽어 경고한다.
const readOk = new Counter('enter_ok')
const read4xx = new Counter('enter_4xx')     // 0 이어야 한다. 크면 그 실행은 못 쓴다
const readFailed = new Counter('enter_failed') // 5xx·타임아웃

// --rate 를 안 주면 절벽이 안 만들어진다. 컨테이너를 다 띄우고 시딩까지 한 뒤에 알면 늦으니
// run.sh 가 인자 파싱 직후에 한 번 더 막지만, 스크립트만 따로 돌리는 사람을 위해 여기도 둔다.
if (RATE <= 0) {
  throw new Error('시나리오 9 는 --rate 가 있어야 한다. 예: --scenario 9 --rate 200')
}

// 절벽을 유지할 시간. run.sh 의 --duration 이 그대로 온다. 앞 구간은 여기서 고정이라
// 실제 실행 길이는 이 값보다 21초 길다.
const HOLD = DURATION

// ── 워밍업은 절벽으로 데우지 않는다 ───────────────────────────────
// run.sh 가 본 측정과 같은 스크립트로 데우는데, 절벽 모양을 그대로 쓰면 앞의 조용한 20초를
// 워밍업에서도 한 번 더 기다린다. 데우는 게 목적이라 평평한 한 계단으로 돈다.
const WARMUP_MODE = __ENV.ENTER_WARMUP === '1'

const STAGES = WARMUP_MODE
  ? [{ target: RATE, duration: HOLD }]
  : [
      { target: 0, duration: '20s' },       // 조용한 구간. 튄 값을 읽을 기준선이 된다
      { target: RATE, duration: '1s' },     // 링크를 뿌린 순간
      { target: RATE, duration: HOLD },     // 버티는지 본다. 전후 비교는 이 구간이 주인공이다
      { target: 0, duration: '1s' },        // 뚝 끊는다
    ]

// ── 회복 구간을 뒤에 안 붙인다 ────────────────────────────────────
// spike.js 처럼 { target: 0, duration: '30s' } 를 하나 더 붙여 큐가 빠지는 걸 보려 했는데,
// k6 가 남은 계단이 전부 0 이면 더 스케줄할 게 없다고 보고 거기서 실행을 끝낸다. 실측으로
// 1m52s 짜리 실행이 1m22s 에 끝나면서 진행 표시에 ✗ 만 남았다 (실패가 아닌데 실패로 읽힌다).
//
// run.sh 의 측정 구간도 k6 가 끝나는 시각에 닫히므로, 어차피 그 30초는 못 본다.

export const options = baseOptions({
  scenarios: {
    enter: {
      executor: 'ramping-arrival-rate',

      // 0 에서 시작한다. 낮은 부하로 올려 가면 그건 절벽이 아니라 계단이다.
      // (워밍업은 run.sh 가 이 실행 앞에서 따로 돌린다.)
      startRate: 0,
      timeUnit: '1s',

      // 도착률을 채울 VU 가 모자라면 k6 가 요청을 못 보내면서 "서버가 못 받은 것"처럼 보인다.
      // 한 반복이 요청 둘이라 입찰 시나리오보다 반복이 오래 걸리는 만큼 넉넉히 잡는다.
      preAllocatedVUs: Math.max(RATE, VUS, 50),
      maxVUs: Math.max(RATE * 3, VUS, 150),

      stages: STAGES,
      gracefulStop: '15s',
    },
  },

  thresholds: {
    // common.js 의 setResponseCallback 이 4xx 를 성공으로 세므로 5xx 와 타임아웃에만 걸린다.
    // 다만 이 시나리오에서 4xx 는 정상 거절이 아니라 세팅 실수다 (공개 조회에 거절 규칙이
    // 없다). 그건 아래 check 로 잡는다.
    http_req_failed: [{ threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '20s' }],
  },
})

export function setup() {
  if (!SHARE_CODE) {
    throw new Error('SHARE_CODE 가 없다. run.sh 를 쓰거나 seed.sh 가 출력한 값을 넘겨야 한다.')
  }
}

/** 응답 하나를 세 칸(정상·세팅 실수·실패) 중 하나에 넣는다. */
function count(res) {
  if (res.status >= 200 && res.status < 300) {
    readOk.add(1)
  } else if (res.status >= 400 && res.status < 500) {
    read4xx.add(1)
  } else {
    // status 0 은 타임아웃이나 연결 실패다.
    readFailed.add(1)
  }
}

export default function () {
  // 방이 여럿이면 VU 를 방마다 갈라 붙인다. seed.sh 의 배정 규칙과 같다.
  // 한 방에 다 몰면 캐시를 붙였을 때 항목 하나만 데워져서 실제보다 잘 나온다.
  const code = roomOfVu().code

  const room = http.get(`${BASE}/auction-rooms/share/${code}`, {
    tags: { name: 'room-public' },
  })

  count(room)
  check(room, { '방 조회가 200': (r) => r.status === 200 })

  const items = http.get(`${BASE}/auction-rooms/share/${code}/auction-items`, {
    tags: { name: 'room-items' },
  })

  count(items)
  check(items, { '물품 목록이 200': (r) => r.status === 200 })
}

export function handleSummary(data) {
  return summaryTo(data)
}
