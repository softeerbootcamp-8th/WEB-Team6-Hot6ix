/**
 * 화면 구현용 도메인 타입.
 *
 * API 연동 전까지 쓰는 임시 타입이다. Orval 이 생성한 타입이 들어오면
 * 이 파일은 지우고 `@/api/generated/model` 을 쓴다.
 */

export type RoomStatus = 'LIVE' | 'READY' | 'CLOSED'
export type ItemStatus = 'READY' | 'ACTIVE' | 'CLOSED'
/** 그 방에서 내 역할. 방을 만든 사람이면 판매자다. */
export type RoomRole = 'BUYER' | 'SELLER'

export interface AuctionRoomSummary {
  id: number
  title: string
  sellerName: string
  status: RoomStatus
  role: RoomRole
  itemCount: number
  participantCount: number
  /** 종료된 방만 값이 있다. */
  closedAt: string | null
}

export interface LeaderboardEntry {
  rank: number
  nickname: string
  amount: number
  /** 나인지 여부. 서버가 내려주는 값을 그대로 쓴다. */
  isMe: boolean
}

export interface BidHistoryEntry {
  id: number
  nickname: string
  amount: number
  bidAt: string
  /** 규칙 미달로 거절된 입찰도 이력에 남는다. */
  rejected?: boolean
}

export interface AuctionItemDetail {
  id: number
  roomId: number
  name: string
  category: string
  /** 판매자가 작성한 상품 설명 */
  description: string
  /** 외부 상품 링크. 없을 수 있다. */
  productUrl: string | null
  status: ItemStatus
  /**
   * 낙찰로 끝났는지. `status` 가 `CLOSED` 여도 입찰이 없었으면 유찰이라 false 다.
   *
   * 화면 상태 3가지로는 낙찰과 유찰을 구분할 수 없어서 따로 둔다. 이게 없으면
   * "닫혔으면 곧 낙찰"로 단정하게 되어 유찰 물품에도 낙찰가가 붙는다.
   */
  sold: boolean
  startPrice: number
  currentPrice: number
  bidUnit: number
  /** 서버 기준 마감 시각. 클라이언트 타이머는 표시용일 뿐이다. */
  endsAt: string
  bidCount: number
  topBidderNickname: string | null
  leaderboard: LeaderboardEntry[]
  history: BidHistoryEntry[]
  /** Soft Close 로 연장된 적이 있으면 true */
  extended: boolean
}

export interface AuctionRoomDetail {
  id: number
  title: string
  description: string
  sellerName: string
  status: RoomStatus
  role: RoomRole
  participantCount: number
  shareCode: string
  /** 마감 직전 입찰 시 연장되는 시간(초) */
  softCloseSeconds: number
  items: AuctionItemDetail[]
}

export interface RoomEvent {
  id: number
  at: string
  kind: 'BID' | 'START' | 'CLOSE' | 'EXTEND' | 'REJECT'
  message: string
  /** 물품명이나 금액·닉네임 같은 보조 설명 */
  subtitle?: string
  /** 마감 임박·연장처럼 눈에 띄어야 하는 이벤트 */
  emphasized?: boolean
}

/**
 * 상품은 한 번의 경매에만 쓰인다. 그래서 상태가 곧 경매 결과다.
 * `DRAFT` 미진행 → `IN_AUCTION` 경매 중 → `SOLD` 낙찰 / `UNSOLD` 유찰.
 */
export type ProductStatus = 'DRAFT' | 'IN_AUCTION' | 'SOLD' | 'UNSOLD'

export interface Product {
  id: number
  name: string
  category: string
  description: string
  /** Figma 상품 등록의 "참고 링크". 없을 수 있다. */
  productUrl?: string
  status: ProductStatus
  createdAt: string
}

/** 거래 내역이 쓰는 3가지 상태 */
export type TradeStatus = 'IN_PROGRESS' | 'COMPLETED' | 'UNSOLD'

export interface TradeSummary {
  id: number
  auctionItemId: number
  /**
   * 이 거래가 나온 상품. 판매자 상품 상세에서 거래로 건너뛸 때 쓴다.
   * 내가 산 물건(구매자 거래)은 내 상품이 아니라서 값이 없다.
   */
  productId?: number
  /** 이 거래가 나온 경매방. 거래 상세에서 방 전체 결과로 넘어갈 때 쓴다. */
  roomId: number
  productName: string
  category: string
  roomTitle: string
  role: RoomRole
  status: TradeStatus
  amount: number
  partnerNickname: string
  partnerPhone: string
  closedAt: string
}
