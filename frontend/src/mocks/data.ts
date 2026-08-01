import type {
  AuctionItemDetail,
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
    id: 7,
    title: '오픈 준비 중인 방',
    sellerName: '민지 셀러',
    status: 'READY',
    role: 'SELLER',
    itemCount: 0,
    participantCount: 0,
    closedAt: null,
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

/**
 * 아무것도 없는 경매방.
 *
 * 물품도 이벤트도 참여자도 0인 상태를 확인하려고 둔다. `/rooms/7` 로 들어간다.
 * 방을 막 만들고 아직 아무 일도 일어나지 않은 시점이다.
 */
export const MOCK_EMPTY_ROOM: AuctionRoomDetail = {
  id: 7,
  title: '오픈 준비 중인 방',
  description: '아직 물품을 편성하지 않았어요.',
  sellerName: '민지 셀러',
  status: 'READY',
  role: 'SELLER',
  participantCount: 0,
  shareCode: 'ready7',
  softCloseSeconds: 30,
  items: [],
}

/**
 * 방 테마별 물품 이름.
 *
 * 상세 목업은 한 벌뿐이라 어느 방을 열어도 같은 물품이 나왔다. 방 제목과
 * 물품이 따로 놀지 않도록 이름·분류·설명만 테마에 맞게 갈아 끼운다.
 * (금액·순위 같은 수치는 그대로 두므로 화면 검증에는 영향이 없다.)
 */
const ROOM_ITEM_THEMES: Record<
  number,
  { name: string; category: string; description: string }[]
> = {
  // 포토카드 특가전
  2: [
    {
      name: '아이돌 포토카드 세트',
      category: '컬렉터블',
      description: '슬리브 포함 · 상태 A급',
    },
    {
      name: '미니앨범 한정 포토카드',
      category: '컬렉터블',
      description: '초동 특전 · 미개봉',
    },
    {
      name: '팬미팅 포토카드',
      category: '컬렉터블',
      description: '현장 수령 · 1인 1매 한정',
    },
    {
      name: '시즌그리팅 포토카드',
      category: '컬렉터블',
      description: '2026 시즌 · 풀세트',
    },
    {
      name: '홀로그램 포토카드',
      category: '컬렉터블',
      description: '럭키드로우 당첨분',
    },
    {
      name: '포토카드 바인더',
      category: '컬렉터블',
      description: '30포켓 · 미사용',
    },
  ],
  // 신규 입고 테스트 — 전자제품
  3: [
    {
      name: '무선 이어폰',
      category: '전자제품',
      description: '충전 케이스 포함 · 배터리 정상',
    },
    {
      name: '노이즈캔슬링 헤드폰',
      category: '전자제품',
      description: '패드 교체 완료 · 잡음 없음',
    },
    {
      name: '기계식 키보드',
      category: '전자제품',
      description: '적축 · 키캡 세트 포함',
    },
    {
      name: '스마트워치',
      category: '전자제품',
      description: '스트랩 2종 · 액정 무기스',
    },
    {
      name: '무선 충전기',
      category: '전자제품',
      description: '15W · 정품 어댑터 포함',
    },
    {
      name: '스마트폰',
      category: '전자제품',
      description: '배터리 성능 92% · 케이스 증정',
    },
  ],
  // 스니커즈 위크
  4: [
    {
      name: '조던 1 레트로 하이',
      category: '스니커즈',
      description: '270mm · 박스 포함',
    },
    {
      name: '에어포스 1 로우',
      category: '스니커즈',
      description: '260mm · 미착용',
    },
    {
      name: '덩크 로우 팬더',
      category: '스니커즈',
      description: '275mm · 2회 착용',
    },
    {
      name: '뉴발란스 992',
      category: '스니커즈',
      description: '265mm · 정품 확인서',
    },
    {
      name: '컨버스 척테일러 70',
      category: '스니커즈',
      description: '280mm · 박스 없음',
    },
    {
      name: '러닝화 한정판',
      category: '스니커즈',
      description: '270mm · 미착용',
    },
  ],
  // 한정판 굿즈 정리
  5: [
    {
      name: '레트로 피규어',
      category: '컬렉터블',
      description: '미개봉 · 초판',
    },
    {
      name: '한정판 아트토이',
      category: '컬렉터블',
      description: '넘버링 · 박스 포함',
    },
    {
      name: '캐릭터 피규어 세트',
      category: '컬렉터블',
      description: '5종 풀세트',
    },
    {
      name: '한정판 키링',
      category: '잡화',
      description: '미사용 · 태그 부착',
    },
    {
      name: '아크릴 스탠드',
      category: '컬렉터블',
      description: '특전 · 미개봉',
    },
    {
      name: '굿즈 포스터 세트',
      category: '컬렉터블',
      description: '튜브 포장 · 접힘 없음',
    },
  ],
  // 봄 시즌 오프 — 패션
  6: [
    {
      name: '빈티지 데님 자켓',
      category: '아우터',
      description: 'L · 90년대 리바이스',
    },
    {
      name: '트렌치 코트',
      category: '아우터',
      description: '95 사이즈 · 드라이 완료',
    },
    {
      name: '니트 가디건',
      category: '상의',
      description: '오버핏 · 보풀 없음',
    },
    { name: '린넨 셔츠', category: '상의', description: 'M · 여름용' },
    { name: '가죽 크로스백', category: '잡화', description: '스크래치 적음' },
    {
      name: '캔버스 스니커즈',
      category: '스니커즈',
      description: '250mm · 세탁 완료',
    },
  ],
}

/**
 * 방 번호에 맞게 물품 테마를 입힌다. 테마가 없는 방은 목업 그대로 쓴다.
 */
export function themedRoomItems(
  roomId: number,
  items: AuctionItemDetail[],
): AuctionItemDetail[] {
  const theme = ROOM_ITEM_THEMES[roomId]
  if (!theme) return items
  return items.map((item, index) =>
    theme[index] ? { ...item, ...theme[index] } : item,
  )
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
    productUrl: 'https://example.com/jordan',
    status: 'IN_AUCTION',
    createdAt: '2026-07-20',
  },
  {
    id: 2,
    name: '빈티지 데님 자켓',
    category: '아우터',
    description: 'L 사이즈 · 90년대 리바이스',
    productUrl: 'https://example.com/denim',
    status: 'SOLD',
    createdAt: '2026-07-19',
  },
  {
    id: 3,
    name: '희귀 포토카드 세트',
    category: '컬렉터블',
    description: '슬리브 포함 · 상태 A급',
    productUrl: 'https://example.com/photocard',
    status: 'DRAFT',
    createdAt: '2026-07-15',
  },
  {
    id: 4,
    name: '레트로 필름 카메라',
    category: '카메라',
    description: '작동 확인 완료 · 케이스 포함',
    productUrl: 'https://example.com/film-camera',
    status: 'UNSOLD',
    createdAt: '2026-07-02',
  },
  {
    id: 5,
    name: '핸드메이드 가죽지갑',
    category: '잡화',
    description: '베지터블 태닝 · 각인 가능',
    productUrl: 'https://example.com/leather-wallet',
    status: 'SOLD',
    createdAt: '2026-06-28',
  },
  {
    id: 6,
    name: '리미티드 워치',
    category: '시계',
    description: '정품 보증서 포함 · 스크래치 없음',
    productUrl: 'https://example.com/limited-watch',
    status: 'DRAFT',
    createdAt: '2026-06-21',
  },
  {
    id: 7,
    name: '레트로 피규어',
    category: '컬렉터블',
    description: '미개봉 · 초판',
    productUrl: 'https://example.com/retro-figure',
    status: 'UNSOLD',
    createdAt: '2026-06-14',
  },
  {
    id: 8,
    name: '빈티지 필름 렌즈',
    category: '카메라',
    description: '곰팡이 없음 · 헬리코이드 부드러움',
    productUrl: 'https://example.com/film-lens',
    status: 'DRAFT',
    createdAt: '2026-06-02',
  },
]

