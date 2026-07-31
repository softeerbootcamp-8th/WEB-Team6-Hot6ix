import type {
  AuctionRoomDetail,
  AuctionRoomSummary,
  DealCandidate,
  Product,
  RoomEvent,
  TradeSummary,
} from '@/mocks/types'

/**
 * 화면 확인용 목업 데이터.
 *
 * 실제 값은 API 에서 온다. 마감 시각만 화면을 열 때 기준으로 계산해서
 * 카운트다운이 실제로 흐르게 해뒀다.
 */

const now = Date.now()

/** 지금부터 n초 뒤의 ISO 시각 */
function after(seconds: number) {
  return new Date(now + seconds * 1000).toISOString()
}

/** 지금부터 n분 전의 ISO 시각 */
function before(minutes: number) {
  return new Date(now - minutes * 60 * 1000).toISOString()
}

export const MOCK_ROOMS: AuctionRoomSummary[] = [
  {
    id: 1,
    title: '7월 셀러 라이브 경매방',
    sellerName: '민지 셀러',
    status: 'LIVE',
    role: 'BUYER',
    itemCount: 4,
    participantCount: 128,
    closedAt: null,
  },
  {
    id: 2,
    title: '포토카드 특가전',
    sellerName: '데님러버 셀러',
    status: 'LIVE',
    role: 'SELLER',
    itemCount: 3,
    participantCount: 64,
    closedAt: null,
  },
  {
    id: 3,
    title: '신규 입고 테스트',
    sellerName: '빈티지샵',
    status: 'CLOSED',
    role: 'SELLER',
    itemCount: 2,
    participantCount: 41,
    closedAt: '2026-07-18',
  },
  {
    id: 4,
    title: '스니커즈 위크',
    sellerName: '준호 셀러',
    status: 'CLOSED',
    role: 'BUYER',
    itemCount: 5,
    participantCount: 213,
    closedAt: '2026-06-20',
  },
  {
    id: 5,
    title: '한정판 굿즈 정리',
    sellerName: '민지 셀러',
    status: 'CLOSED',
    role: 'BUYER',
    itemCount: 3,
    participantCount: 88,
    closedAt: '2026-06-02',
  },
  {
    id: 6,
    title: '봄 시즌 오프',
    sellerName: '빈티지샵',
    status: 'CLOSED',
    role: 'SELLER',
    itemCount: 4,
    participantCount: 57,
    closedAt: '2026-05-11',
  },
]

