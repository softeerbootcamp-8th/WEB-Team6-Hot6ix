package com.hot6ix.upbid.domain.auction.store;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import java.util.List;

/** 물품 시작 직후 Redis 입찰 상태를 준비하는 데 필요한 불변 스냅샷. */
public record AuctionRedisSeed(
        long itemId,
        long roomId,
        long sellerUserId,
        AuctionItemStatus status,
        long startingPrice,
        long currentPrice,
        Long leaderUserId,
        long bidIncrement,
        long endAtMillis,
        Integer softCloseTriggerSeconds,
        Integer softCloseExtendSeconds,
        int totalExtensionSeconds,
        int maxTotalExtensionSeconds,
        String itemName,
        List<AuctionRedisParticipant> participants,
        List<AuctionRedisLeaderboardEntry> leaderboard
) {

    public AuctionRedisSeed {
        if (itemName == null || itemName.isBlank()) {
            throw new IllegalArgumentException("물품 itemName은 비어 있을 수 없다");
        }
        participants = List.copyOf(participants);
        leaderboard = List.copyOf(leaderboard);
    }

    public AuctionRedisSeed(
            long itemId,
            long roomId,
            long sellerUserId,
            AuctionItemStatus status,
            long startingPrice,
            long currentPrice,
            Long leaderUserId,
            long bidIncrement,
            long endAtMillis,
            Integer softCloseTriggerSeconds,
            Integer softCloseExtendSeconds,
            int totalExtensionSeconds,
            int maxTotalExtensionSeconds,
            String itemName,
            List<AuctionRedisParticipant> participants
    ) {
        this(itemId, roomId, sellerUserId, status, startingPrice, currentPrice, leaderUserId,
                bidIncrement, endAtMillis, softCloseTriggerSeconds, softCloseExtendSeconds,
                totalExtensionSeconds, maxTotalExtensionSeconds, itemName, participants, List.of());
    }
}
