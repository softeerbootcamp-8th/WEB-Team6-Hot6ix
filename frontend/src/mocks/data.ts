import type {
  AuctionItemDetail,
  AuctionRoomDetail,
  AuctionRoomSummary,
  Product,
  TradeSummary,
} from '@/types/domain'

/**
 * 화면 확인용 목업 데이터.
 *
 * 실제 값은 API 에서 온다. 마감 시각만 화면을 열 때 기준으로 계산해서
 * 카운트다운이 실제로 흐르게 해뒀다.
 *
 * **한 벌로 이어져 있다.** 방 → 물품 → 거래 → 상품이 id 로 연결되고
 * 이름·금액·낙찰자가 화면마다 같은 값을 쓴다. 한쪽만 고치면 다른 화면에서
 * 어긋나므로 아래 규칙을 지킨다.
 *
 * - 물품 id 는 `방번호 * 100 + 순번` 이라 방을 넘어서도 겹치지 않는다.
 *   `/trades/$itemId` 가 물품 id 하나로 거래를 정확히 찾는 근거다.
 * - 거래는 **내 거래만** 있다. 내가 판 방(`role: 'SELLER'`)은 물품마다
 *   거래가 생기고, 남이 판 방은 내가 낙찰받은 물품에만 생긴다.
 * - 판매자 방의 판매자명은 내 가게(아래 `MY_SHOP`)다.
 */

const now = Date.now()

/** 로그인한 나. `lib/session.ts` 의 `MOCK_MEMBER` 와 같은 값이어야 한다. */
const ME = '기승민'
/** 내 가게 이름. 목업 방·거래에만 쓴다(실제 가게명은 서버가 준다). */
const MY_SHOP = '승민이네 빈티지'

/** 지금부터 n초 뒤의 ISO 시각 */
function after(seconds: number) {
  return new Date(now + seconds * 1000).toISOString()
}

/** 지금부터 n분 전의 ISO 시각 */
function before(minutes: number) {
  return new Date(now - minutes * 60 * 1000).toISOString()
}

/** 종료된 방의 물품 마감 시각. 방이 닫힌 날로 맞춘다. */
function closedOn(date: string) {
  return new Date(`${date}T21:00:00+09:00`).toISOString()
}

export const MOCK_ROOMS: AuctionRoomSummary[] = [
  {
    id: 1,
    title: '7월 셀러 라이브 경매방',
    sellerName: '원기 셀러',
    status: 'LIVE',
    role: 'BUYER',
    itemCount: 6,
    participantCount: 128,
    closedAt: null,
  },
  {
    id: 2,
    title: '포토카드 특가전',
    sellerName: MY_SHOP,
    status: 'LIVE',
    role: 'SELLER',
    itemCount: 3,
    participantCount: 64,
    closedAt: null,
  },
  {
    id: 3,
    title: '신규 입고 테스트',
    sellerName: MY_SHOP,
    status: 'CLOSED',
    role: 'SELLER',
    itemCount: 2,
    participantCount: 41,
    closedAt: '2026-07-18',
  },
  {
    id: 4,
    title: '스니커즈 위크',
    sellerName: '한기 셀러',
    status: 'CLOSED',
    role: 'BUYER',
    itemCount: 5,
    participantCount: 213,
    closedAt: '2026-06-20',
  },
  {
    id: 5,
    title: '한정판 굿즈 정리',
    sellerName: '서지 셀러',
    status: 'CLOSED',
    role: 'BUYER',
    itemCount: 3,
    participantCount: 88,
    closedAt: '2026-06-02',
  },
  {
    id: 7,
    title: '오픈 준비 중인 방',
    sellerName: MY_SHOP,
    status: 'READY',
    role: 'SELLER',
    itemCount: 0,
    participantCount: 0,
    closedAt: null,
  },
  {
    id: 6,
    title: '봄 시즌 오프',
    sellerName: MY_SHOP,
    status: 'CLOSED',
    role: 'SELLER',
    itemCount: 4,
    participantCount: 57,
    closedAt: '2026-05-11',
  },
]

/**
 * 라이브 경매방 (`/rooms/1`). 시연의 중심이 되는 방이다.
 *
 * 진행 중 2 · 시작 전 1 · 종료 3 으로 물품 상태를 모두 깔아 둔다.
 * 종료된 `106` 은 내가 7위 후보로 올라 있어서 거래 상세(WEB-02)로 이어진다.
 */
