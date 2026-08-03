package com.hot6ix.upbid.domain.deal.dto.response;

import java.util.List;

/**
 * 판매자가 보는 경매방 하나의 거래 현황.
 *
 * <p>마감된 물품만 담는다. 아직 시작하지 않았거나 진행 중인 물품은 거래가 시작된 적이 없어
 * 현황에 올릴 것이 없다 — {@code GET /api/v1/deals}의 판매 쪽과 같은 기준이다.
 */
public record AuctionRoomDealStatusResponseDto(
        Long auctionRoomId,
        String name,
        List<AuctionItemDealStatusResponseDto> items
) {
}
