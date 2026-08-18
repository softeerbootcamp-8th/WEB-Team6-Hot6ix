import assert from 'node:assert/strict'
import test from 'node:test'

import { createLiveEventBuffer } from './live-event-buffer.ts'

type Event = { eventId?: number; name: string }

test('최초 연결과 재연결에서 snapshot 전 이벤트를 ID 순서로 모아 처리한다', () => {
  const delivered: string[] = []
  const buffer = createLiveEventBuffer<Event>((event) => {
    delivered.push(event.name)
  })

  buffer.receive({ eventId: 3, name: '세 번째' })
  buffer.receive({ eventId: 1, name: '첫 번째' })
  buffer.receive({ eventId: 2, name: '두 번째' })

  assert.deepEqual(delivered, [])
  buffer.drain()
  assert.deepEqual(delivered, ['첫 번째', '두 번째', '세 번째'])

  buffer.receive({ eventId: 4, name: '실시간' })
  assert.deepEqual(delivered, ['첫 번째', '두 번째', '세 번째', '실시간'])

  buffer.start()
  buffer.receive({ eventId: 6, name: '재연결 두 번째' })
  buffer.receive({ eventId: 5, name: '재연결 첫 번째' })

  assert.deepEqual(delivered, ['첫 번째', '두 번째', '세 번째', '실시간'])
  buffer.drain()
  assert.deepEqual(delivered, [
    '첫 번째',
    '두 번째',
    '세 번째',
    '실시간',
    '재연결 첫 번째',
    '재연결 두 번째',
  ])
})

test('snapshot 대기 중 terminal 이벤트가 오면 앞선 이벤트까지 즉시 처리한다', () => {
  const delivered: string[] = []
  const buffer = createLiveEventBuffer<Event>(
    (event) => delivered.push(event.name),
    (event) => event.name === '방 종료',
  )

  buffer.receive({ eventId: 2, name: '두 번째' })
  buffer.receive({ eventId: 1, name: '첫 번째' })
  buffer.receive({ eventId: 3, name: '방 종료' })

  assert.deepEqual(delivered, ['첫 번째', '두 번째', '방 종료'])
})
