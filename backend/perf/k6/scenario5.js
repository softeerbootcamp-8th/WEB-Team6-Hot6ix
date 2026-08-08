// 시나리오 5 — 마감과 입찰을 겹친다. (콘솔에서는 "6번 마감 + 입찰 겹치기")
//
// 시나리오 4 가 재현하지 못하는 상황을 만든다. 4 는 판매자 한 명이 물품을 시작하고 마감될
// 때까지 잠만 자기 때문에 입찰이 없고, 마감끼리는 서로 다른 물품 행이라 락 경합이 아예 안
// 생긴다. 실측으로 물품 20개가 0.25초 만에 다 닫혔다. 그 상태에서는 --scheduler-pool 을 1로
// 내리든 16으로 올리든 그래프가 평평해서 4가 맞는 값인지 말할 수 없다.
//
// 여기서 만들려는 그림은 이것이다.
//
//   입찰 VU 가 물품에 계속 입찰      → 물품 행에 락이 붙어 있다
//   같은 순간 그 물품들의 마감이 깨어남 → 스케줄러 스레드가 FOR UPDATE 를 기다린다
//   스레드와 커넥션이 락 대기로 묶임    → heartbeat 가 실행 슬롯을 못 얻는다
//
// 볼 지표: upbid_auction_close_duration (락을 기다린 시간),
//          executor_queued_tasks{name="taskScheduler"}, upbid_sse_heartbeat
//
//   ./perf/run.sh --scenario 5 --vus 40 --items 20

import http from 'k6/http'
import { check, sleep } from 'k6'
import { BASE, DURATION, VUS, authHeaders, ensureSession, summaryTo } from './common.js'
import { bidOnce } from './bid.js'

// 시딩을 --start none 으로 하므로 물품은 여기 담겨 온다. ITEM_IDS 는 비어 있다.
const CLOSE_ITEM_IDS = (__ENV.CLOSE_ITEM_IDS || '').split(',').filter(Boolean)

// 물품 시작 API 의 하한이 1분이다. 가장 짧게 잡아야 측정 구간 안에 마감이 들어온다.
const DURATION_MINUTES = Number(__ENV.CLOSE_DURATION_MINUTES || 1)

// ── 물품을 두 무리로 나눈다 ────────────────────────────────────────
// 전부에 입찰하면 마감이 한 건도 안 일어난다. 방의 Soft Close 트리거가 60초인데 물품 길이도
// 60초라(API 하한) 시작하자마자 임박 구간이고, 입찰이 들어올 때마다 60초씩 밀린다. 누적 상한
// 3600초에 걸리는 건 1시간 뒤라 3분짜리 측정에는 영원히 안 닫힌다. 실측으로 확인했다 —
// close_delay 와 close_duration 이 전부 NaN 으로 나왔다.
//
// 그래서 앞쪽 몇 개에만 입찰을 넣는다. 입찰이 붙은 물품은 계속 밀리면서 예약 취소와 재등록을
// 만들고(스케줄러 큐가 쌓인다), 입찰이 없는 물품은 제때 닫혀서 마감 지표를 남긴다.
//
// 대신 "마감이 같은 물품의 입찰 락을 기다리는" 그림은 이 방법으로 재현되지 않는다. 여기서
// 재는 것은 입찰 부하가 스레드와 커넥션을 잡고 있을 때 마감이 얼마나 밀리는가다.
const BID_ITEMS = Number(__ENV.BID_ITEMS || 0)

function splitTargets(ids) {
  if (ids.length < 2) {
    return { bid: [], close: ids }
  }

  // 안 주면 반씩. 최소 하나는 마감 쪽에 남겨야 마감 지표가 나온다.
  const wanted = BID_ITEMS > 0 ? BID_ITEMS : Math.floor(ids.length / 2)
  const k = Math.min(Math.max(wanted, 1), ids.length - 1)

  return { bid: ids.slice(0, k), close: ids.slice(k) }
}

const TARGETS = splitTargets(CLOSE_ITEM_IDS)

// closer 가 물품을 다 시작하기 전에 입찰이 들어가면 전부 진행중이 아닌 물품으로 거절된다.
// 그 거절은 4xx 로 세어져서 run.sh 가 "세팅이 잘못됐다"고 경고한다. --items 를 크게 올리면
// 이 값도 같이 올려야 한다.
const BID_START = __ENV.BID_START || '10s'

export const options = {
  scenarios: {
    closer: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      exec: 'closer',
      maxDuration: DURATION,
    },
    bidders: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
      exec: 'bidder',
      startTime: BID_START,
      gracefulStop: '15s',
    },
  },
  discardResponseBodies: true,
}

export function setup() {
  if (CLOSE_ITEM_IDS.length === 0) {
    throw new Error('CLOSE_ITEM_IDS 가 비어 있다. run.sh 가 --start none 으로 시딩했는지 본다.')
  }

  if (TARGETS.bid.length === 0) {
    throw new Error('물품이 하나뿐이라 입찰과 마감을 나눌 수 없다. --items 를 2 이상으로 준다.')
  }

  console.log(`입찰 대상 ${TARGETS.bid.length}개 / 마감 대상 ${TARGETS.close.length}개`)
}

/** 판매자 한 명이 물품을 모두 시작해서 같은 시각에 닫히게 만든다. 시나리오 4 와 같다. */
export function closer() {

  // 물품을 시작할 수 있는 건 방 주인뿐이다. 판매자는 자기 방에 약관 동의를 하지 않는다.
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

  // 마감이 실제로 일어나고 지표가 찍힐 때까지 남아 있는다. 여기서 끝내면 컨테이너가 내려가
  // 정작 재려던 순간을 못 본다.
  sleep(DURATION_MINUTES * 60 + 60)
}

/**
 * 구매자들이 <b>입찰 대상 물품에만</b> 계속 입찰한다.
 *
 * constant-vus 라 구간 내내 고르게 깔린다. 마감 순간에 몰아넣으면 락 경합이 부하 발생기 쪽
 * 타이밍에 좌우돼서 재현이 안 된다.
 *
 * 이 물품들은 입찰 때문에 Soft Close 로 계속 밀린다. 그때마다 예약이 취소되고 다시 걸려서
 * 스케줄러 큐가 쌓이는데, 그게 이 시나리오가 보려는 것 중 하나다.
 */
export function bidder() {
  bidOnce(TARGETS.bid)
}

export function handleSummary(data) {
  return summaryTo(data)
}