const ROOM_1: AuctionRoomDetail = {
  id: 1,
  title: '7월 셀러 라이브 경매방',
  description: '한정판 스니커즈와 빈티지 의류를 정리합니다.',
  sellerName: '원기 셀러',
  sellerImageUrl: null,
  liveUrl: null,
  status: 'LIVE',
  role: 'BUYER',
  participantCount: 128,
  shareCode: 'live001',
  softCloseSeconds: 30,
  softCloseTriggerSeconds: 60,
  bidUnit: 1000,
  items: [
    {
      id: 101,
      roomId: 1,
      name: '한정판 조던 스니커즈',
      category: '스니커즈',
      description:
        '오프화이트와 레드 컬러 조합의 한정판 조던 스니커즈입니다. 보관 상태가 좋고 실착 횟수가 적어 전체적인 컨디션이 우수합니다. 구성품과 상세 상태는 상품 링크에서 확인할 수 있습니다.',
      productUrl: 'brand.com/limited-jordan',
      status: 'ACTIVE',
      sold: false,
      startPrice: 50000,
      currentPrice: 85000,
      bidUnit: 1000,
      endsAt: after(7200),
      bidCount: 12,
      topBidderNickname: '스니커홀릭',
      extended: false,
      leaderboard: [
        { rank: 1, nickname: '스니커홀릭', amount: 85000, isMe: false },
        { rank: 2, nickname: '조던매니아', amount: 82000, isMe: false },
        { rank: 3, nickname: ME, amount: 80000, isMe: true },
        { rank: 4, nickname: '슈즈러버', amount: 78000, isMe: false },
        { rank: 5, nickname: '킥스타', amount: 75000, isMe: false },
      ],
      history: [
        { id: 3, nickname: '스니커홀릭', amount: 85000, bidAt: before(1) },
        { id: 2, nickname: '조던매니아', amount: 82000, bidAt: before(3) },
        { id: 1, nickname: ME, amount: 80000, bidAt: before(6) },
      ],
    },
    {
      id: 102,
      roomId: 1,
      name: '빈티지 데님 자켓',
      category: '아우터',
      description:
        '90년대 리바이스 데님 자켓입니다. L 사이즈이고 워싱이 자연스럽게 빠져 있습니다. 소매 끝 마감과 단추 상태는 사진으로 확인해주세요.',
      productUrl: 'brand.com/vintage-denim',
      status: 'ACTIVE',
      sold: false,
      startPrice: 8000,
      currentPrice: 13000,
      bidUnit: 1000,
      /*
       * 시연용으로 짧게 잡았다. 화면을 열고 1분 뒤 마감되므로 마감 임박 표시,
       * 소프트클로즈 +30초 연장(입찰 시), 자동 마감까지 한 번에 보여줄 수 있다.
       * 새로고침하면 다시 1분부터 센다.
       */
      endsAt: after(60),
      bidCount: 7,
      topBidderNickname: '데님러버',
      extended: true,
      leaderboard: [
        { rank: 1, nickname: '데님러버', amount: 13000, isMe: false },
        { rank: 2, nickname: '빈티지홀릭', amount: 12000, isMe: false },
        { rank: 3, nickname: ME, amount: 11000, isMe: true },
      ],
      history: [
        { id: 3, nickname: '데님러버', amount: 13000, bidAt: before(1) },
        { id: 2, nickname: '빈티지홀릭', amount: 12000, bidAt: before(2) },
        { id: 1, nickname: ME, amount: 11000, bidAt: before(4) },
      ],
    },
    {
      id: 103,
      roomId: 1,
      name: '아이돌 포토카드 세트',
      category: '컬렉터블',
      description:
        '미개봉 상태의 포토카드 세트입니다. 슬리브에 넣어 보관했고 스크래치가 없습니다.',
      productUrl: 'brand.com/photocard-set',
      status: 'READY',
      sold: false,
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
      id: 104,
      roomId: 1,
      name: '리미티드 워치',
      category: '시계',
      description:
        '한정 수량으로 나온 오토매틱 워치입니다. 정품 보증서와 여분 스트랩이 함께 있습니다.',
      productUrl: 'brand.com/limited-watch',
      status: 'CLOSED',
      sold: true,
      startPrice: 30000,
      currentPrice: 56000,
      bidUnit: 2000,
      endsAt: before(12),
      bidCount: 19,
      topBidderNickname: '시계덕후',
      extended: false,
      leaderboard: [
        { rank: 1, nickname: '시계덕후', amount: 56000, isMe: false },
        { rank: 2, nickname: '스니커홀릭', amount: 54000, isMe: false },
        { rank: 3, nickname: '빈티지홀릭', amount: 52000, isMe: false },
      ],
      history: [
        { id: 2, nickname: '시계덕후', amount: 56000, bidAt: before(13) },
        { id: 1, nickname: '스니커홀릭', amount: 54000, bidAt: before(15) },
      ],
    },
    {
      id: 105,
      roomId: 1,
      name: '레트로 피규어',
      category: '컬렉터블',
      description:
        '90년대 발매된 레트로 피규어입니다. 박스는 없고 본체만 있습니다.',
      productUrl: 'brand.com/retro-figure',
      status: 'CLOSED',
      sold: true,
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
      id: 106,
      roomId: 1,
      name: '핸드메이드 가죽지갑',
      category: '잡화',
      description:
        '베지터블 가죽으로 직접 만든 반지갑입니다. 사용할수록 색이 짙어집니다.',
      productUrl: 'brand.com/leather-wallet',
      status: 'CLOSED',
      sold: true,
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
        { rank: 3, nickname: '슈프림홀릭', amount: 43000, isMe: false },
        { rank: 4, nickname: '오프화이트', amount: 42000, isMe: false },
        { rank: 5, nickname: '스트릿핏', amount: 41000, isMe: false },
      ],
      history: [
        { id: 2, nickname: '가죽공방', amount: 45000, bidAt: before(34) },
        { id: 1, nickname: '데님러버', amount: 44000, bidAt: before(36) },
      ],
    },
  ],
}

