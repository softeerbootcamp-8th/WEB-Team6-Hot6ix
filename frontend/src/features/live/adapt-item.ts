import type {
  AuctionItemDetailResponseDto,
  AuctionItemSummaryResponseDto,
  LeaderboardEntryResponseDto,
} from '@/api/generated/model'
import { MOCK_ROOM_DETAIL } from '@/mocks/data'
import type {
  AuctionItemDetail,
  ItemStatus,
  LeaderboardEntry,
} from '@/mocks/types'

/**
 * 서버 물품 DTO 를 화면이 쓰는 `AuctionItemDetail` 로 바꾼다.
 *
 * 서버에는 아직 입찰 이력·입찰 수·카테고리가 없는데, 이 값들이 6개 컴포넌트에
 * 박혀 있다. 화면 타입을 서버에 맞추면 라이브 기능을 통째로 다시 써야 하므로,
 * 변환을 여기 한 곳에 몰아넣고 없는 값만 목업에서 빌려온다.
 *
 * **리더보드·최고 입찰자·사진은 목업을 쓰지 않는다.** 실제 입찰과 무관한 순위가
 * 화면에 남는 문제(#136) 때문에 서버 값만 쓰고, 없으면 비워 둔다. 사진도 같은
 * 이유다 — 이름으로 고른 목업 사진이 뜨면 판매자가 올린 것과 다른 사진이
 * 경매 중에 보인다(#138).
 *
 * ponytail: 임시물이다. 남은 목업 필드가 실제 값으로 채워지면
 * (`history` 는 #134, `extended` 는 Soft Close 연장 로직) `fallback` 인자를
 * 지우고 순수 변환 함수로 줄인다.
 */

/**
 * 서버 상태 4가지를 화면 상태 3가지로 좁힌다. 낙찰·유찰은 화면에선 둘 다 종료다.
 *
 * **낙찰인지 유찰인지가 여기서 사라지므로 {@code sold} 로 따로 남긴다.** 그 구분을
 * 버리면 화면이 "닫혔으면 곧 낙찰"로 단정하게 되어, 입찰이 없던 물품에도 낙찰가가 붙는다.
 */
function toItemStatus(status: string | undefined): ItemStatus {
  switch (status) {
    case 'IN_PROGRESS':
      return 'ACTIVE'
    case 'SOLD':
    case 'FAILED':
      return 'CLOSED'
    default:
      return 'READY'
  }
}

/**
 * 서버 리더보드를 화면 타입으로 옮긴다.
 *
 * **목업으로 떨어지지 않는다.** 서버가 값을 안 주면 빈 리더보드다. 예전에는
 * 목업 순위를 빌려 썼는데, 실제 입찰과 무관한 가짜 순위가 조용히 화면에 남아
 * 시연에서 입찰을 넣어도 순위가 그대로였다. 비어 보이는 게 틀린 값보다 낫다.
 *
 * `isMe` 는 서버가 내려주지 않아 세션 닉네임으로 비교한다. 닉네임에 unique
 * 제약이 없어서(카카오 이름을 그대로 쓴다) **동명이인이면 남의 줄이 내 순위로
 * 강조된다.** 정확히 하려면 응답에 `isMe` 가 필요하다.
 *
 * 닉네임 없는 줄은 그릴 수 없으니 버린다. springdoc 이 모든 필드를 optional 로
 * 내보내서 타입에만 나타나는 경우이고, 실제 응답에는 늘 채워져 있다. 버린 줄이
 * 있으면 서버 순위에 구멍이 생기므로 남은 순서로 다시 매긴다.
 */
function toLeaderboard(
  entries: LeaderboardEntryResponseDto[] | undefined,
  myNickname: string | null,
): LeaderboardEntry[] {
  const named = (entries ?? []).filter(
    (entry): entry is LeaderboardEntryResponseDto & { nickname: string } =>
      entry.nickname !== undefined,
  )

  return named.map((entry, index) => ({
    rank: entry.rank ?? index + 1,
    nickname: entry.nickname,
    amount: entry.amount ?? 0,
    isMe: myNickname !== null && entry.nickname === myNickname,
  }))
}

/**
 * 서버에 없는 필드를 채울 목업을 순번으로 고른다.
 * 물품 수가 목업보다 많으면 앞에서부터 다시 쓴다.
 */
export function fallbackItem(index: number): AuctionItemDetail {
  const items = MOCK_ROOM_DETAIL.items
  return items[index % items.length]
}

