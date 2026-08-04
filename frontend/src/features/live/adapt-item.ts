import type {
  AuctionItemDetailResponseDto,
  AuctionItemSummaryResponseDto,
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
 * **리더보드와 최고 입찰자는 목업을 쓰지 않는다.** 실제 입찰과 무관한 순위가
 * 화면에 남는 문제(#136) 때문에 서버 값만 쓰고, 없으면 비워 둔다.
 *
 * ponytail: 임시물이다. 남은 목업 필드가 실제 값으로 채워지면
 * (`history` 는 #134, `extended` 는 Soft Close 연장 로직) `fallback` 인자를
 * 지우고 순수 변환 함수로 줄인다.
 *
 * 서버 `imageUrl` 은 버린다. 화면 타입에 사진 자리가 없고 `mocks/images.ts` 가
 * 물품 이름으로 사진을 고르기 때문이다. 이름은 서버 값이라 사진도 따라간다.
 * 사진을 서버 값으로 바꾸는 건 `API-INTEGRATION.md` 대응표에 남은 별도 작업이다.
 */

/**
 * 서버는 #115 부터 리더보드를 내려주는데, 그 뒤로 `pnpm api:gen` 을 돌리지 않아
 * 생성된 DTO 타입에 필드가 없다. 생성물(`api/generated/**`)은 손대지 않으므로
 * 여기서만 좁게 선언한다.
 *
 * 모양은 백엔드 `LeaderboardEntryResponseDto`(rank·nickname·amount) 기준이다.
 * 재생성하면 이 타입과 아래 캐스팅을 지우고 `dto.leaderboard` 를 바로 읽는다.
 */
type LeaderboardCarrier = {
  leaderboard?: { rank: number; nickname: string; amount: number }[]
}

/** 서버 상태 4가지를 화면 상태 3가지로 좁힌다. 낙찰·유찰은 화면에선 둘 다 종료다. */
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
 */
function toLeaderboard(
  entries: LeaderboardCarrier['leaderboard'],
  myNickname: string | null,
): LeaderboardEntry[] {
  return (entries ?? []).map((entry) => ({
    rank: entry.rank,
    nickname: entry.nickname,
    amount: entry.amount,
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
  const leaderboard = toLeaderboard(
    (dto as LeaderboardCarrier).leaderboard,
    myNickname,
  )

  return {
    ...fallback,
    id: dto.auctionItemId ?? fallback.id,
    roomId: detail.auctionRoomId ?? fallback.roomId,
    name: dto.productName ?? fallback.name,
    description: detail.description ?? fallback.description,
    productUrl: detail.referenceUrl ?? fallback.productUrl,
    status,
    /*
     * 시작가는 상세 응답에만 있다. 목록 응답이면 아직 입찰이 없는 READY 상태에
     * 한해 현재가가 곧 시작가다. 진행·종료 물품은 현재가가 이미 올라가 있어
     * 시작가를 알 수 없으므로 목업을 쓴다.
     */
    startPrice:
      detail.startingPrice ??
      (status === 'READY' ? dto.currentPrice : undefined) ??
      fallback.startPrice,
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
     * 서버는 오프셋 없는 LocalDateTime 을 준다. 서버와 브라우저의 시간대가
     * 다르면 카운트다운이 통째로 어긋난다. 지금은 둘 다 KST 라 맞지만,
     * 배포 서버가 UTC 로 돌면 여기서 티가 난다.
     */
    endsAt: dto.endAt ?? fallback.endsAt,
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
