import assert from 'node:assert/strict'
import test from 'node:test'

import {
  applyAcceptedBid,
  applyLiveEvent,
  applyLiveSnapshots,
  reconcileServerItems,
} from './live-state.ts'
import type { AuctionItemDetail } from '@/types/domain'

function item(
  id: number,
  overrides: Partial<AuctionItemDetail> = {},
): AuctionItemDetail {
  return {
    id,
    roomId: 1,
    name: `물품 ${id}`,
    imageUrl: null,
    description: '',
    productUrl: null,
    status: 'ACTIVE',
    sold: false,
    startPrice: 10_000,
    currentPrice: 10_000,
    bidUnit: 1_000,
    endsAt: '2026-08-17T23:00:00',
    topBidderNickname: null,
    leaderboard: [],
    revision: 0,
    ...overrides,
  }
}

test('Snapshot은 MySQL 목록보다 최신인 Redis 상태 전체를 적용한다', () => {
  const result = applyLiveSnapshots(
    [item(10)],
    [
      {
        auctionItemId: 10,
        status: 'CLOSING',
        currentPrice: 30_000,
        endAt: '2026-08-17T22:30:00',
        revision: 7,
        leaderboard: [
          {
            rank: 1,
            bidderKey: 'bidder-a',
            nickname: '민수',
            amount: 30_000,
            isMe: true,
          },
          {
            rank: 2,
            bidderKey: 'bidder-b',
            nickname: '민수',
            amount: 20_000,
            isMe: false,
          },
        ],
      },
    ],
  )

  assert.equal(result[0]?.status, 'CLOSED')
  assert.equal(result[0]?.sold, true)
  assert.equal(result[0]?.currentPrice, 30_000)
  assert.equal(result[0]?.endsAt, '2026-08-17T22:30:00')
  assert.equal(result[0]?.revision, 7)
  assert.deepEqual(
    result[0]?.leaderboard.map((entry) => entry.bidderKey),
    ['bidder-a', 'bidder-b'],
  )
})

test('낮은 revision의 Snapshot과 SSE는 최신 화면을 되돌리지 않는다', () => {
  const latest = item(10, { currentPrice: 30_000, revision: 7 })

  const afterSnapshot = applyLiveSnapshots(
    [latest],
    [
      {
        auctionItemId: 10,
        status: 'IN_PROGRESS',
        currentPrice: 20_000,
        endAt: '2026-08-17T22:00:00',
        revision: 6,
        leaderboard: [],
      },
    ],
  )
  const afterEvent = applyLiveEvent(afterSnapshot, {
    kind: 'BidPlaced',
    itemId: 10,
    bidPrice: 25_000,
    bidderNickname: '과거 입찰자',
    bidderKey: 'old-bidder',
    revision: 6,
  })

  assert.equal(afterEvent[0]?.currentPrice, 30_000)
  assert.equal(afterEvent[0]?.revision, 7)
})

test('MySQL CLOSED 확정 뒤에는 같은 revision의 늦은 Snapshot도 무시한다', () => {
  const closed = item(10, {
    status: 'CLOSED',
    sold: true,
    currentPrice: 30_000,
    revision: 7,
  })

  const result = applyLiveSnapshots(
    [closed],
    [
      {
        auctionItemId: 10,
        status: 'IN_PROGRESS',
        currentPrice: 20_000,
        endAt: '2026-08-17T22:00:00',
        revision: 7,
        leaderboard: [],
      },
    ],
  )

  assert.equal(result[0]?.status, 'CLOSED')
  assert.equal(result[0]?.sold, true)
  assert.equal(result[0]?.currentPrice, 30_000)
})

test('같은 revision의 입찰과 연장 이벤트는 모두 적용하고 동명이인은 bidderKey로 구분한다', () => {
  const initial = item(10, {
    revision: 6,
    leaderboard: [
      {
        rank: 1,
        bidderKey: 'bidder-a',
        nickname: '민수',
        amount: 20_000,
        isMe: false,
      },
      {
        rank: 2,
        bidderKey: 'bidder-b',
        nickname: '민수',
        amount: 19_000,
        isMe: true,
      },
    ],
  })

  const afterBid = applyLiveEvent([initial], {
    kind: 'BidPlaced',
    itemId: 10,
    bidPrice: 30_000,
    bidderNickname: '민수',
    bidderKey: 'bidder-b',
    revision: 7,
  })
  const afterExtension = applyLiveEvent(afterBid, {
    kind: 'SoftCloseExtended',
    itemId: 10,
    endedTime: '2026-08-17T23:05:00',
    revision: 7,
  })

  assert.deepEqual(
    afterExtension[0]?.leaderboard.map((entry) => entry.bidderKey),
    ['bidder-b', 'bidder-a'],
  )
  assert.equal(afterExtension[0]?.leaderboard[0]?.isMe, true)
  assert.equal(afterExtension[0]?.endsAt, '2026-08-17T23:05:00')
  assert.equal(afterExtension[0]?.revision, 7)
})

