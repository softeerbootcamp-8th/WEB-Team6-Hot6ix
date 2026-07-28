package com.hot6ix.upbid.domain.auction.dto.response;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import java.time.LocalDateTime;

public record AuctionItemSummaryResponseDto(
        Long auctionItemId,
        String productName,
        String imageUrl,
        Long currentPrice,
        AuctionItemStatus status,
        LocalDateTime endAt
) {
}
