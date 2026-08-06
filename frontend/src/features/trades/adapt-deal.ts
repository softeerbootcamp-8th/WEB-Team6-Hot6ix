import type {
  DealSummaryResponseDto,
  DealSummaryResponseDtoRole,
  DealSummaryResponseDtoStatus,
} from '@/api/generated/model'

/** 내가 판 것인지 산 것인지. 서버가 판정해서 내려준다. */
export type DealRole = DealSummaryResponseDtoRole

/**
 * 물품 하나의 거래가 어디까지 왔는지. 후보 한 명의 상태가 아니라 물품 단위이고,
 * 판매자와 구매자가 같은 값을 본다.
 *
 * 거래 없이 끝난 건이 둘로 갈린다. `UNSOLD` 는 유효한 입찰이 없어 후보가 아예 없는
 * 경우고, `ALL_FAILED` 는 후보가 있었는데 전부 실패한 경우다. 판매자가 취할 조치가
 * 달라서 서버가 상태로 구분해 준다 — 화면이 `partnerNickname` 으로 추측하지 않는다.
 */
export type DealItemStatus = DealSummaryResponseDtoStatus

/** 거래 내역 한 줄. */
export interface Deal {
  auctionItemId: number
  auctionRoomId: number | null
  /** 물품 상세를 부를 때 방을 지목하는 공개 식별자. 숫자 ID 로는 못 부른다. */
  shareCode: string | null
  auctionRoomName: string
  productName: string
  /** 판매자가 상품 사진을 안 올렸으면 없다. */
  imageUrl: string | null
  role: DealRole
  status: DealItemStatus
  /** 유찰이면 없다. */
  amount: number | null
  /** 유찰이거나 후보가 전원 실패해 상대가 없으면 없다. */
  partnerNickname: string | null
  /** 구매 건에서 판매자 연락처를 조회할 때 쓰는 키. 판매 건에는 없다. */
  sellerProfileId: number | null
}

/**
 * `GET /api/v1/deals` 응답을 화면이 바로 쓸 모양으로 좁힌다.
 *
 * DTO 필드가 전부 optional 인 건 springdoc 이 `required` 를 안 내려서다.
 * 서버는 항상 채워 주지만 타입만 봐서는 알 수 없으므로, 카드를 그리는 데 반드시
 * 필요한 세 값(물품 ID·역할·상태)이 없는 줄은 버린다. 없는 값을 기본값으로
 * 메우면 필터 건수와 배지가 조용히 틀린다.
 *
 * 정렬은 하지 않는다 — 서버가 최근 마감 순으로 준다.
 */
export function toDeals(dtos: DealSummaryResponseDto[] | undefined): Deal[] {
  return (dtos ?? []).flatMap((dto) => {
    if (dto.auctionItemId == null || dto.role == null || dto.status == null) {
      return []
    }

    return [
      {
        auctionItemId: dto.auctionItemId,
        auctionRoomId: dto.auctionRoomId ?? null,
        shareCode: dto.shareCode ?? null,
        auctionRoomName: dto.auctionRoomName ?? '',
        productName: dto.productName ?? '이름 없는 물품',
        imageUrl: dto.imageUrl ?? null,
        role: dto.role,
        status: dto.status,
        amount: dto.amount ?? null,
        partnerNickname: dto.partnerNickname ?? null,
        sellerProfileId: dto.sellerProfileId ?? null,
      },
    ]
  })
}
