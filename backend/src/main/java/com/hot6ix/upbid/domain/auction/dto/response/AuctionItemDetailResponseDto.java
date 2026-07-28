package com.hot6ix.upbid.domain.auction.dto.response;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.product.entity.Product;
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

    public static AuctionItemDetailResponseDto from(AuctionItem auctionItem) {
        Product product = auctionItem.getProduct();
        return new AuctionItemDetailResponseDto(
                auctionItem.getAuctionItemId(),
                auctionItem.getAuctionRoom().getAuctionRoomId(),
                product.getName(),
                product.getDescription(),
                product.getImageUrl(),
                product.getReferenceUrl(),
                auctionItem.getCurrentPrice(),
                auctionItem.getBidIncrement(),
                auctionItem.getStatus(),
                auctionItem.getEndAt()
        );
    }
}
