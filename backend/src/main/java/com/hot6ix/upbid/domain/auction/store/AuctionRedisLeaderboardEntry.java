package com.hot6ix.upbid.domain.auction.store;

/** Redis 물품 리더보드의 입찰자별 최고 금액. 사용자 ID는 외부 응답에 노출하지 않는다. */
public record AuctionRedisLeaderboardEntry(
        long bidderUserId,
        String nickname,
        long amount
) {

    public AuctionRedisLeaderboardEntry {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("리더보드 nickname은 비어 있을 수 없다");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("리더보드 amount는 음수일 수 없다");
        }
    }
}
