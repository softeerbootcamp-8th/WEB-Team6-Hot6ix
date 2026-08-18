import type {
  AuctionLiveStateResponseDto,
  BidCreateResponseDto,
} from '@/api/generated/model'
import type { AuctionItemDetail, LeaderboardEntry } from '@/types/domain'

type RevisionedItemEvent =
  | {
      kind: 'ItemStarted'
      itemId: number
      endedTime: string
      revision?: number
    }
  | {
      kind: 'BidPlaced'
      itemId: number
      bidPrice: number
      bidderNickname: string
      bidderKey?: string | null
      endedTime?: string
      revision?: number
      isMe?: boolean
    }
  | {
      kind: 'SoftCloseExtended' | 'ItemCloseAdvanced'
      itemId: number
      endedTime: string
      revision?: number
    }
  | {
      kind: 'ItemEnded'
      itemId: number
      finalPrice: number | null
      winnerNickname: string | null
      winnerBidderKey?: string | null
      revision?: number
    }

function revisionOf(value: number | undefined): number {
  return Number.isSafeInteger(value) && (value ?? 0) >= 0 ? (value ?? 0) : 0
}

function canApply(current: AuctionItemDetail, incoming: number): boolean {
  if (incoming === 0) return current.revision === 0
  return incoming >= current.revision
}

function toLeaderboard(
  entries: AuctionLiveStateResponseDto['leaderboard'],
): LeaderboardEntry[] {
  return (entries ?? [])
    .filter(
      (entry): entry is typeof entry & { nickname: string } =>
        entry.nickname !== undefined,
    )
    .map((entry, index) => ({
      rank: entry.rank ?? index + 1,
      bidderKey: entry.bidderKey ?? null,
      nickname: entry.nickname,
      amount: entry.amount ?? 0,
      isMe: entry.isMe ?? false,
    }))
}

/** Redis Snapshot은 같은 revision도 전체 상태를 다시 맞추는 복구 값으로 적용한다. */
export function applyLiveSnapshots(
  items: AuctionItemDetail[],
  snapshots: AuctionLiveStateResponseDto[],
): AuctionItemDetail[] {
  const byItemId = new Map(
    snapshots
      .filter((snapshot) => snapshot.auctionItemId !== undefined)
      .map((snapshot) => [snapshot.auctionItemId as number, snapshot]),
  )

  return items.map((item) => {
    const snapshot = byItemId.get(item.id)
    if (!snapshot) return item

    const revision = revisionOf(snapshot.revision)
    if (!canApply(item, revision)) return item

    const leaderboard = toLeaderboard(snapshot.leaderboard)
    return {
      ...item,
      status: snapshot.status === 'CLOSING' ? 'CLOSED' : 'ACTIVE',
      sold: snapshot.status === 'CLOSING' && leaderboard.length > 0,
      currentPrice: snapshot.currentPrice ?? item.currentPrice,
      endsAt: snapshot.endAt ?? item.endsAt,
      topBidderNickname: leaderboard[0]?.nickname ?? null,
      leaderboard,
      revision,
    }
  })
}

/** SSE는 낮은 revision만 버리고, 같은 revision의 입찰·연장 묶음은 모두 적용한다. */
export function applyLiveEvent(
  items: AuctionItemDetail[],
  event: RevisionedItemEvent,
): AuctionItemDetail[] {
  return items.map((item) => {
    if (item.id !== event.itemId) return item

    const revision = revisionOf(event.revision)
    if (!canApply(item, revision)) return item
    const nextRevision = Math.max(item.revision, revision)

    switch (event.kind) {
      case 'ItemStarted':
        return {
          ...item,
          status: 'ACTIVE',
          sold: false,
          endsAt: event.endedTime,
          revision: nextRevision,
        }

      case 'BidPlaced': {
        const bidderKey = event.bidderKey ?? null
        const previous = item.leaderboard.find((entry) =>
          bidderKey
            ? entry.bidderKey === bidderKey
            : entry.nickname === event.bidderNickname,
        )
        const leaderboard = [
          {
            rank: 1,
            bidderKey,
            nickname: event.bidderNickname,
            amount: event.bidPrice,
            isMe: event.isMe ?? previous?.isMe ?? false,
          },
          ...item.leaderboard.filter((entry) =>
            bidderKey
              ? entry.bidderKey !== bidderKey
              : entry.nickname !== event.bidderNickname,
          ),
        ]
          .slice(0, 3)
          .map((entry, index) => ({ ...entry, rank: index + 1 }))

        return {
          ...item,
          currentPrice: event.bidPrice,
          endsAt: event.endedTime ?? item.endsAt,
          topBidderNickname: event.bidderNickname,
          leaderboard,
          revision: nextRevision,
        }
      }

      case 'SoftCloseExtended':
      case 'ItemCloseAdvanced':
        return {
          ...item,
          endsAt: event.endedTime,
          revision: nextRevision,
        }

      case 'ItemEnded':
        return {
          ...item,
          status: 'CLOSED',
          sold: event.winnerNickname !== null,
          currentPrice: event.finalPrice ?? item.currentPrice,
          topBidderNickname: event.winnerNickname,
          revision: nextRevision,
        }
    }
  })
}

/** 입찰 승인 응답은 SSE가 유실돼도 본인 화면을 즉시 Redis 확정값으로 맞춘다. */
export function applyAcceptedBid(
  items: AuctionItemDetail[],
  response: BidCreateResponseDto,
  bidderNickname: string,
): AuctionItemDetail[] {
  if (
    response.auctionItemId === undefined ||
    response.amount === undefined ||
    response.revision === undefined
  ) {
    return items
  }

  const afterBid = applyLiveEvent(items, {
    kind: 'BidPlaced',
    itemId: response.auctionItemId,
    bidPrice: response.amount,
    bidderNickname,
    bidderKey: response.bidderKey,
    revision: response.revision,
    isMe: true,
  })

  if (!response.endAt) return afterBid
  return applyLiveEvent(afterBid, {
    kind: 'SoftCloseExtended',
    itemId: response.auctionItemId,
    endedTime: response.endAt,
    revision: response.revision,
  })
}

/**
 * 목록 재조회는 편성과 정적 필드를 갱신한다. revision이 있는 진행 상태는 Redis가
 * 원본이므로 MySQL의 과거 현재가·마감 시각·리더보드로 되돌리지 않는다.
 */
export function reconcileServerItems(
  currentItems: AuctionItemDetail[],
  serverItems: AuctionItemDetail[],
): AuctionItemDetail[] {
  const currentById = new Map(currentItems.map((item) => [item.id, item]))

  return serverItems.map((serverItem) => {
    const current = currentById.get(serverItem.id)
    if (!current || current.revision === 0 || serverItem.status === 'CLOSED') {
      return serverItem
    }

    return {
      ...serverItem,
      status: current.status,
      sold: current.sold,
      currentPrice: current.currentPrice,
      endsAt: current.endsAt,
      topBidderNickname: current.topBidderNickname,
      leaderboard: current.leaderboard,
      revision: current.revision,
    }
  })
}
