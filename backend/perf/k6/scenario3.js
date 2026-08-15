// 시나리오 3 — SSE 연결 유지·정확성 측정의 시간축.
//
// 실제 구독자는 perf/sse-client/client.mjs다. k6의 일반 HTTP API로 GET을 오래 열어 두면
// 서버 연결 수는 만들 수 있지만 프레임별 callback이 없어 누락·중복·순서·수신 지연을 잴 수
// 없다. run.sh가 먼저 전용 구독자를 목표 연결 수까지 ramp-up한 뒤 이 스크립트를 실행한다.
//
// 여기서는 측정 구간 동안 1 VU만 유지해 summary.json과 k6 실행 시간축을 남긴다.
// 직접 실행하면 SSE 부하가 생기지 않으므로 반드시 run.sh로 실행한다.

import { sleep } from 'k6'
import { DURATION, summaryTo } from './common.js'

export const options = {
  scenarios: {
    observation_clock: {
      executor: 'constant-vus',
      vus: 1,
      duration: DURATION,
      gracefulStop: '1s',
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)'],
}

export default function () {
  sleep(1)
}

export function handleSummary(data) {
  return summaryTo(data)
}