export const MOCK_ROOM_DETAIL: AuctionRoomDetail = {
  id: 1,
  title: '7월 셀러 라이브 경매방',
  description: '한정판 스니커즈와 빈티지 의류를 정리합니다.',
  sellerName: '민지 셀러',
  status: 'LIVE',
  role: 'BUYER',
  participantCount: 128,
  shareCode: 'abc123',
  softCloseSeconds: 30,
  items: [
    {
      id: 1,
      roomId: 1,
      name: '한정판 조던 스니커즈',
      category: '스니커즈',
      description:
        '오프화이트와 레드 컬러 조합의 한정판 조던 스니커즈입니다. 보관 상태가 좋고 실착 횟수가 적어 전체적인 컨디션이 우수합니다. 구성품과 상세 상태는 상품 링크에서 확인할 수 있습니다.',
      productUrl: 'brand.com/limited-jordan',
      status: 'ACTIVE',
      startPrice: 50000,
      currentPrice: 85000,
      bidUnit: 1000,
      endsAt: after(730),
      bidCount: 12,
      topBidderNickname: '스니커홀릭',
      extended: false,
      leaderboard: [
        { rank: 1, nickname: '스니커홀릭', amount: 85000, isMe: true },
        { rank: 2, nickname: '조던매니아', amount: 82000, isMe: false },
        { rank: 3, nickname: '슈즈러버', amount: 80000, isMe: false },
      ],
      history: [
        { id: 3, nickname: '스니커홀릭', amount: 85000, bidAt: before(1) },
        { id: 2, nickname: '조던매니아', amount: 82000, bidAt: before(3) },
        { id: 1, nickname: '슈즈러버', amount: 80000, bidAt: before(6) },
      ],
    },
    {
      id: 2,
      roomId: 1,
      name: '빈티지 데님 자켓',
      category: '아우터',
      description:
        '90년대 리바이스 데님 자켓입니다. L 사이즈이고 워싱이 자연스럽게 빠져 있습니다. 소매 끝 마감과 단추 상태는 사진으로 확인해주세요.',
      productUrl: 'brand.com/vintage-denim',
      status: 'ACTIVE',
      startPrice: 8000,
      currentPrice: 13000,
      bidUnit: 1000,
      endsAt: after(28),
      bidCount: 7,
      topBidderNickname: '데님러버',
      extended: true,
      leaderboard: [
        { rank: 1, nickname: '데님러버', amount: 13000, isMe: false },
        { rank: 2, nickname: '빈티지홀릭', amount: 12000, isMe: false },
        { rank: 3, nickname: '청청패션', amount: 11000, isMe: true },
      ],
      history: [
        { id: 3, nickname: '데님러버', amount: 13000, bidAt: before(1) },
        { id: 2, nickname: '빈티지홀릭', amount: 12000, bidAt: before(2) },
        { id: 1, nickname: '청청패션', amount: 11000, bidAt: before(4) },
      ],
    },
    {
      id: 3,
      roomId: 1,
      name: '아이돌 포토카드 세트',
      category: '컬렉터블',
      description:
        '미개봉 상태의 포토카드 세트입니다. 슬리브에 넣어 보관했고 스크래치가 없습니다.',
      productUrl: 'brand.com/photocard-set',
      status: 'READY',
      startPrice: 5000,
      currentPrice: 5000,
      bidUnit: 500,
      endsAt: after(1800),
      bidCount: 0,
      topBidderNickname: null,
      extended: false,
      leaderboard: [],
      history: [],
    },
    {
      id: 4,
      roomId: 1,
      name: '리미티드 워치',
      category: '시계',
      description:
        '한정 수량으로 나온 오토매틱 워치입니다. 정품 보증서와 여분 스트랩이 함께 있습니다.',
      productUrl: 'brand.com/limited-watch',
      status: 'CLOSED',
      startPrice: 30000,
      currentPrice: 56000,
      bidUnit: 2000,
      endsAt: before(12),
      bidCount: 19,
      topBidderNickname: '시계덕후',
      extended: false,
      leaderboard: [
        { rank: 1, nickname: '시계덕후', amount: 56000, isMe: false },
        { rank: 2, nickname: '스니커홀릭', amount: 54000, isMe: true },
        { rank: 3, nickname: '빈티지홀릭', amount: 52000, isMe: false },
      ],
      history: [
        { id: 2, nickname: '시계덕후', amount: 56000, bidAt: before(13) },
        { id: 1, nickname: '스니커홀릭', amount: 54000, bidAt: before(15) },
      ],
    },
    {
      id: 5,
      roomId: 1,
      name: '레트로 피규어',
      category: '컬렉터블',
      description:
        '90년대 발매된 레트로 피규어입니다. 박스는 없고 본체만 있습니다.',
      productUrl: 'brand.com/retro-figure',
      status: 'CLOSED',
      startPrice: 10000,
      currentPrice: 24000,
      bidUnit: 1000,
      endsAt: before(25),
      bidCount: 11,
      topBidderNickname: '피규어왕',
      extended: false,
      leaderboard: [
        { rank: 1, nickname: '피규어왕', amount: 24000, isMe: false },
        { rank: 2, nickname: '청청패션', amount: 23000, isMe: false },
      ],
      history: [
        { id: 1, nickname: '피규어왕', amount: 24000, bidAt: before(26) },
      ],
    },
    {
      id: 6,
      roomId: 1,
      name: '핸드메이드 가죽지갑',
      category: '잡화',
      description:
        '베지터블 가죽으로 직접 만든 반지갑입니다. 사용할수록 색이 짙어집니다.',
      productUrl: 'brand.com/leather-wallet',
      status: 'CLOSED',
      startPrice: 20000,
      currentPrice: 45000,
      bidUnit: 1000,
      endsAt: before(33),
      bidCount: 15,
      topBidderNickname: '가죽공방',
      extended: false,
      leaderboard: [
        { rank: 1, nickname: '가죽공방', amount: 45000, isMe: false },
        { rank: 2, nickname: '데님러버', amount: 44000, isMe: false },
      ],
      history: [
        { id: 1, nickname: '가죽공방', amount: 45000, bidAt: before(34) },
      ],
    },
  ],
}

