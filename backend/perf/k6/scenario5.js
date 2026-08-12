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
import exec from 'k6/execution'
import { check, sleep } from 'k6'
import { BASE, DURATION, ROOMS, VUS, authHeaders, ensureSession, summaryTo } from './common.js'
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

// ── 마감 대상에도 초반에만 입찰을 넣는다 ──────────────────────────
// 위 방식대로 마감 대상을 그냥 두면 입찰이 하나도 없어서 전부 유찰로 닫힌다. 유찰은
// ItemPassed 라 낙찰 후보 생성(DealCandidateService.award)이 안 돈다. 그런데 그게 마감
// 소요 시간에 얹히는 조각 중 하나라, 지금까지는 그 조각을 아예 못 재고 있었다.
//
// 그래서 측정 시작 후 이 초까지만 마감 대상에도 입찰을 넣고 그 뒤로는 손을 뗀다. 임박
// 구간에 걸치면 마감이 계속 밀려서 또 안 닫히므로, run.sh 쪽에서 트리거를 낮춰 주고
// (물품 길이 - 트리거)보다 작은 값을 준다.
//
// 0 이면 예전 그대로 마감 대상에 입찰을 안 넣는다.
const CLOSE_BID_UNTIL = Number(__ENV.CLOSE_BID_UNTIL || 0)

/** 한 무리를 입찰용과 마감용으로 가른다. 최소 하나는 마감 쪽에 남겨야 마감 지표가 나온다. */
function splitGroup(ids, wanted) {
  if (ids.length < 2) {
    return { bid: [], close: ids }
  }

  const k = Math.min(Math.max(wanted, 1), ids.length - 1)

  return { bid: ids.slice(0, k), close: ids.slice(k) }
}

// ── 방이 여럿이면 방마다 따로 가른다 ──────────────────────────────
// 목록을 통째로 앞에서 자르면 안 된다. seed 가 물품을 방마다 연속으로 담기 때문에, 앞에서
// 자른 입찰 대상이 앞쪽 방 몇 개에 전부 몰린다. 뒤쪽 방을 맡은 VU 는 자기 방에 입찰 대상이
// 없어서 요청을 한 건도 못 보내는데, 4xx 도 에러도 안 남아서 결과만 봐서는 모른다.
// (VU 20 으로 적혀 있지만 실제로 부하를 만드는 건 절반뿐인 상태가 된다.)
//
// 방마다 가르면 어느 방에 붙은 VU 든 입찰 대상을 갖는다.
function splitTargets(ids) {
  const groups = ROOMS.map((r) => r.itemIds).filter((g) => g.length > 0)

  if (groups.length < 2) {
    return splitGroup(ids, BID_ITEMS > 0 ? BID_ITEMS : Math.floor(ids.length / 2))
  }

  // --bid-items 는 전체 개수라 방 수로 나눠 쓴다. 안 주면 방마다 반씩.
  const perRoom = BID_ITEMS > 0 ? Math.round(BID_ITEMS / groups.length) : 0
  const bid = []
  const close = []

  for (const group of groups) {
    const part = splitGroup(group, perRoom > 0 ? perRoom : Math.floor(group.length / 2))

    bid.push(...part.bid)
    close.push(...part.close)
  }

  return { bid, close }
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

  // 물품이 하나뿐인 방은 통째로 마감 쪽으로 간다. 그 방을 맡은 VU 는 CLOSE_BID_UNTIL 구간이
  // 지나면 놀게 되므로, 부하가 왜 모자란지 나중에 헤매지 않도록 여기서 알린다.
  const roomsWithoutBid = ROOMS.filter((r) => r.itemIds.length === 1).length

  if (roomsWithoutBid > 0) {
    console.warn(
      `물품이 1개뿐인 방이 ${roomsWithoutBid}개 있다. 그 방을 맡은 VU 는 입찰 대상이 없다. ` +
      '--items 를 --per-room 의 배수로 주면 없어진다.',
    )
  }
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

  // 못 시작한 물품은 그대로 마감 표본이 줄어드는 것이라 눈에 띄게 남긴다. check 만 실패시키면
  // summary.json 을 열어 보기 전까지 모르고, 결과 표에는 "마감이 왜 이것밖에 안 됐지"만 남는다.
  if (started !== CLOSE_ITEM_IDS.length) {
    console.error(
      `물품 ${CLOSE_ITEM_IDS.length}개 중 ${started}개만 시작됐다. ` +
      '동시 진행 상한(MAX_IN_PROGRESS_PER_ROOM)이나 방 물품 수 상한을 확인한다.',
    )
  }

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
/**
 * 측정이 시작되고 몇 초 지났는지. VU 마다 따로 도는 값을 쓰면 VU 별로 창이 닫히는 시각이
 * 달라져서, 늦게 뜬 VU 가 임박 구간에 입찰을 넣고 마감을 밀어 버린다.
 */
function elapsedSeconds() {
  return exec.instance.currentTestRunDuration / 1000
}

export function bidder() {

  // 초반에는 마감 대상까지 함께 노린다. 그래야 마감이 낙찰로 닫히고 낙찰 후보 생성까지
  // 마감 소요 시간에 들어온다. 창이 닫히면 마감 대상은 놔둬야 실제로 닫힌다.
  if (CLOSE_BID_UNTIL > 0 && elapsedSeconds() < CLOSE_BID_UNTIL) {
    if (bidOnce(CLOSE_ITEM_IDS) === null) {
      sleep(1)
    }

    return
  }

  // 이 방에 입찰 대상이 없으면 bidOnce 가 요청 없이 바로 null 을 준다. 그대로 두면 반복이
  // 즉시 끝나고 다시 시작하는 빈 루프가 되어, 부하는 안 만들면서 k6 CPU 만 태운다.
  if (bidOnce(TARGETS.bid) === null) {
    sleep(1)
  }
}

export function handleSummary(data) {
  return summaryTo(data)
}