export const MOCK_TRADES: TradeSummary[] = [
  {
    id: 1,
    auctionItemId: 3,
    productName: '아이돌 포토카드 세트',
    category: '컬렉터블',
    roomId: 1,
    roomTitle: '7월 셀러 라이브 경매방',
    role: 'BUYER',
    status: 'IN_PROGRESS',
    amount: 22000,
    partnerNickname: '피규어덕후',
    partnerPhone: '010-2345-6789',
    closedAt: '2026-07-30',
  },
  {
    id: 2,
    auctionItemId: 2,
    productId: 2,
    productName: '빈티지 데님 자켓',
    category: '아우터',
    roomId: 4,
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
    productId: 7,
    productName: '레트로 피규어',
    category: '컬렉터블',
    roomId: 3,
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
    roomId: 1,
    roomTitle: '7월 셀러 라이브 경매방',
    role: 'BUYER',
    status: 'IN_PROGRESS',
    amount: 13000,
    partnerNickname: '조던수집가',
    partnerPhone: '010-3456-7890',
    closedAt: '2026-07-29',
  },
  {
    id: 5,
    auctionItemId: 6,
    productId: 5,
    productName: '핸드메이드 가죽지갑',
    category: '잡화',
    roomId: 6,
    roomTitle: '봄 시즌 오프',
    role: 'SELLER',
    status: 'COMPLETED',
    amount: 45000,
    partnerNickname: '가죽공방',
    partnerPhone: '010-4567-8901',
    closedAt: '2026-05-11',
  },
  {
    id: 6,
    auctionItemId: 7,
    productId: 4,
    productName: '레트로 필름 카메라',
    category: '카메라',
    roomId: 5,
    roomTitle: '한정판 굿즈 정리',
    role: 'SELLER',
    status: 'UNSOLD',
    amount: 0,
    partnerNickname: '유효한 낙찰자 없음',
    partnerPhone: '-',
    closedAt: '2026-06-02',
  },
]