export const MOCK_ROOM_EVENTS: RoomEvent[] = [
  {
    id: 6,
    at: before(1),
    kind: 'BID',
    message: '스니커홀릭님이 85,000원 입찰',
    subtitle: '한정판 조던 스니커즈',
    emphasized: true,
  },
  {
    id: 5,
    at: before(2),
    kind: 'EXTEND',
    message: '마감 1분 전 입찰 발생 · 마감 +30초 자동 연장',
    subtitle: '빈티지 데님 자켓',
    emphasized: true,
  },
  {
    id: 4,
    at: before(3),
    kind: 'BID',
    message: '데님러버님이 13,000원 입찰',
    subtitle: '빈티지 데님 자켓',
  },
  {
    id: 3,
    at: before(4),
    kind: 'CLOSE',
    message: '빈티지 데님 자켓 마감 1분 전',
  },
  {
    id: 2,
    at: before(7),
    kind: 'CLOSE',
    message: '핸드메이드 가죽지갑 낙찰 확정',
    subtitle: '45,000원 · 가죽공방님',
    emphasized: true,
  },
  {
    id: 1,
    at: before(10),
    kind: 'START',
    message: '한정판 조던 스니커즈 경매가 시작됐어요',
  },
]

export const MOCK_PRODUCTS: Product[] = [
  {
    id: 1,
    name: '한정판 조던 스니커즈',
    category: '스니커즈',
    description: '275mm · 미착용 · 박스 포함',
    status: 'IN_AUCTION',
    createdAt: '2026-07-20',
  },
  {
    id: 2,
    name: '빈티지 데님 자켓',
    category: '아우터',
    description: 'L 사이즈 · 90년대 리바이스',
    status: 'IN_AUCTION',
    createdAt: '2026-07-19',
  },
  {
    id: 3,
    name: '희귀 포토카드 세트',
    category: '컬렉터블',
    description: '슬리브 포함 · 상태 A급',
    status: 'DRAFT',
    createdAt: '2026-07-15',
  },
  {
    id: 4,
    name: '레트로 필름 카메라',
    category: '카메라',
    description: '작동 확인 완료 · 케이스 포함',
    status: 'SOLD',
    createdAt: '2026-07-02',
  },
]

export const MOCK_TRADES: TradeSummary[] = [
  {
    id: 1,
    auctionItemId: 3,
    productName: '아이돌 포토카드 세트',
    category: '컬렉터블',
    roomTitle: '7월 셀러 라이브 경매방',
    role: 'BUYER',
    status: 'ACTION_NEEDED',
    amount: 22000,
    partnerNickname: '피규어덕후',
    partnerPhone: '010-2345-6789',
    closedAt: '2026-07-30',
  },
  {
    id: 2,
    auctionItemId: 2,
    productName: '빈티지 데님 재킷',
    category: '아우터',
    roomTitle: '스니커즈 위크',
    role: 'SELLER',
    status: 'COMPLETED',
    amount: 12000,
    partnerNickname: '스니커홀릭',
    partnerPhone: '010-1234-5678',
    closedAt: '2026-06-20',
  },
  {
    id: 3,
    auctionItemId: 5,
    productName: '레트로 피규어',
    category: '컬렉터블',
    roomTitle: '신규 입고 테스트',
    role: 'SELLER',
    status: 'UNSOLD',
    amount: 0,
    partnerNickname: '유효한 낙찰자 없음',
    partnerPhone: '-',
    closedAt: '2026-07-18',
  },
  {
    id: 4,
    auctionItemId: 1,
    productName: '스니커즈 한정판',
    category: '스니커즈',
    roomTitle: '7월 셀러 라이브 경매방',
    role: 'BUYER',
    status: 'IN_PROGRESS',
    amount: 13000,
    partnerNickname: '조던수집가',
    partnerPhone: '010-3456-7890',
    closedAt: '2026-07-29',
  },
]

export const MOCK_CANDIDATES: DealCandidate[] = [
  {
    id: 1,
    rank: 1,
    nickname: '포카수집가',
    phone: '010-4567-8901',
    amount: 46000,
    status: 'IN_PROGRESS',
  },
  {
    id: 2,
    rank: 2,
    nickname: '스니커홀릭',
    phone: '010-1234-5678',
    amount: 44000,
    status: 'WAITING',
  },
  {
    id: 3,
    rank: 3,
    nickname: '데님러버',
    phone: '010-5678-9012',
    amount: 42000,
    status: 'WAITING',
  },
  {
    id: 4,
    rank: 4,
    nickname: '빈티지킹',
    phone: '010-6789-0123',
    amount: 40000,
    status: 'WAITING',
  },
  {
    id: 5,
    rank: 5,
    nickname: '주말경매러',
    phone: '010-7890-1234',
    amount: 38000,
    status: 'WAITING',
  },
]
