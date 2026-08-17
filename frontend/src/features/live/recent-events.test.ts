import assert from 'node:assert/strict'
import test from 'node:test'

import { mergeRecentRoomEvents } from './merge-recent-events.ts'
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
