package com.hot6ix.upbid.domain.auction.dto.response;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.global.common.ServerTime;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * @param leaderboard 상위 3명. 입찰이 없으면 빈 목록이다
 */
public record AuctionItemDetailResponseDto(
        Long auctionItemId,
        Long auctionRoomId,
        String productName,
        String description,
        String imageUrl,
        String referenceUrl,
        Long startingPrice,
        Long currentPrice,
        Long bidIncrement,
        AuctionItemStatus status,
        OffsetDateTime endAt,
        List<LeaderboardEntryResponseDto> leaderboard
) {
    /** 쿼리 전용 생성자. JPQL이 바인딩하는 타입이라 파라미터는 LocalDateTime 그대로 둔다. */
    public AuctionItemDetailResponseDto(
            Long auctionItemId, Long auctionRoomId, String productName, String description,
            String imageUrl, String referenceUrl, Long startingPrice, Long currentPrice,
            Long bidIncrement, AuctionItemStatus status, LocalDateTime endAt) {
        this(auctionItemId, auctionRoomId, productName, description, imageUrl, referenceUrl,
                startingPrice, currentPrice, bidIncrement, status, ServerTime.toOffset(endAt), List.of());
    }

    /**
     * 조회는 JPQL 프로젝션으로 이 DTO를 직접 만들지만, 물품을 갓 추가한 직후처럼 엔티티가
     * 이미 손에 있을 때는 이쪽을 쓴다.
     *
     * <p>리더보드는 빈 목록이다. 조회를 생략하는 게 아니라 실제로 비어 있다 — 이 팩토리를
     * 쓰는 {@code add}는 방금 만든 READY 물품이고, {@code start}는 READY 물품만 시작할 수
     * 있는데 READY 물품에는 입찰이 들어올 수 없다({@code BidService.validateBiddable}).
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
                auctionItem.getStartingPrice(),
                auctionItem.getCurrentPrice(),
                auctionItem.getBidIncrement(),
                auctionItem.getStatus(),
                ServerTime.toOffset(auctionItem.getEndAt()),
                List.of());
    }

    /** 쿼리가 만든 DTO에 리더보드만 갈아 끼운다. record라 새 인스턴스를 만든다. */
    public AuctionItemDetailResponseDto withLeaderboard(List<LeaderboardEntryResponseDto> leaderboard) {
        return new AuctionItemDetailResponseDto(
                auctionItemId, auctionRoomId, productName, description, imageUrl, referenceUrl,
                startingPrice, currentPrice, bidIncrement, status, endAt, leaderboard);
    }
}
