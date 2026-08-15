// 시나리오 10 — 안정화된 SSE 구독자에게 입찰 이벤트를 fan-out한다.
//
// run.sh 실행 순서
//   1. perf/sse-client가 --vus N개의 실제 SSE 연결을 2분에 걸쳐 올린다.
//   2. 목표 연결 수 도달 후 안정화 시간을 기다린다.
//   3. 이 스크립트가 --rate R건/초로 입찰을 발생시킨다.
//
// 연결 폭주와 이벤트 전송을 같은 시각에 시작하면 참가자 수 이벤트가 O(N²)로 쏟아져,
// 비교 대상인 입찰 fan-out 전에 큐가 포화된다. 연결 수립과 안정 구간을 측정 전에 분리한 이유다.
// 실제 SSE 프레임 수신·누락·중복·순서·E2E 지연은 전용 클라이언트가 Prometheus로 노출한다.
//
//   ./perf/run.sh --scenario 10 --vus 200 --rate 30 --sse-vt
//   ./perf/run.sh --scenario 10 --vus 200 --rate 30 --no-sse-vt --sse-pool 4

import { sleep } from 'k6'
import {
  DURATION, RATE, SHARE_CODE,
  ensureSession, agree, summaryTo, roomOfVu, ROOMS,
} from './common.js'
import { bidOnce } from './bid.js'

const BID_VUS = Number(__ENV.BID_VUS || 20)

export const options = {
  scenarios: {
    bidders: RATE > 0
      ? {
          executor: 'constant-arrival-rate',
          rate: RATE,
          timeUnit: '1s',
          duration: DURATION,
          preAllocatedVUs: Math.max(RATE * 2, 1),
          maxVUs: Math.max(RATE * 4, 1),
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
    throw new Error('SHARE_CODE가 없다. run.sh를 쓰거나 seed.sh가 출력한 값을 넘겨야 한다.')
  }
}

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
