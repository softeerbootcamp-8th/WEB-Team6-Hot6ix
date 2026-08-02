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
    /**
     * 조회는 JPQL 프로젝션으로 이 DTO를 직접 만들지만, 물품을 갓 추가한 직후처럼 엔티티가
     * 이미 손에 있을 때는 이쪽을 쓴다.
     */
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
                auctionItem.getEndAt());
    }
}