test('연장 이벤트보다 다음 revision 입찰이 먼저 도착해도 마감 시각을 복구한다', () => {
  const nextBid = {
    kind: 'BidPlaced' as const,
    itemId: 10,
    bidPrice: 31_000,
    bidderNickname: '영희',
    bidderKey: 'bidder-c',
    endedTime: '2026-08-17T23:05:00',
    revision: 8,
  }

  const afterBid = applyLiveEvent(
    [item(10, { endsAt: '2026-08-17T23:00:00', revision: 6 })],
    nextBid,
  )
  const afterDelayedExtension = applyLiveEvent(afterBid, {
    kind: 'SoftCloseExtended',
    itemId: 10,
    endedTime: '2026-08-17T23:05:00',
    revision: 7,
  })

  assert.equal(afterDelayedExtension[0]?.currentPrice, 31_000)
  assert.equal(afterDelayedExtension[0]?.endsAt, '2026-08-17T23:05:00')
  assert.equal(afterDelayedExtension[0]?.revision, 8)
})

test('MySQL 재조회는 정적 필드만 갱신하고 revision이 있는 라이브 필드는 보존한다', () => {
  const current = item(10, {
    name: '이전 이름',
    currentPrice: 30_000,
    endsAt: '2026-08-17T23:05:00',
    revision: 7,
  })
  const staleServer = item(10, {
    name: '수정된 이름',
    currentPrice: 20_000,
    endsAt: '2026-08-17T23:00:00',
  })

  const result = reconcileServerItems([current], [staleServer, item(11)])

  assert.equal(result[0]?.name, '수정된 이름')
  assert.equal(result[0]?.currentPrice, 30_000)
  assert.equal(result[0]?.endsAt, '2026-08-17T23:05:00')
  assert.equal(result[0]?.revision, 7)
  assert.equal(result[1]?.id, 11)
})

test('MySQL CLOSED 확정 뒤에는 낮은 revision SSE가 최종 상태를 되돌리지 않는다', () => {
  const current = item(10, {
    currentPrice: 30_000,
    endsAt: '2026-08-17T23:05:00',
    revision: 8,
    leaderboard: [
      {
        rank: 1,
        bidderKey: 'bidder-current',
        nickname: '현재 낙찰자',
        amount: 30_000,
        isMe: false,
      },
    ],
  })
  const persisted = item(10, {
    status: 'CLOSED',
    sold: true,
    currentPrice: 30_000,
    endsAt: '2026-08-17T23:05:00',
  })

  const closed = reconcileServerItems([current], [persisted])
  const afterStaleBid = applyLiveEvent(closed, {
    kind: 'BidPlaced',
    itemId: 10,
    bidPrice: 20_000,
    bidderNickname: '과거 입찰자',
    bidderKey: 'bidder-old',
    endedTime: '2026-08-17T23:00:00',
    revision: 7,
  })

  assert.equal(afterStaleBid[0]?.status, 'CLOSED')
  assert.equal(afterStaleBid[0]?.currentPrice, 30_000)
  assert.equal(afterStaleBid[0]?.endsAt, '2026-08-17T23:05:00')
  assert.equal(afterStaleBid[0]?.revision, 8)
  assert.equal(afterStaleBid[0]?.leaderboard[0]?.bidderKey, 'bidder-current')
})

test('본인 입찰 HTTP 응답만 도착해도 SSE 없이 현재가와 타이머를 확정값으로 갱신한다', () => {
  const result = applyAcceptedBid(
    [item(10)],
    {
      auctionItemId: 10,
      amount: 30_000,
      endAt: '2026-08-17T23:05:00',
      extendedSeconds: 30,
      revision: 7,
      bidderKey: 'my-bidder-key',
    },
    '나',
  )

  assert.equal(result[0]?.currentPrice, 30_000)
  assert.equal(result[0]?.endsAt, '2026-08-17T23:05:00')
  assert.equal(result[0]?.revision, 7)
  assert.equal(result[0]?.leaderboard[0]?.bidderKey, 'my-bidder-key')
  assert.equal(result[0]?.leaderboard[0]?.isMe, true)
})
