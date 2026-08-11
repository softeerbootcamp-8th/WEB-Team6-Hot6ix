// 7번 — 절벽. 이 서비스에서 스파이크는 램프의 변형이 아니라 진짜 사용자 동선이다.
//
// 판매자가 링크와 QR 을 올리는 순간 사람이 한꺼번에 들어오고, 마감 직전 입찰도 같은 모양이다.
// 계단은 "이 부하를 버티나"를 재고 스파이크는 "갑자기 몰릴 때 어떻게 되나"를 잰다. 계단에서
// 멀쩡하던 부하도 절벽으로 오면 커넥션 풀과 스케줄러가 동시에 비어 있다가 한꺼번에 찬다.
//
//   ./perf/run.sh --scenario 7 --rate 200 --items 1
//
// 볼 것: 절벽 직후 hikari_pending 과 conn_acquire_p95 가 얼마나 튀고 몇 초 만에 돌아오는가.
// 안 돌아오면 그 rate 는 못 버티는 것이고, 돌아오면 그 시간이 곧 회복 시간이다.

import { baseOptions, summaryTo, RATE } from './common.js'
import { bidOnce, setupCheck } from './bid.js'

// --rate 를 안 주면 계단과 구분이 안 되는 실행이 된다. 여기서 멈추는 게 낫다.
if (RATE <= 0) {
  throw new Error('시나리오 7 은 --rate 가 있어야 한다. 예: --scenario 7 --rate 200')
}

export const options = baseOptions({
  scenarios: {
    spike: {
      executor: 'ramping-arrival-rate',

      // 0 에서 시작한다. 낮은 부하로 데우고 올리면 그건 절벽이 아니라 계단이다.
      // (워밍업은 run.sh 가 이 실행 앞에서 따로 돌린다.)
      startRate: 0,
      timeUnit: '1s',

      preAllocatedVUs: Math.max(RATE, 50),
      maxVUs: Math.max(RATE * 3, 150),

      stages: [
        { target: 0, duration: '20s' },       // 조용한 구간. 튄 값을 읽을 기준선이 된다
        { target: RATE, duration: '1s' },     // 절벽
        { target: RATE, duration: '1m' },     // 버티는지 본다
        { target: 0, duration: '1s' },        // 뚝 끊는다
        { target: 0, duration: '40s' },       // 회복 구간. 큐가 빠지는 데 걸리는 시간을 본다
      ],
      gracefulStop: '15s',
    },
  },

  thresholds: {
    // explore 와 같은 이유로 5xx 와 타임아웃에만 걸린다.
    http_req_failed: [{ threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '20s' }],
  },
})

export function setup() {
  setupCheck()
}

export default function () {
  bidOnce()
}

export function handleSummary(data) {
  return summaryTo(data)
}