export function toAuctionItemDetail(
  dto: AuctionItemSummaryResponseDto | AuctionItemDetailResponseDto,
  fallback: AuctionItemDetail,
  myNickname: string | null = null,
): AuctionItemDetail {
  // 상세 DTO 에만 있는 필드. 목록 응답이면 undefined 라 목업으로 떨어진다.
  const detail = dto as AuctionItemDetailResponseDto
  const status = toItemStatus(dto.status)
  const leaderboard = toLeaderboard(dto.leaderboard, myNickname)

  return {
    ...fallback,
    id: dto.auctionItemId ?? fallback.id,
    roomId: detail.auctionRoomId ?? fallback.roomId,
    name: dto.productName ?? fallback.name,
    /*
     * 목업으로 떨어지지 않는다. 값이 없으면 `ProductThumbnail` 이 회색 아이콘을
     * 그린다. 목록 응답과 상세 응답 둘 다 `imageUrl` 을 준다.
     */
    imageUrl: dto.imageUrl ?? null,
    description: detail.description ?? fallback.description,
    /*
     * 목업으로 떨어지지 않는다. 목업 링크(`brand.com/…`)는 팀이 가진 도메인이
     * 아니라서, 누르면 관계없는 사이트가 새 창으로 열린다. 없으면 링크를
     * 감추는 게 맞다.
     *
     * 목록 응답에는 애초에 링크가 없다. 물품을 열어 상세를 받은 뒤에 뜬다.
     */
    productUrl: detail.referenceUrl ?? null,
    status,
    /*
     * 목록 응답에는 시작가가 없다. 아직 입찰이 없으면 현재가가 곧 시작가이므로
     * 그 값을 쓴다. 목업으로 두면 **판매자가 방금 입력한 시작가와 다른 숫자**가
     * 카드에 뜬다(시작 전 카드는 이 값을 "시작가"로 그린다).
     */
    startPrice: detail.startingPrice ?? dto.currentPrice ?? fallback.startPrice,
    currentPrice: dto.currentPrice ?? fallback.currentPrice,
    bidUnit: detail.bidIncrement ?? fallback.bidUnit,
    leaderboard,
    /*
     * 최고 입찰자는 리더보드 1등이다. 서버가 리더보드를 준 이상 별도 필드가
     * 없어도 유도할 수 있고, 입찰이 없으면 `null` 이라 화면이 "입찰자 없음"을
     * 그린다. 리더보드와 같은 출처를 쓰므로 둘이 어긋날 수 없다.
     */
    topBidderNickname: leaderboard[0]?.nickname ?? null,
    /*
     * 서버는 타임존 오프셋이 붙은 문자열을 준다(예: "...+09:00", 이슈 #183).
     * `new Date()` 가 오프셋을 그대로 읽으므로 서버·브라우저 시간대가 달라도
     * 카운트다운이 맞는다.
     */
    endsAt: dto.endAt ?? fallback.endsAt,
    /*
     * 낙찰·유찰은 status 에서 사라지므로 따로 남긴다. 이게 없으면 화면이 "닫혔으면
     * 낙찰"로 단정해, 입찰이 한 번도 없던 물품에 낙찰가가 붙는다.
     */
    sold: dto.status === 'SOLD',
  }
}

/** 목록 응답을 화면 배열로 바꾼다. */
export function toAuctionItems(
  dtos: AuctionItemSummaryResponseDto[],
  myNickname: string | null = null,
): AuctionItemDetail[] {
  return dtos.map((dto, index) =>
    toAuctionItemDetail(dto, fallbackItem(index), myNickname),
  )
}

/**
 * 층으로 띄운 물품 상세에 상세 응답을 얹는다.
 *
 * **상세 응답은 목록에 없는 필드만 채운다.** 실시간으로 바뀌는 값(현재가·
 * 리더보드·최고 입찰자·마감 시각·상태)은 `live` 가 원본이다.
 *
 * 상세는 물품을 열 때 한 번만 부르고, 남의 입찰로는 다시 부르지 않는다. 그래서
 * `toAuctionItemDetail` 을 쓰면 SSE 로 갱신한 값을 낡은 응답이 덮어써서 상세가
 * 실시간으로 안 바뀐다 — 목록은 바뀌는데 상세만 멈춘 것처럼 보였다.
 */
export function mergeItemDetail(
  dto: AuctionItemDetailResponseDto,
  live: AuctionItemDetail,
): AuctionItemDetail {
  return {
    ...live,
    description: dto.description ?? live.description,
    // 목록 값으로 되돌리지 않는다. 목록에는 링크가 없어서 목업만 남는다.
    productUrl: dto.referenceUrl ?? null,
    bidUnit: dto.bidIncrement ?? live.bidUnit,
    /*
     * 목록에서 온 `startPrice` 는 진행 중인 물품이면 현재가라 틀리다
     * (목록 응답에 시작가가 없어 현재가로 대신한다). 상세 값이 있으면 그게 맞다.
     */
    startPrice: dto.startingPrice ?? live.startPrice,
  }
}
