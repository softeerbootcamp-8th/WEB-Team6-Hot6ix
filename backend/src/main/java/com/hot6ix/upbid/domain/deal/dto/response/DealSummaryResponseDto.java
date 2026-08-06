package com.hot6ix.upbid.domain.deal.dto.response;

import com.hot6ix.upbid.domain.deal.entity.DealItemStatus;
import com.hot6ix.upbid.domain.deal.entity.DealRole;
import java.time.LocalDateTime;

/**
 * 거래 내역 한 줄.
 *
 * @param productId       판매 건만 값이 있다. 내가 산 물건은 내 상품이 아니다
 * @param imageUrl        상품 사진 주소. 판매자가 안 올렸으면 {@code null}
 * @param partnerNickname 판매 건이면 거래 상대 후보, 구매 건이면 판매자.
 *                        유찰이거나 후보가 전원 실패해 상대가 없으면 {@code null}
 * @param sellerProfileId 구매자가 판매자에게 연락할 때 쓰는 프로필 조회 키. 연락처를 목록에
 *                        싣지 않는 이유는 거래와 무관한 화면까지 개인 정보를 들고 다니게 되기
 *                        때문이다
 * @param shareCode       거래 상세 화면이 물품 상세를 부를 때 방을 지목하는 공개 식별자.
 *                        물품 상세는 공개 경로라 숫자 PK를 받지 않는다
 */
public record DealSummaryResponseDto(
        Long auctionItemId,
        Long auctionRoomId,
        String shareCode,
        Long productId,
        String productName,
        String imageUrl,
        String auctionRoomName,
        DealRole role,
        DealItemStatus status,
        Long amount,
        String partnerNickname,
        Long sellerProfileId,
        LocalDateTime closedAt
) {
}