/** 내가 연 라이브 방 (`/rooms/2`). 판매자 시점 라이브 화면을 본다. */
const ROOM_2: AuctionRoomDetail = {
  id: 2,
  title: '포토카드 특가전',
  description: '모아둔 포토카드를 한 번에 정리합니다.',
  sellerName: MY_SHOP,
  sellerImageUrl: null,
  liveUrl: null,
  status: 'LIVE',
  role: 'SELLER',
  participantCount: 64,
  shareCode: 'live002',
  softCloseSeconds: 30,
  softCloseTriggerSeconds: 60,
  bidUnit: 1000,
  items: [
    {
      id: 201,
      roomId: 2,
      name: '희귀 포토카드 세트',
      category: '컬렉터블',
      description:
        '슬리브에 넣어 보관한 A급 포토카드 세트입니다. 모서리 눌림 없이 깨끗합니다.',
      productUrl: 'https://example.com/photocard',
      status: 'ACTIVE',
      sold: false,
      startPrice: 5000,
      currentPrice: 22000,
      bidUnit: 1000,
      endsAt: after(3600),
      bidCount: 9,
      topBidderNickname: '피규어덕후',
      extended: false,
      leaderboard: [
        { rank: 1, nickname: '피규어덕후', amount: 22000, isMe: false },
        { rank: 2, nickname: '앨범수집가', amount: 21000, isMe: false },
        { rank: 3, nickname: '카드모으기', amount: 19000, isMe: false },
      ],
      history: [
        { id: 2, nickname: '피규어덕후', amount: 22000, bidAt: before(2) },
        { id: 1, nickname: '앨범수집가', amount: 21000, bidAt: before(5) },
      ],
    },
    {
      id: 202,
      roomId: 2,
      name: '미니앨범 한정 포토카드',
      category: '컬렉터블',
      description: '초동 특전으로 나온 미개봉 포토카드입니다.',
      productUrl: 'https://example.com/mini-album',
      status: 'ACTIVE',
      sold: false,
      startPrice: 3000,
      currentPrice: 9000,
      bidUnit: 500,
      endsAt: after(2700),
      bidCount: 5,
      topBidderNickname: '앨범수집가',
      extended: false,
      leaderboard: [
        { rank: 1, nickname: '앨범수집가', amount: 9000, isMe: false },
        { rank: 2, nickname: '카드모으기', amount: 8500, isMe: false },
      ],
      history: [
        { id: 1, nickname: '앨범수집가', amount: 9000, bidAt: before(3) },
      ],
    },
    {
      id: 203,
      roomId: 2,
      name: '홀로그램 포토카드',
      category: '컬렉터블',
      description: '럭키드로우 당첨분입니다. 홀로그램 상태가 선명합니다.',
      productUrl: 'https://example.com/hologram',
      status: 'READY',
      sold: false,
      startPrice: 4000,
      currentPrice: 4000,
      bidUnit: 500,
      endsAt: after(5400),
      bidCount: 0,
      topBidderNickname: null,
      extended: false,
      leaderboard: [],
      history: [],
    },
  ],
}

