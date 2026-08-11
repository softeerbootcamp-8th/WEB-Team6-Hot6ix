// 6번 — 무릎 찾기 전용. **이 실행 결과는 판정에 쓰지 않는다.**
//
// 로컬은 무릎(처리량이 안 오르기 시작하는 지점)을 이미 아는데 배포는 모른다. 인스턴스 타입과
// nginx 값, CPU 크레딧이 다르기 때문이다. 계단을 10/20/40/80/160 으로 그냥 올리면 무릎을
// 건너뛸 수 있어서, 램프로 한 번 훑어 대략 어디인지 잡고 그 근처에 계단을 촘촘히 낸다.
// 무릎이 40~80 사이면 40/55/70/85 가 80/160 보다 훨씬 많은 걸 말한다.
//
// 램프라서 p95 가 구간 평균이 되고 max 계열이 램프 끝 값만 남는다. 그래서 이 줄은
// run.sh 가 status=aborted 로 남기고 노션 표에 안 붙인다.
//
//   ./perf/run.sh --scenario 6 --items 1
//
// 볼 것: 어느 rate 에서 accepted_per_s 가 평평해지는가, hikari_pending 이 언제 0을 벗어나는가

import { baseOptions, summaryTo } from './common.js'
import { bidOnce, setupCheck } from './bid.js'

export const options = baseOptions({
  scenarios: {
    explore: {
      executor: 'ramping-arrival-rate',
      startRate: 10,
      timeUnit: '1s',

      // 목표 rate x 예상 응답시간(초) 보다 넉넉히 잡는다. 모자라면 k6 가 도착률을 못 채우면서
      // 서버가 못 받은 것처럼 보인다.
      preAllocatedVUs: 200,
      maxVUs: 600,

      stages: [
        { target: 50, duration: '1m' },
        { target: 100, duration: '1m' },
        { target: 200, duration: '1m' },
        { target: 400, duration: '1m' },
      ],
      gracefulStop: '15s',
    },
  },

  thresholds: {
    // common.js 의 setResponseCallback 이 4xx 를 성공으로 세므로 이 임계값은 5xx 와
    // 타임아웃에만 걸린다. 정상 거절 때문에 멈추지 않는다.
    //
    // 배포에서 특히 중요하다. 5xx 가 나기 시작하면 k6 가 스스로 멈춰서, 남의 인프라(카카오,
    // NCP)와 RDS 를 계속 때리지 않는다.
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
