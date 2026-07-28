package com.hot6ix.upbid.domain.auction.dto.response;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.product.entity.Product;
import java.time.LocalDateTime;

public record AuctionItemSummaryResponseDto(
        Long auctionItemId,
        String productName,
        String imageUrl,
        Long currentPrice,
        AuctionItemStatus status,
        LocalDateTime endAt
) {

    public static AuctionItemSummaryResponseDto from(AuctionItem auctionItem) {
        Product product = auctionItem.getProduct();
        return new AuctionItemSummaryResponseDto(
                auctionItem.getAuctionItemId(),
                product.getName(),
                product.getImageUrl(),
                auctionItem.getCurrentPrice(),
                auctionItem.getStatus(),
                auctionItem.getEndAt()
        );
    }
}
