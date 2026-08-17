package com.hot6ix.upbid.domain.auction.store;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import java.util.List;

/** 진행 중 또는 영속화 대기 물품을 Redis에서 한 시점으로 읽은 내부 라이브 상태. */
public record AuctionRedisLiveState(
        long itemId,
        long roomId,
        AuctionItemStatus status,
        long currentPrice,
        Long leaderUserId,
        long endAtMillis,
        int totalExtensionSeconds,
        long revision,
        List<AuctionRedisLeaderboardEntry> leaderboard
) {

    public AuctionRedisLiveState {
        leaderboard = List.copyOf(leaderboard);
    }
}