/** 내가 연 종료된 방 (`/rooms/3`). 물품마다 내 거래가 이어진다. */
const ROOM_3: AuctionRoomDetail = {
  id: 3,
  title: '신규 입고 테스트',
  description: '새로 들어온 카메라 장비를 시험 삼아 올렸습니다.',
  sellerName: MY_SHOP,
  sellerImageUrl: null,
  liveUrl: null,
  status: 'CLOSED',
  role: 'SELLER',
  participantCount: 41,
  shareCode: 'done003',
  softCloseSeconds: 30,
  softCloseTriggerSeconds: 60,
  bidUnit: 1000,
  items: [
    {
      id: 301,
      roomId: 3,
      name: '레트로 필름 카메라',
      category: '카메라',
      description:
        '작동 확인을 마친 필름 카메라입니다. 케이스가 함께 있습니다.',
      productUrl: 'https://example.com/film-camera',
      status: 'CLOSED',
      sold: false,
      startPrice: 40000,
      currentPrice: 40000,
      bidUnit: 2000,
      endsAt: closedOn('2026-07-18'),
      bidCount: 0,
      topBidderNickname: null,
      extended: false,
      leaderboard: [],
      history: [],
    },
    {
      id: 302,
      roomId: 3,
      name: '빈티지 필름 렌즈',
      category: '카메라',
      description: '곰팡이 없이 깨끗하고 헬리코이드가 부드럽습니다.',
      productUrl: 'https://example.com/film-lens',
      status: 'CLOSED',
      sold: true,
      startPrice: 20000,
      currentPrice: 38000,
      bidUnit: 1000,
      endsAt: closedOn('2026-07-18'),
      bidCount: 13,
      topBidderNickname: '우재',
      extended: false,
      leaderboard: [
        { rank: 1, nickname: '우재', amount: 38000, isMe: false },
        { rank: 2, nickname: '필름사랑', amount: 36000, isMe: false },
        { rank: 3, nickname: '올드렌즈', amount: 34000, isMe: false },
      ],
      history: [
        {
          id: 1,
          nickname: '우재',
          amount: 38000,
          bidAt: closedOn('2026-07-18'),
        },
      ],
    },
  ],
}

/** 남이 연 종료된 방 (`/rooms/4`). `402` 만 내가 낙찰받아 거래가 있다. */
const ROOM_4: AuctionRoomDetail = {
  id: 4,
  title: '스니커즈 위크',
  description: '일주일 동안 스니커즈만 모아 진행한 경매입니다.',
  sellerName: '한기 셀러',
  sellerImageUrl: null,
  liveUrl: null,
  status: 'CLOSED',
  role: 'BUYER',
  participantCount: 213,
  shareCode: 'done004',
  softCloseSeconds: 30,
  softCloseTriggerSeconds: 60,
  bidUnit: 1000,
  items: [
    {
      id: 401,
      roomId: 4,
      name: '조던 1 레트로 하이',
      category: '스니커즈',
      description: '270mm · 박스 포함 · 실착 2회',
      productUrl: 'brand.com/jordan-1-retro',
      status: 'CLOSED',
      sold: true,
      startPrice: 90000,
      currentPrice: 132000,
      bidUnit: 2000,
      endsAt: closedOn('2026-06-20'),
      bidCount: 24,
      topBidderNickname: '킥스타',
      extended: false,
      leaderboard: [
        { rank: 1, nickname: '킥스타', amount: 132000, isMe: false },
        { rank: 2, nickname: '조던매니아', amount: 128000, isMe: false },
        { rank: 3, nickname: '슈프림홀릭', amount: 125000, isMe: false },
      ],
      history: [
        {
          id: 1,
          nickname: '킥스타',
          amount: 132000,
          bidAt: closedOn('2026-06-20'),
        },
      ],
    },
    {
      id: 402,
      roomId: 4,
      name: '에어포스 1 로우',
      category: '스니커즈',
      description: '260mm · 미착용 · 정품 확인서 포함',
      productUrl: 'brand.com/air-force-1',
      status: 'CLOSED',
      sold: true,
      startPrice: 50000,
      currentPrice: 78000,
      bidUnit: 1000,
      endsAt: closedOn('2026-06-20'),
      bidCount: 16,
      topBidderNickname: ME,
      extended: false,
      leaderboard: [
        { rank: 1, nickname: ME, amount: 78000, isMe: true },
        { rank: 2, nickname: '데일리슈', amount: 76000, isMe: false },
        { rank: 3, nickname: '스트릿핏', amount: 74000, isMe: false },
      ],
      history: [
        { id: 1, nickname: ME, amount: 78000, bidAt: closedOn('2026-06-20') },
      ],
    },
    {
      id: 403,
      roomId: 4,
      name: '덩크 로우 팬더',
      category: '스니커즈',
      description: '275mm · 2회 착용 · 박스 포함',
      productUrl: 'brand.com/dunk-low-panda',
      status: 'CLOSED',
      sold: true,
      startPrice: 100000,
      currentPrice: 145000,
      bidUnit: 5000,
      endsAt: closedOn('2026-06-20'),
      bidCount: 21,
      topBidderNickname: '슈프림홀릭',
      extended: false,
      leaderboard: [
        { rank: 1, nickname: '슈프림홀릭', amount: 145000, isMe: false },
        { rank: 2, nickname: '킥스타', amount: 140000, isMe: false },
      ],
      history: [
        {
          id: 1,
          nickname: '슈프림홀릭',
          amount: 145000,
          bidAt: closedOn('2026-06-20'),
        },
      ],
    },
    {
      id: 404,
      roomId: 4,
      name: '뉴발란스 992',
      category: '스니커즈',
      description: '265mm · 정품 확인서 · 밑창 마모 적음',
      productUrl: 'brand.com/new-balance-992',
      status: 'CLOSED',
      sold: false,
      startPrice: 120000,
      currentPrice: 120000,
      bidUnit: 5000,
      endsAt: closedOn('2026-06-20'),
      bidCount: 0,
      topBidderNickname: null,
      extended: false,
      leaderboard: [],
      history: [],
    },
    {
      id: 405,
      roomId: 4,
      name: '컨버스 척테일러 70',
      category: '스니커즈',
      description: '280mm · 박스 없음 · 세탁 완료',
      productUrl: 'brand.com/chuck-taylor-70',
      status: 'CLOSED',
      sold: true,
      startPrice: 35000,
      currentPrice: 52000,
      bidUnit: 1000,
      endsAt: closedOn('2026-06-20'),
      bidCount: 11,
      topBidderNickname: '데일리슈',
      extended: false,
      leaderboard: [
        { rank: 1, nickname: '데일리슈', amount: 52000, isMe: false },
        { rank: 2, nickname: '스트릿핏', amount: 50000, isMe: false },
      ],
      history: [
        {
          id: 1,
          nickname: '데일리슈',
          amount: 52000,
          bidAt: closedOn('2026-06-20'),
        },
      ],
    },
  ],
}

