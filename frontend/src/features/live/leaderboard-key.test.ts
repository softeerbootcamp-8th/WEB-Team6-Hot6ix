import assert from 'node:assert/strict'
import test from 'node:test'

import { leaderboardEntryKey } from './leaderboard-key.ts'

test('동명이인은 bidderKey로 서로 다른 리더보드 행을 유지한다', () => {
  assert.notEqual(
    leaderboardEntryKey({ nickname: '민수', bidderKey: 'bidder-a' }),
    leaderboardEntryKey({ nickname: '민수', bidderKey: 'bidder-b' }),
  )
})

test('기존 응답처럼 bidderKey가 없어도 동명이인은 rank로 서로 다른 키를 쓴다', () => {
  assert.notEqual(
    leaderboardEntryKey({ nickname: '민수', rank: 1 }),
    leaderboardEntryKey({ nickname: '민수', rank: 2 }),
  )
})
