import assert from 'node:assert/strict'
import test from 'node:test'

import { eventIdFromLastEventId } from './sse-event-id.ts'

test('ID 없는 참여자 수 이벤트는 브라우저가 보존한 이전 event ID를 쓰지 않는다', () => {
  assert.equal(eventIdFromLastEventId('ParticipantCount', '42'), undefined)
  assert.equal(eventIdFromLastEventId('BidPlaced', '42'), 42)
})
