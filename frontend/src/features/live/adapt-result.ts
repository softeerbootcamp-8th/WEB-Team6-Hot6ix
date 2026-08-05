import type {
  AuctionItemResultResponseDto,
  AuctionRoomResultResponseDto,
} from '@/api/generated/model'

/**
 * `GET /auction-rooms/share/{shareCode}/results` 응답을 화면(`rooms.$shareCode.result.tsx`,
 * `ClosedRoomView`)이 바로 쓸 모양으로 좁힌다. 두 화면이 같은 응답을 쓰므로
 * 변환·집계를 여기 한 곳에 모은다.
 *
 * DTO 필드가 전부 optional 인 건 springdoc 이 `required` 를 안 내려서다
 * (`adapt-deal.ts` 와 같은 사정). 카드를 그리는 데 반드시 필요한 값(물품 ID·상태)이
 * 없는 줄은 버린다 — 배지·집계가 조용히 틀리는 것보다 줄이 빠지는 게 낫다.
 */

/**
 * 물품 하나의 결말. `/results` 는 종료된 방 전용이 아니라 진행 중인 물품도
 * 섞여 올 수 있어(`READY`/`IN_PROGRESS`), 낙찰·유찰과 나란히 `PENDING` 을 둔다.
 * "유찰 = 전체 − 낙찰" 로 세면 진행 중인 물품까지 유찰로 잡힌다.
 */
export type ItemOutcome = 'SOLD' | 'FAILED' | 'PENDING'

export interface ItemResult {
  auctionItemId: number
  productName: string
  imageUrl: string | null
  outcome: ItemOutcome
  /** 낙찰이 아니면 없다. */
  finalPrice: number | null
  /** 낙찰이 아니면 없다. */
  winnerNickname: string | null
  /** 후보에 없거나 비로그인이면 없다. */
  myRank: number | null
  myAmount: number | null
  /** 물품이 팔렸고 내가 1순위 = 내가 낙찰받았다. */
  isMyWin: boolean
}

export interface RoomResult {
  auctionRoomId: number
  name: string
  sellerStoreName: string | null
  status: 'READY' | 'LIVE' | 'CLOSED'
  /** 방 종료 전이가 서버에 없어 당장은 항상 null 이다. */
  closedAt: string | null
  items: ItemResult[]
  soldCount: number
  /** `FAILED` 만 센다. 진행 중인 물품은 유찰이 아니다. */
  unsoldCount: number
  totalAmount: number
  myWinCount: number
}

/** 서버 방 상태를 화면 어휘로 흡수한다. `toItemStatus`(`adapt-item.ts`)와 같은 방식. */
function toRoomStatus(status: string | undefined): RoomResult['status'] {
  switch (status) {
    case 'OPEN':
      return 'LIVE'
    case 'CLOSED':
      return 'CLOSED'
    default:
      return 'READY'
  }
}

function toOutcome(status: string | undefined): ItemOutcome {
  switch (status) {
    case 'SOLD':
      return 'SOLD'
    case 'FAILED':
      return 'FAILED'
    default:
      return 'PENDING'
  }
}

function toItemResult(dto: AuctionItemResultResponseDto): ItemResult | null {
  if (dto.auctionItemId == null || dto.status == null) return null

  const outcome = toOutcome(dto.status)

  return {
    auctionItemId: dto.auctionItemId,
    productName: dto.productName ?? '이름 없는 물품',
    imageUrl: dto.imageUrl ?? null,
    outcome,
    finalPrice: outcome === 'SOLD' ? (dto.finalPrice ?? null) : null,
    winnerNickname: outcome === 'SOLD' ? (dto.winnerNickname ?? null) : null,
    myRank: dto.myRank ?? null,
    myAmount: dto.myAmount ?? null,
    isMyWin: outcome === 'SOLD' && dto.myRank === 1,
  }
}

export function toRoomResult(
  dto: AuctionRoomResultResponseDto | undefined,
): RoomResult | null {
  if (!dto || dto.auctionRoomId == null) return null

  const items = (dto.items ?? []).flatMap((item) => {
    const result = toItemResult(item)
    return result ? [result] : []
  })

  return {
    auctionRoomId: dto.auctionRoomId,
    name: dto.name ?? '이름 없는 경매방',
    sellerStoreName: dto.sellerStoreName ?? null,
    status: toRoomStatus(dto.status),
    closedAt: dto.closedAt ?? null,
    items,
    soldCount: items.filter((item) => item.outcome === 'SOLD').length,
    unsoldCount: items.filter((item) => item.outcome === 'FAILED').length,
    totalAmount: items.reduce((sum, item) => sum + (item.finalPrice ?? 0), 0),
    myWinCount: items.filter((item) => item.isMyWin).length,
  }
}