/** 남이 연 종료된 방 (`/rooms/5`). `502` 만 내가 낙찰받아 거래가 있다. */
const ROOM_5: AuctionRoomDetail = {
  id: 5,
  title: '한정판 굿즈 정리',
  description: '모아둔 한정판 굿즈를 정리합니다.',
  sellerName: '서지 셀러',
  sellerImageUrl: null,
  liveUrl: null,
  status: 'CLOSED',
  role: 'BUYER',
  participantCount: 88,
  shareCode: 'done005',
  softCloseSeconds: 30,
  softCloseTriggerSeconds: 60,
  bidUnit: 1000,
  items: [
    {
      id: 501,
      roomId: 5,
      name: '한정판 아트토이',
      category: '컬렉터블',
      description: '넘버링이 찍힌 한정판 아트토이입니다. 박스 포함.',
      productUrl: 'brand.com/art-toy',
      status: 'CLOSED',
      sold: true,
      startPrice: 40000,
      currentPrice: 67000,
      bidUnit: 1000,
      endsAt: closedOn('2026-06-02'),
      bidCount: 18,
      topBidderNickname: '오프화이트',
      extended: false,
      leaderboard: [
        { rank: 1, nickname: '오프화이트', amount: 67000, isMe: false },
        { rank: 2, nickname: '수집러버', amount: 65000, isMe: false },
        { rank: 3, nickname: ME, amount: 62000, isMe: true },
      ],
      history: [
        {
          id: 1,
          nickname: '오프화이트',
          amount: 67000,
          bidAt: closedOn('2026-06-02'),
        },
      ],
    },
    {
      id: 502,
      roomId: 5,
      name: '캐릭터 피규어 세트',
      category: '컬렉터블',
      description: '5종 풀세트입니다. 전부 미개봉 상태입니다.',
      productUrl: 'brand.com/figure-set',
      status: 'CLOSED',
      sold: false,
      startPrice: 18000,
      currentPrice: 31000,
      bidUnit: 1000,
      endsAt: closedOn('2026-06-02'),
      bidCount: 9,
      topBidderNickname: ME,
      extended: false,
      leaderboard: [
        { rank: 1, nickname: ME, amount: 31000, isMe: true },
        { rank: 2, nickname: '수집러버', amount: 30000, isMe: false },
      ],
      history: [
        { id: 1, nickname: ME, amount: 31000, bidAt: closedOn('2026-06-02') },
      ],
    },
    {
      id: 503,
      roomId: 5,
      name: '굿즈 포스터 세트',
      category: '컬렉터블',
      description: '튜브에 넣어 보관해 접힘이 없습니다.',
      productUrl: 'brand.com/poster-set',
      status: 'CLOSED',
      sold: false,
      startPrice: 15000,
      currentPrice: 15000,
      bidUnit: 1000,
      endsAt: closedOn('2026-06-02'),
      bidCount: 0,
      topBidderNickname: null,
      extended: false,
      leaderboard: [],
      history: [],
    },
  ],
}

