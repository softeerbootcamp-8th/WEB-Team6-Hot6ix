package com.hot6ix.upbid.domain.auction.dto.response;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @param leaderboard 상위 3명. 입찰이 없으면 빈 목록이고 화면이 {@code -}로 채운다.
 *                    쿼리가 DTO를 바로 만드는 {@code findSummaries}에서는 채울 수 없어
 *                    (JPQL 생성자 표현식에 List를 넣지 못한다) Service가
 *                    {@link #withLeaderboard}로 붙인다
 */
public record AuctionItemSummaryResponseDto(
        Long auctionItemId,
        String productName,
        String imageUrl,
        Long currentPrice,
        AuctionItemStatus status,
        LocalDateTime endAt,
        List<LeaderboardEntryResponseDto> leaderboard
) {
    /** 쿼리 전용 생성자. 리더보드는 Service가 나중에 붙인다. */
    public AuctionItemSummaryResponseDto(
            Long auctionItemId, String productName, String imageUrl,
            Long currentPrice, AuctionItemStatus status, LocalDateTime endAt) {
        this(auctionItemId, productName, imageUrl, currentPrice, status, endAt, List.of());
    }

    /** 쿼리가 만든 DTO에 리더보드만 갈아 끼운다. record라 새 인스턴스를 만든다. */
    public AuctionItemSummaryResponseDto withLeaderboard(List<LeaderboardEntryResponseDto> leaderboard) {
        return new AuctionItemSummaryResponseDto(
                auctionItemId, productName, imageUrl, currentPrice, status, endAt, leaderboard);
    }
}
