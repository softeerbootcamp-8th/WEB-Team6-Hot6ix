import assert from 'node:assert/strict'
import test from 'node:test'

import { createBidRequestIdTracker } from './bid-request-id.ts'

const sequentialIds = () => {
  let sequence = 0
  return () => `request-${++sequence}`
}

test('동일한 물품과 금액을 재시도하면 같은 요청 ID를 반환한다', () => {
  const tracker = createBidRequestIdTracker(sequentialIds())

  assert.equal(tracker.acquire(10, 20_000), 'request-1')
  assert.equal(tracker.acquire(10, 20_000), 'request-1')
})

test('물품이나 금액이 달라지면 새로운 요청 ID를 반환한다', () => {
  const tracker = createBidRequestIdTracker(sequentialIds())

  assert.equal(tracker.acquire(10, 20_000), 'request-1')
  assert.equal(tracker.acquire(10, 21_000), 'request-2')
  assert.equal(tracker.acquire(11, 20_000), 'request-3')
})

test('성공한 요청을 완료하면 같은 입찰 의도에도 새로운 요청 ID를 반환한다', () => {
  const tracker = createBidRequestIdTracker(sequentialIds())
  const completedRequestId = tracker.acquire(10, 20_000)

  tracker.complete(completedRequestId)

  assert.equal(tracker.acquire(10, 20_000), 'request-2')
})

test('다른 요청의 완료 신호는 현재 재시도 ID를 제거하지 않는다', () => {
  const tracker = createBidRequestIdTracker(sequentialIds())

  tracker.acquire(10, 20_000)
  tracker.complete('unknown-request')

  assert.equal(tracker.acquire(10, 20_000), 'request-1')
})
