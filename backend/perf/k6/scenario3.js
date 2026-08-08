// 시나리오 3 — SSE 동시 접속.
//
// 접속을 50 → 800 으로 올린다. 무너질 곳은 둘이다.
//   * emitter 가 힙에 쌓인다 (접속당 메모리 x 접속 수)
//   * heartbeat 가 물품 마감 예약과 spring.task.scheduling.pool 을 같이 쓴다
//
// 볼 지표: upbid_sse_connections, 힙, upbid_sse_heartbeat p95
//          tomcat_threads_busy 가 접속 수만큼 오르면 async 가 안 걸린 것이고 그 자체가 발견이다
//
//   ./perf/run.sh --scenario 3 --vus 200
//
// ── k6 에는 SSE 클라이언트가 없다 ─────────────────────────────────
// 그래서 평범한 GET 을 걸어 두고 측정 시간만큼 안 끊는다. SSE 는 끝나지 않는 응답이라
// 요청이 그대로 열린 채 남고, 서버가 보기엔 진짜 구독자와 구분되지 않는다.
// responseType: 'none' 이라 계속 흘러오는 본문은 읽고 버린다 (안 그러면 k6 메모리가 찬다).
//
// 함정: macOS 기본 ulimit -n 이 낮아서 수백 개에서 먼저 터진다. 서버 한계로 착각하기 쉽다.
//       run.sh 가 시작할 때 확인해 준다.
// ────────────────────────────────────────────────────────────

import http from 'k6/http'
import { check } from 'k6'
import { BASE, SHARE_CODE, DURATION, baseOptions, summaryTo } from './common.js'

// 측정 구간보다 넉넉히 길게 잡아야 한 VU 가 연결을 두 번 맺지 않는다. 도중에 끊겼다
// 다시 붙으면 접속 수 그래프가 톱니처럼 흔들려서 "몇 명까지 버티나"를 못 읽는다.
const HOLD = __ENV.SSE_HOLD || '10m'

export const options = baseOptions({
  // 연결을 붙든 채 끝내야 하므로 기본 요청 타임아웃(60초)을 걷어낸다.
  scenarios: {
    main: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 50),
      duration: DURATION,
      gracefulStop: '5s',
    },
  },
  discardResponseBodies: true,
})

export function setup() {
  if (!SHARE_CODE) {
    throw new Error('SHARE_CODE 가 없다. run.sh 를 쓰거나 seed.sh 가 출력한 값을 넘겨야 한다.')
  }
}

export default function () {
  // subscribe 는 @GuestAllowed 라 로그인 없이 붙는다. 로그인을 끼우면 재는 대상에
  // 세션 발급이 섞인다.
  const res = http.get(`${BASE}/auction-rooms/share/${SHARE_CODE}/subscribe`, {
    headers: { Accept: 'text/event-stream' },
    timeout: HOLD,
    responseType: 'none',
    tags: { name: 'sse-subscribe' },
  })

  // 여기 도달했다는 건 연결이 끊겼다는 뜻이다. 측정 중에 이게 늘면 서버가 끊었거나
  // nginx proxy_read_timeout 이나 ulimit 에 걸린 것이다.
  check(res, {
    '연결이 측정 끝까지 유지됨': (r) => r.status === 200 || r.status === 0,
  })
}

export function handleSummary(data) {
  return summaryTo(data)
}
