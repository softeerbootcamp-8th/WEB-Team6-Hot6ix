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
  /**
   * 서버가 준 상품 사진 주소. 판매자가 올리지 않았으면 null 이다.
   *
   * 목업 물품(`mocks/data.ts`)에는 없다. 사진은 목업으로 채우지 않고
   * 서버 값만 쓰므로(`adapt-item.ts`) 값이 없으면 회색 아이콘이다.
   */
  imageUrl?: string | null
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
  /**
   * 서버 기준 마감 시각. 클라이언트 타이머는 표시용일 뿐이다.
   *
   * **시작 전(`READY`) 물품은 `null` 이다.** 마감 시각은 경매를 시작할 때 정해지므로
   * 그 전에는 존재하지 않는다. 예전에는 목업 시각으로 채웠는데, 아직 시작하지도 않은
   * 물품의 상세에서 카운트다운이 흐르는 문제가 있었다(이슈 #214).
   */
  endsAt: string | null
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
  /**
   * 남은 시간이 이 아래로 내려간 뒤 입찰이 들어오면 연장된다(초).
   *
   * 판매자가 마감을 앞당기면 이 값만큼 남은 시각으로 당겨지므로, 앞당기기 확인
   * 다이얼로그 문구와 버튼 잠금 판정도 이 값을 쓴다.
   */
  softCloseTriggerSeconds: number
  /** 방 기본 입찰 단위(원). 물품에 담길 때 복사되므로 물품 값이 우선이다. */
  bidUnit: number
  items: AuctionItemDetail[]
}

export interface RoomEvent {
  id: number
  at: string
  /**
   * `WIN` 은 낙찰 확정, `CLOSE` 는 마감 임박과 유찰이다.
   *
   * 예전에는 둘 다 `CLOSE` 로 두고 `subtitle`(낙찰가·낙찰자) 유무로 갈랐다.
   * 그러다 보니 낙찰만 문구가 두 줄이 되어 다른 이벤트와 결이 달랐다.
   */
  kind: 'BID' | 'START' | 'CLOSE' | 'WIN' | 'EXTEND' | 'REJECT'
  message: string
  /**
   * 어느 물품의 이벤트인지. 물품 상세가 자기 로그만 골라낼 때 쓴다.
   *
   * 방 종료처럼 물품 단위가 아닌 이벤트는 없다. 예전에는 물품명 문자열로
   * 대조했는데, 이름은 한 방에서 중복될 수 있고 부분일치까지 걸려서 로그가
   * 서로 섞이거나 아예 안 보였다.
   */
  itemId?: number
  /**
   * 어느 물품 이야기인지. 피드가 문구 위에 이름표로 올린다.
   *
   * 문구에 `{물품명} 경매가 시작됐어요` 처럼 섞어 쓰지 않는다. 종류마다 물품이
   * 문구 안에 있기도 없기도 하면(입찰은 `홍길동님이 8만원 입찰`) 물품이 여러
   * 개인 방에서 어떤 이벤트는 대상을 알 수 없다. 문구는 "무슨 일"만 적고
   * "어느 물품"은 항상 이 필드가 맡는다.
   *
   * 물품 하나만 다루는 화면(물품 상세)은 이름표를 그리지 않는다.
   */
  itemName?: string
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
