// 시나리오 4 — 동시 마감.
//
// 같은 시각에 닫히는 물품을 5 → 50 으로 올린다. 무너질 곳은 둘이다.
//   * 마감 예약이 heartbeat 와 spring.task.scheduling.pool 4개를 같이 쓴다
//   * 마감도 입찰과 같은 물품 행 락을 잡는다
//
// 볼 지표: upbid_auction_close_delay p95
//
//   ./perf/run.sh --scenario 4 --items 50
//
// 다른 시나리오와 달리 VU 수가 변수가 아니다. 올리는 값은 "동시에 닫히는 물품 수"라
// VU 는 판매자 한 명으로 고정하고 --items 를 올린다.
//
// 정확히 같은 밀리초에 닫히지는 않는다. 물품 50개를 시작하는 데 몇 초가 걸려서 마감 시각도
// 그만큼 벌어진다. 재는 값이 ms 단위 지연이라 이 정도 편차는 결론을 바꾸지 않지만,
// "완전한 동시"는 아니라는 걸 알고 읽는다.

import http from 'k6/http'
import { check, sleep } from 'k6'
import { BASE, DURATION, authHeaders, ensureSession, summaryTo } from './common.js'

const CLOSE_ITEM_IDS = (__ENV.CLOSE_ITEM_IDS || '').split(',').filter(Boolean)

// 물품 시작 API 의 하한이 1분이다. 가장 짧게 잡아야 3분 측정 구간 안에 마감이 들어온다.
const DURATION_MINUTES = Number(__ENV.CLOSE_DURATION_MINUTES || 1)

export const options = {
  scenarios: {
    main: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: DURATION,
    },
  },
  discardResponseBodies: true,
}

export function setup() {
  if (CLOSE_ITEM_IDS.length === 0) {
    throw new Error('CLOSE_ITEM_IDS 가 비어 있다. run.sh 가 --start none 으로 시딩했는지 본다.')
  }
}

export default function () {
  // 물품을 시작할 수 있는 건 방 주인뿐이다. seed.sh 가 만든 판매자와 같은 key 로 붙는다.
  // 판매자는 자기 방에 약관 동의를 하지 않으므로 agree() 는 부르지 않는다.
  ensureSession('seller')

  let started = 0

  for (const itemId of CLOSE_ITEM_IDS) {
    const res = http.post(
      `${BASE}/auction-items/${itemId}/start`,
      JSON.stringify({ durationMinutes: DURATION_MINUTES }),
      { headers: authHeaders({ 'Content-Type': 'application/json' }), tags: { name: 'item-start' } },
    )

    if (res.status === 200 || res.status === 201) {
      started += 1
    }
  }

  check(null, {
    '물품이 하나라도 시작됨': () => started > 0,
    '요청한 물품이 전부 시작됨': () => started === CLOSE_ITEM_IDS.length,
  })

  // 마감이 실제로 일어나고 upbid_auction_close_delay 가 찍힐 때까지 기다린다.
  // 여기서 끝내 버리면 컨테이너가 내려가서 정작 재려던 순간을 못 본다.
  sleep(DURATION_MINUTES * 60 + 60)
}

export function handleSummary(data) {
  return summaryTo(data)
}
