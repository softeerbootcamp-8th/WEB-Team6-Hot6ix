// Consumer를 끈 상태에서 실제 입찰 API와 Lua를 통과한 승인 이벤트로 Stream backlog를 만든다.
// MySQL 현재가는 Consumer OFF 동안 갱신되지 않으므로 상세 API를 읽지 않고, 기준 시각부터
// 단조 증가하는 격자 금액을 사용한다. 네트워크 순서가 뒤집힌 요청 일부는 정상 거절될 수 있다.

import { baseOptions, summaryTo, ITEM_IDS, VUS, START_PRICE, BID_UNIT,
  ensureSession, agree, roomOfVu } from './common.js'
import { setupCheck, submitBid } from './bid.js'

const BACKLOG_EPOCH_MS = Number(__ENV.BACKLOG_EPOCH_MS || Date.now())
const MAX_BID_AMOUNT = 5_000_000_000_000

export const options = baseOptions()

export function setup() {
  setupCheck()
}

export default function () {
  const room = roomOfVu()

  if (ensureSession(`bidder-${__VU}`)) {
    agree(room.code)
  }

  const itemIds = room.itemIds.length > 0 ? room.itemIds : ITEM_IDS
  if (itemIds.length === 0) {
    return
  }

  const itemId = itemIds[(__ITER + __VU - 1) % itemIds.length]
  const elapsedMillis = Math.max(Date.now() - BACKLOG_EPOCH_MS, 0)
  const sequence = elapsedMillis * (VUS + 1) + __VU
  const amount = START_PRICE + BID_UNIT * sequence

  if (!Number.isSafeInteger(amount) || amount > MAX_BID_AMOUNT) {
    throw new Error(`backlog 입찰 금액이 API 범위를 벗어났다: ${amount}`)
  }

  submitBid(itemId, amount)
}

export function handleSummary(data) {
  return summaryTo(data)
}
