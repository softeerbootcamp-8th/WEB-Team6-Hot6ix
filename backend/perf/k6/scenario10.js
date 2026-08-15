// 시나리오 10 — SSE dispatch 부하 (가상 스레드 vs 플랫폼 스레드 비교)
//
// SSE 연결 N개를 유지한 채 입찰 이벤트를 R개/초 발생시켜
// 이벤트 fanout 지연과 자원 사용을 측정한다.
//
// 비교 방법:
//   가상 스레드:   ./perf/run.sh --scenario 10 --vus 200 --rate 30 --sse-vt
//   플랫폼 스레드: ./perf/run.sh --scenario 10 --vus 200 --rate 30
//
// 볼 지표
//   upbid_sse_broadcast_seconds   — 팬아웃 p95 (핵심. 연결 수 늘수록 오름)
//   upbid_sse_heartbeat_seconds   — heartbeat 지연
//   upbid_sse_connections         — 실제 붙은 연결 수
//   jvm_threads_live              — 플랫폼 스레드면 연결 수만큼 증가, VT면 변화 없음
//   힙(jvm_memory_used_bytes)     — VT는 유휴 연결에 스레드를 안 잡음
//
// VUS 파라미터 역할
//   --vus N   SSE 연결 수 (listener VU). 입찰자는 --rate 로 조정
//   --rate R  초당 입찰 수. 0이면 닫힌 모델(BID_VUS 고정)
//
// ── k6 SSE 클라이언트 구현 방식 ──────────────────────────────────
// scenario3.js 와 같은 방식: GET 에 responseType:'none' 을 걸어 두면
// 응답이 끝나지 않는 SSE 스트림을 k6 가 버리면서 연결만 유지한다.
// 서버가 보기엔 진짜 구독자와 구분되지 않는다.
// ────────────────────────────────────────────────────────────────

import http from 'k6/http'
import { check, sleep } from 'k6'
import {
  BASE, DURATION, RATE, VUS, SHARE_CODE,
  ensureSession, agree, authHeaders, summaryTo, roomOfVu, ROOMS,
} from './common.js'
import { bidOnce } from './bid.js'

const SSE_HOLD     = __ENV.SSE_HOLD     || '15m'  // 연결 유지 시간 (측정 구간보다 길게)
const BID_VUS      = Number(__ENV.BID_VUS || 20)   // 닫힌 모델일 때 입찰 VU 수

// SSE VU 수. --vus 가 연결 수 역할을 한다.
const LISTENER_VUS = VUS

export const options = {
  scenarios: {
    // ── SSE listener: 연결을 맺고 측정이 끝날 때까지 붙들고 있는다 ──
    sse_listeners: {
      executor: 'constant-vus',
      vus: LISTENER_VUS,
      duration: DURATION,
      gracefulStop: '5s',
      exec: 'sseListener',
      tags: { role: 'listener' },
    },

    // ── bidders: 이벤트를 발생시키는 입찰 VU ──────────────────────
    // --rate 를 주면 열린 모델(초당 도착 건수 고정), 없으면 닫힌 모델(VU 고정)
    bidders: RATE > 0
      ? {
          executor: 'constant-arrival-rate',
          rate: RATE,
          timeUnit: '1s',
          duration: DURATION,
          preAllocatedVUs: RATE * 2,
          maxVUs: RATE * 4,
          gracefulStop: '15s',
          exec: 'bidder',
          tags: { role: 'bidder' },
        }
      : {
          executor: 'constant-vus',
          vus: BID_VUS,
          duration: DURATION,
          gracefulStop: '15s',
          exec: 'bidder',
          tags: { role: 'bidder' },
        },
  },
  discardResponseBodies: true,
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)'],
}

export function setup() {
  if (!SHARE_CODE && ROOMS.length === 0) {
    throw new Error('SHARE_CODE 가 없다. run.sh 를 쓰거나 seed.sh 가 출력한 값을 넘겨야 한다.')
  }
}

// SSE 연결을 열고 측정이 끝날 때까지 유지한다.
// @GuestAllowed 라 로그인 없이도 붙는다. 로그인을 끼우면 재는 대상에 세션 발급이 섞인다.
export function sseListener() {
  const code = ROOMS.length > 0
    ? ROOMS[(__VU - 1) % ROOMS.length].code
    : SHARE_CODE

  const res = http.get(`${BASE}/auction-rooms/share/${code}/subscribe`, {
    headers: { Accept: 'text/event-stream' },
    timeout: SSE_HOLD,
    responseType: 'none',
    tags: { name: 'sse-subscribe' },
  })

  // 여기 도달 = 연결이 끊겼다. 측정 중에 이 check 가 실패로 늘면
  // 서버가 끊은 것(heartbeat 실패, 큐 포화)이거나 nginx timeout 이다.
  check(res, {
    '연결이 측정 끝까지 유지됨': (r) => r.status === 200 || r.status === 0,
  })
}

// 이벤트를 발생시키는 입찰 VU.
// bid.js 의 bidOnce 를 그대로 써서 현재가를 읽고 +단위 금액으로 입찰한다.
export function bidder() {
  if (ensureSession(`s10-bidder-${__VU}`)) {
    agree(roomOfVu().code || SHARE_CODE)
  }
  bidOnce()
  sleep(0.1)
}

export function handleSummary(data) {
  return summaryTo(data)
}
