import assert from 'node:assert/strict'
import test from 'node:test'

import {
  createRoomEventIdTracker,
  mergeRecentRoomEvents,
  shouldProcessRoomEvent,
} from './merge-recent-events.ts'
import type { RoomEvent } from '@/types/domain'

const event = (id: number, message: string): RoomEvent => ({
  id,
  at: '2026-08-17T23:00:00Z',
  kind: 'BID',
  message,
})

test('최근 이벤트 API와 SSE가 같은 서버 event ID를 주면 피드에 한 번만 남긴다', () => {
  const result = mergeRecentRoomEvents(
    [event(41, '이전 사건'), event(42, '같은 사건')],
    [event(42, '같은 사건'), event(43, '새 사건')],
  )

  assert.deepEqual(
    result.map((entry) => entry.id),
    [41, 42, 43],
  )
})

test('REST로 본 event ID는 나중에 SSE로 도착해도 다시 처리하지 않는다', () => {
  const tracker = createRoomEventIdTracker()

  tracker.remember([42])

  assert.equal(tracker.accept(42), false)
  assert.equal(tracker.accept(43), true)
  assert.equal(tracker.accept(43), false)
  assert.equal(tracker.accept(undefined), true)
})

test('REST로 본 방 종료 이벤트도 SSE 제어 처리는 수행한다', () => {
  const tracker = createRoomEventIdTracker()
  tracker.remember([42])

  const isNewEvent = tracker.accept(42)

  assert.equal(isNewEvent, false)
  assert.equal(shouldProcessRoomEvent('RoomClosed', isNewEvent), true)
  assert.equal(shouldProcessRoomEvent('BidPlaced', isNewEvent), false)
})
