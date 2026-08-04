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
 * 서버에는 아직 리더보드·입찰 이력·시작가·입찰 수가 없는데, 이 값들이 6개
 * 컴포넌트에 박혀 있다. 화면 타입을 서버에 맞추면 라이브 기능을 통째로 다시
 * 써야 하므로, 변환을 여기 한 곳에 몰아넣고 없는 값만 목업에서 빌려온다.
 *
 * ponytail: 임시물이다. #42(SSE)에서 리더보드·이력이 실제 값으로 채워지면
 * `fallback` 인자를 지우고 순수 변환 함수로 줄인다.
 *
 * 서버 `imageUrl` 은 버린다. 화면 타입에 사진 자리가 없고 `mocks/images.ts` 가
 * 물품 이름으로 사진을 고르기 때문이다. 이름은 서버 값이라 사진도 따라간다.
 * 사진을 서버 값으로 바꾸는 건 `API-INTEGRATION.md` 대응표에 남은 별도 작업이다.
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
 * 서버 리더보드를 화면 타입으로 바꾼다.
 *
 * `isMe` 는 서버에 없어서 닉네임으로 판단한다. 닉네임이 겹치면 남의 입찰이 내 것으로
 * 보일 수 있지만, 서버가 입찰자 ID 를 내려주기 전까지는 이게 가진 전부다.
 */
function toLeaderboard(
  entries: LeaderboardEntryResponseDto[] | undefined,
  viewerNickname: string | null,
): LeaderboardEntry[] {
  return (entries ?? []).map((entry, index) => ({
    rank: entry.rank ?? index + 1,
    nickname: entry.nickname ?? '',
    amount: entry.amount ?? 0,
    isMe: viewerNickname !== null && entry.nickname === viewerNickname,
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
  viewerNickname: string | null = null,
): AuctionItemDetail {
  // 상세 DTO 에만 있는 필드. 목록 응답이면 undefined 라 목업으로 떨어진다.
  const detail = dto as AuctionItemDetailResponseDto
  const leaderboard = toLeaderboard(dto.leaderboard, viewerNickname)

  return {
    ...fallback,
    id: dto.auctionItemId ?? fallback.id,
    roomId: detail.auctionRoomId ?? fallback.roomId,
    name: dto.productName ?? fallback.name,
    description: detail.description ?? fallback.description,
    productUrl: detail.referenceUrl ?? fallback.productUrl,
    status: toItemStatus(dto.status),
    /*
     * 목록 응답에는 시작가가 없다. 아직 입찰이 없으면 현재가가 곧 시작가이므로
     * 그 값을 쓴다. 목업으로 두면 **판매자가 방금 입력한 시작가와 다른 숫자**가
     * 카드에 뜬다(시작 전 카드는 이 값을 "시작가"로 그린다).
     */
    startPrice: detail.startingPrice ?? dto.currentPrice ?? fallback.startPrice,
    currentPrice: dto.currentPrice ?? fallback.currentPrice,
    bidUnit: detail.bidIncrement ?? fallback.bidUnit,
    /*
     * 서버는 오프셋 없는 LocalDateTime 을 준다. 서버와 브라우저의 시간대가
     * 다르면 카운트다운이 통째로 어긋난다. 지금은 둘 다 KST 라 맞지만,
     * 배포 서버가 UTC 로 돌면 여기서 티가 난다.
     */
    endsAt: dto.endAt ?? fallback.endsAt,
    /*
     * 낙찰·유찰은 status 에서 사라지므로 따로 남긴다. 이게 없으면 화면이 "닫혔으면
     * 낙찰"로 단정해, 입찰이 한 번도 없던 물품에 낙찰가가 붙는다.
     */
    sold: dto.status === 'SOLD',
    /*
     * 리더보드는 목업으로 떨어뜨리지 않는다. 서버가 빈 배열을 주는 것과 "아직 못
     * 받았다"를 구분할 수 없어서, 목업으로 메우면 입찰이 없는 물품에 남의 입찰이 뜬다.
     */
    leaderboard,
    topBidderNickname: leaderboard[0]?.nickname ?? null,
  }
}

/** 목록 응답을 화면 배열로 바꾼다. */
export function toAuctionItems(
  dtos: AuctionItemSummaryResponseDto[],
  viewerNickname: string | null = null,
): AuctionItemDetail[] {
  return dtos.map((dto, index) =>
    toAuctionItemDetail(dto, fallbackItem(index), viewerNickname),
  )
}