/** 내가 연 종료된 방 (`/rooms/6`). 물품 4개 모두 내 거래로 이어진다. */
const ROOM_6: AuctionRoomDetail = {
  id: 6,
  title: '봄 시즌 오프',
  description: '봄에 입던 옷과 잡화를 정리합니다.',
  sellerName: MY_SHOP,
  sellerImageUrl: null,
  liveUrl: null,
  status: 'CLOSED',
  role: 'SELLER',
  participantCount: 57,
  shareCode: 'done006',
  softCloseSeconds: 30,
  softCloseTriggerSeconds: 60,
  bidUnit: 1000,
  items: [
    {
      id: 601,
      roomId: 6,
      name: '트렌치 코트',
      category: '아우터',
      description: '95 사이즈 · 드라이 완료 · 벨트 포함',
      productUrl: 'https://example.com/trench-coat',
      status: 'CLOSED',
      sold: true,
      startPrice: 25000,
      currentPrice: 45000,
      bidUnit: 1000,
      endsAt: closedOn('2026-05-11'),
      bidCount: 14,
      topBidderNickname: '코트러버',
      extended: false,
      leaderboard: [
        { rank: 1, nickname: '코트러버', amount: 45000, isMe: false },
        { rank: 2, nickname: '봄바람', amount: 43000, isMe: false },
        { rank: 3, nickname: '데일리핏', amount: 41000, isMe: false },
      ],
      history: [
        {
          id: 1,
          nickname: '코트러버',
          amount: 45000,
          bidAt: closedOn('2026-05-11'),
        },
      ],
    },
    {
      id: 602,
      roomId: 6,
      name: '니트 가디건',
      category: '상의',
      description: '오버핏 · 보풀 없음 · 단추 여분 있음',
      productUrl: 'https://example.com/knit-cardigan',
      status: 'CLOSED',
      sold: true,
      startPrice: 12000,
      currentPrice: 23000,
      bidUnit: 1000,
      endsAt: closedOn('2026-05-11'),
      bidCount: 8,
      topBidderNickname: '니트홀릭',
      extended: false,
      leaderboard: [
        { rank: 1, nickname: '니트홀릭', amount: 23000, isMe: false },
        { rank: 2, nickname: '데일리핏', amount: 22000, isMe: false },
      ],
      history: [
        {
          id: 1,
          nickname: '니트홀릭',
          amount: 23000,
          bidAt: closedOn('2026-05-11'),
        },
      ],
    },
    {
      id: 603,
      roomId: 6,
      name: '가죽 크로스백',
      category: '잡화',
      description: '스크래치가 적고 스트랩 길이 조절이 됩니다.',
      productUrl: 'https://example.com/leather-bag',
      status: 'CLOSED',
      sold: false,
      startPrice: 30000,
      currentPrice: 30000,
      bidUnit: 1000,
      endsAt: closedOn('2026-05-11'),
      bidCount: 0,
      topBidderNickname: null,
      extended: false,
      leaderboard: [],
      history: [],
    },
    {
      id: 604,
      roomId: 6,
      name: '캔버스 스니커즈',
      category: '스니커즈',
      description: '250mm · 세탁 완료 · 끈 여분 포함',
      productUrl: 'https://example.com/canvas-sneakers',
      status: 'CLOSED',
      sold: true,
      startPrice: 18000,
      currentPrice: 31000,
      bidUnit: 1000,
      endsAt: closedOn('2026-05-11'),
      bidCount: 10,
      topBidderNickname: '데일리슈',
      extended: false,
      leaderboard: [
        { rank: 1, nickname: '데일리슈', amount: 31000, isMe: false },
        { rank: 2, nickname: '봄바람', amount: 30000, isMe: false },
      ],
      history: [
        {
          id: 1,
          nickname: '데일리슈',
          amount: 31000,
          bidAt: closedOn('2026-05-11'),
        },
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
  sellerName: MY_SHOP,
  sellerImageUrl: null,
  liveUrl: null,
  status: 'READY',
  role: 'SELLER',
  participantCount: 0,
  shareCode: 'ready007',
  softCloseSeconds: 30,
  softCloseTriggerSeconds: 60,
  bidUnit: 1000,
  items: [],
}

/** 방 번호로 찾는 상세 목업. 방마다 자기 물품을 가진다. */
export const MOCK_ROOM_DETAILS: Record<number, AuctionRoomDetail> = {
  1: ROOM_1,
  2: ROOM_2,
  3: ROOM_3,
  4: ROOM_4,
  5: ROOM_5,
  6: ROOM_6,
  7: MOCK_EMPTY_ROOM,
}

/** 라이브 경매방 목업. 방을 지정하지 않는 화면이 기본값으로 쓴다. */
export const MOCK_ROOM_DETAIL = ROOM_1

/** 방 번호로 상세 목업을 찾는다. 없는 방이면 `undefined`. */
export function findMockRoom(roomId: number): AuctionRoomDetail | undefined {
  return MOCK_ROOM_DETAILS[roomId]
}

/** 공유 코드로 상세 목업을 찾는다. `/join/$shareCode` 가 쓴다. */
export function findMockRoomByShareCode(
  shareCode: string,
): AuctionRoomDetail | undefined {
  return Object.values(MOCK_ROOM_DETAILS).find(
    (room) => room.shareCode === shareCode,
  )
}

/** 모든 방의 물품을 한 줄로 편다. 물품 id 는 방을 넘어서도 겹치지 않는다. */
const ALL_ITEMS: AuctionItemDetail[] = Object.values(MOCK_ROOM_DETAILS).flatMap(
  (room) => room.items,
)

/** 물품 id 로 물품을 찾는다. 어느 방에 있든 상관없다. */
export function findMockItem(itemId: number): AuctionItemDetail | undefined {
  return ALL_ITEMS.find((item) => item.id === itemId)
}

/**
 * 내 상품.
 *
 * 내가 연 방(2·3·6번)에 올린 물품과 이름·분류가 같고, 상태는 그 경매의
 * 결과와 맞춰 둔다. `DRAFT` 3개는 경매방을 새로 만들 때 고를 수 있게 남긴다.
 */
export const MOCK_PRODUCTS: Product[] = [
  {
    id: 1,
    name: '희귀 포토카드 세트',
    category: '컬렉터블',
    description: '슬리브 포함 · 상태 A급',
    productUrl: 'https://example.com/photocard',
    status: 'IN_AUCTION',
    createdAt: '2026-07-28',
  },
  {
    id: 2,
    name: '미니앨범 한정 포토카드',
    category: '컬렉터블',
    description: '초동 특전 · 미개봉',
    productUrl: 'https://example.com/mini-album',
    status: 'IN_AUCTION',
    createdAt: '2026-07-28',
  },
  {
    id: 3,
    name: '홀로그램 포토카드',
    category: '컬렉터블',
    description: '럭키드로우 당첨분',
    productUrl: 'https://example.com/hologram',
    status: 'IN_AUCTION',
    createdAt: '2026-07-27',
  },
  {
    id: 4,
    name: '레트로 필름 카메라',
    category: '카메라',
    description: '작동 확인 완료 · 케이스 포함',
    productUrl: 'https://example.com/film-camera',
    status: 'UNSOLD',
    createdAt: '2026-07-10',
  },
  {
    id: 5,
    name: '빈티지 필름 렌즈',
    category: '카메라',
    description: '곰팡이 없음 · 헬리코이드 부드러움',
    productUrl: 'https://example.com/film-lens',
    status: 'SOLD',
    createdAt: '2026-07-10',
  },
  {
    id: 6,
    name: '트렌치 코트',
    category: '아우터',
    description: '95 사이즈 · 드라이 완료',
    productUrl: 'https://example.com/trench-coat',
    status: 'SOLD',
    createdAt: '2026-05-02',
  },
  {
    id: 7,
    name: '니트 가디건',
    category: '상의',
    description: '오버핏 · 보풀 없음',
    productUrl: 'https://example.com/knit-cardigan',
    status: 'SOLD',
    createdAt: '2026-05-02',
  },
  {
    id: 8,
    name: '가죽 크로스백',
    category: '잡화',
    description: '스크래치 적음 · 스트랩 조절 가능',
    productUrl: 'https://example.com/leather-bag',
    status: 'UNSOLD',
    createdAt: '2026-05-01',
  },
  {
    id: 9,
    name: '캔버스 스니커즈',
    category: '스니커즈',
    description: '250mm · 세탁 완료',
    productUrl: 'https://example.com/canvas-sneakers',
    status: 'SOLD',
    createdAt: '2026-05-01',
  },
  {
    id: 10,
    name: '러닝화 한정판',
    category: '스니커즈',
    description: '270mm · 미착용 · 박스 포함',
    productUrl: 'https://example.com/running-shoes',
    status: 'DRAFT',
    createdAt: '2026-08-01',
  },
  {
    id: 11,
    name: '기계식 키보드',
    category: '전자제품',
    description: '적축 · 키캡 세트 포함',
    productUrl: 'https://example.com/keyboard',
    status: 'DRAFT',
    createdAt: '2026-07-30',
  },
  {
    id: 12,
    name: '무선 이어폰',
    category: '전자제품',
    description: '충전 케이스 포함 · 배터리 정상',
    productUrl: 'https://example.com/earbuds',
    status: 'DRAFT',
    createdAt: '2026-07-25',
  },
]

/**
 * 내 거래.
 *
 * 물품에서 그대로 파생된다. `auctionItemId` 는 실제 물품 id 이고
 * 이름·금액은 그 물품의 낙찰 결과와 같다. 내가 판 방은 물품마다,
 * 남이 판 방은 내가 낙찰받은 물품에만 거래가 생긴다.
 *
 * **`productId` 만 90000x 대다.** 상품 화면이 실제 API 로 넘어가면서 서버가 주는
 * 상품 id 도 1, 2, 3… 이 되었다. 예전처럼 한 자리 수로 두면 판매자가 방금 등록한
 * 상품 상세에 남의 목업 낙찰가가 붙어 보인다. 거래까지 연동되면 이 배열은 사라진다.
 */
export const MOCK_TRADES: TradeSummary[] = [
  {
    id: 1,
    auctionItemId: 106,
    productName: '핸드메이드 가죽지갑',
    category: '잡화',
    roomId: 1,
    roomTitle: '7월 셀러 라이브 경매방',
    role: 'BUYER',
    status: 'IN_PROGRESS',
    amount: 45000,
    partnerNickname: '원기 셀러',
    partnerPhone: '010-2345-6789',
    closedAt: '2026-08-03',
  },
  {
    id: 2,
    auctionItemId: 302,
    productId: 900005,
    productName: '빈티지 필름 렌즈',
    category: '카메라',
    roomId: 3,
    roomTitle: '신규 입고 테스트',
    role: 'SELLER',
    status: 'IN_PROGRESS',
    amount: 38000,
    partnerNickname: '우재',
    partnerPhone: '010-3456-7890',
    closedAt: '2026-07-18',
  },
  {
    id: 3,
    auctionItemId: 301,
    productId: 900004,
    productName: '레트로 필름 카메라',
    category: '카메라',
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
    auctionItemId: 402,
    productName: '에어포스 1 로우',
    category: '스니커즈',
    roomId: 4,
    roomTitle: '스니커즈 위크',
    role: 'BUYER',
    status: 'COMPLETED',
    amount: 78000,
    partnerNickname: '한기 셀러',
    partnerPhone: '010-4567-8901',
    closedAt: '2026-06-20',
  },
  {
    id: 5,
    auctionItemId: 502,
    productName: '캐릭터 피규어 세트',
    category: '컬렉터블',
    roomId: 5,
    roomTitle: '한정판 굿즈 정리',
    role: 'BUYER',
    status: 'COMPLETED',
    amount: 31000,
    partnerNickname: '서지 셀러',
    partnerPhone: '010-2345-6789',
    closedAt: '2026-06-02',
  },
  {
    id: 6,
    auctionItemId: 601,
    productId: 900006,
    productName: '트렌치 코트',
    category: '아우터',
    roomId: 6,
    roomTitle: '봄 시즌 오프',
    role: 'SELLER',
    status: 'COMPLETED',
    amount: 45000,
    partnerNickname: '코트러버',
    partnerPhone: '010-5678-9012',
    closedAt: '2026-05-11',
  },
  {
    id: 7,
    auctionItemId: 602,
    productId: 900007,
    productName: '니트 가디건',
    category: '상의',
    roomId: 6,
    roomTitle: '봄 시즌 오프',
    role: 'SELLER',
    status: 'COMPLETED',
    amount: 23000,
    partnerNickname: '니트홀릭',
    partnerPhone: '010-6789-0123',
    closedAt: '2026-05-11',
  },
  {
    id: 8,
    auctionItemId: 603,
    productId: 900008,
    productName: '가죽 크로스백',
    category: '잡화',
    roomId: 6,
    roomTitle: '봄 시즌 오프',
    role: 'SELLER',
    status: 'UNSOLD',
    amount: 0,
    partnerNickname: '유효한 낙찰자 없음',
    partnerPhone: '-',
    closedAt: '2026-05-11',
  },
  {
    id: 9,
    auctionItemId: 604,
    productId: 900009,
    productName: '캔버스 스니커즈',
    category: '스니커즈',
    roomId: 6,
    roomTitle: '봄 시즌 오프',
    role: 'SELLER',
    status: 'COMPLETED',
    amount: 31000,
    partnerNickname: '데일리슈',
    partnerPhone: '010-7890-1234',
    closedAt: '2026-05-11',
  },
]

/** 물품 id 로 이어지는 거래를 찾는다. 없으면 내 거래가 아닌 물품이다. */
export function findMockTrade(itemId: number): TradeSummary | undefined {
  return MOCK_TRADES.find((trade) => trade.auctionItemId === itemId)
}
