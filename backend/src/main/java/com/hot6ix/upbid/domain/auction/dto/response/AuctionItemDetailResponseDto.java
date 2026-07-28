package com.hot6ix.upbid.domain.auction.dto.response;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import java.time.LocalDateTime;

public record AuctionItemDetailResponseDto(
        Long auctionItemId,
        Long auctionRoomId,
        String productName,
        String description,
        String imageUrl,
        String referenceUrl,
        Long currentPrice,
        Long bidIncrement,
        AuctionItemStatus status,
        LocalDateTime endAt
) {
}
