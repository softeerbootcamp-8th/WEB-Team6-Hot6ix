import type { AuctionRoomPublicResponseDto } from '@/api/generated/model'
import type {
  AuctionItemDetail,
  AuctionRoomDetail,
  RoomStatus,
} from '@/mocks/types'

/**
 * 서버 경매방 DTO 를 화면이 쓰는 `AuctionRoomDetail` 로 바꾼다.
 *
 * 물품 배열은 별도 API(`GET .../auction-items`)에서 오므로 밖에서 받아 끼운다.
 * 방 응답에는 물품이 담기지 않는다.
 *
 * 화면 타입을 서버 타입으로 갈아치우지 않는 이유는 `adapt-item.ts` 와 같다 —
 * 이 타입이 라이브·종료 화면 여러 컴포넌트에 박혀 있어서, 여기 한 곳에서
 * 바꿔 주는 편이 영향 범위가 훨씬 작다.
 */

/** 서버 상태 3가지를 화면 상태로 옮긴다. 첫 물품이 시작되면 서버가 OPEN 으로 바꾼다. */
function toRoomStatus(status: string | undefined): RoomStatus {
  switch (status) {
    case 'OPEN':
      return 'LIVE'
    case 'CLOSED':
      return 'CLOSED'
    default:
      return 'READY'
  }
}

export function toAuctionRoomDetail(
  dto: AuctionRoomPublicResponseDto,
  items: AuctionItemDetail[],
): AuctionRoomDetail {
  return {
    id: dto.auctionRoomId ?? 0,
    title: dto.name ?? '',
    description: dto.description ?? '',
    sellerName: dto.sellerStoreName ?? '',
    status: toRoomStatus(dto.status),
    /*
     * 방을 만든 사람이면 판매자다. 서버가 조회자를 보고 판정해 준다.
     * **화면 표시용이고 권한의 근거가 아니다** — 실제 권한은 조작 API 가 다시 본다.
     */
    role: dto.isOwner ? 'SELLER' : 'BUYER',
    // 참여자 집계는 아직 서버가 채우지 않아 늘 0 이다(#117).
    participantCount: dto.participantCount ?? 0,
    /*
     * 공유 코드는 이 응답에 없다. 공유 패널이 전용 API 를 따로 부르므로
     * 화면에서 쓰이지 않는다.
     */
    shareCode: '',
    softCloseSeconds: dto.softCloseExtendSeconds ?? 0,
    softCloseTriggerSeconds: dto.softCloseTriggerSeconds ?? 0,
    bidUnit: dto.bidIncrement ?? 0,
    items,
  }
}
