package com.hot6ix.upbid.domain.bid.dto.response;

import com.hot6ix.upbid.domain.bid.entity.Bid;
import com.hot6ix.upbid.global.common.ServerTime;
import java.time.OffsetDateTime;

public record BidCreateResponseDto(
        Long bidId,
        Long auctionItemId,
        Long amount,
        OffsetDateTime acceptedAt
) {

    /**
     * 저장된 입찰을 응답으로 변환한다.
     * {@code auctionItem}은 지연 로딩 프록시여도 식별자만 꺼내므로 추가 조회가 없다.
     */
    public static BidCreateResponseDto from(Bid bid) {
        return new BidCreateResponseDto(
                bid.getBidId(),
                bid.getAuctionItem().getAuctionItemId(),
                bid.getAmount(),
                ServerTime.toOffset(bid.getAcceptedAt()));
    }
}