/**
 * 낙찰 후보 / 최종 순위.
 *
 * Figma `WEB-03 · 판매자 · 거래 상세`(1~5위)와 `WEB-02 · 구매자 · 거래 상세`
 * (6~10위, 2페이지)에 그려진 값을 그대로 옮겼다. 두 화면 모두 5명씩 끊어
 * 보여주고 하단에 "총 12명 · 5명씩"이 붙으므로 12명을 채워둔다.
 *
 * 1위는 거래 실패해 2위로 승계된 상태다. 구매자 화면의 "나"는 7위다.
 */
export const MOCK_CANDIDATES: DealCandidate[] = [
  {
    id: 1,
    rank: 1,
    nickname: '스니커홀릭',
    phone: '010-1234-5678',
    amount: 85000,
    status: 'FAILED',
  },
  {
    id: 2,
    rank: 2,
    nickname: '조던매니아',
    phone: '010-2345-6789',
    amount: 82000,
    status: 'IN_PROGRESS',
  },
  {
    id: 3,
    rank: 3,
    nickname: '수집러버',
    phone: '010-3456-7890',
    amount: 80000,
    status: 'WAITING',
  },
  {
    id: 4,
    rank: 4,
    nickname: '발망러버',
    phone: '010-4567-8901',
    amount: 78000,
    status: 'WAITING',
  },
  {
    id: 5,
    rank: 5,
    nickname: '킥스타',
    phone: '010-5678-9012',
    amount: 75000,
    status: 'WAITING',
  },
  {
    id: 6,
    rank: 6,
    nickname: '슈프림홀릭',
    phone: '010-6789-0123',
    amount: 68000,
    status: 'WAITING',
  },
  {
    id: 7,
    rank: 7,
    nickname: '빈티지러버',
    phone: '010-7890-1234',
    amount: 66000,
    status: 'WAITING',
    isMe: true,
  },
  {
    id: 8,
    rank: 8,
    nickname: '조던킥스',
    phone: '010-8901-2345',
    amount: 64000,
    status: 'WAITING',
  },
  {
    id: 9,
    rank: 9,
    nickname: '데일리슈',
    phone: '010-9012-3456',
    amount: 62000,
    status: 'WAITING',
  },
  {
    id: 10,
    rank: 10,
    nickname: '오프화이트',
    phone: '010-0123-4567',
    amount: 60000,
    status: 'WAITING',
  },
  {
    id: 11,
    rank: 11,
    nickname: '스트릿핏',
    phone: '010-1357-2468',
    amount: 58000,
    status: 'WAITING',
  },
  {
    id: 12,
    rank: 12,
    nickname: '주말경매러',
    phone: '010-2468-1357',
    amount: 56000,
    status: 'WAITING',
  },
]
