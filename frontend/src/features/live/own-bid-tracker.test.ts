import assert from 'node:assert/strict'
import test from 'node:test'

import { createOwnBidTracker, trackOwnBidAttempt } from './own-bid-tracker.ts'

test('입찰 요청이 끝나기 전에도 본인 입찰로 판정한다', async () => {
  const tracker = createOwnBidTracker()
  let acceptRequest: (() => void) | undefined
  const request = new Promise<void>((resolve) => {
    acceptRequest = resolve
  })

  const attempt = trackOwnBidAttempt(tracker, 10, 20_000, () => request)

  assert.equal(tracker.has(10, 20_000), true)

  acceptRequest?.()
  await attempt
  assert.equal(tracker.has(10, 20_000), true)
})

test('입찰 요청이 실패하면 본인 입찰 후보를 제거한다', async () => {
  const tracker = createOwnBidTracker()

  await assert.rejects(
    trackOwnBidAttempt(tracker, 10, 20_000, async () => {
      throw new Error('입찰 거절')
    }),
  )

  assert.equal(tracker.has(10, 20_000), false)
})

test('물품과 금액이 다른 입찰은 서로 구분한다', async () => {
  const tracker = createOwnBidTracker()

  await trackOwnBidAttempt(tracker, 10, 20_000, async () => undefined)

  assert.equal(tracker.has(10, 20_000), true)
  assert.equal(tracker.has(10, 21_000), false)
  assert.equal(tracker.has(11, 20_000), false)
})
