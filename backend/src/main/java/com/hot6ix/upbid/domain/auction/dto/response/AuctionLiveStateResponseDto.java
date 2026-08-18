package com.hot6ix.upbid.domain.auction.dto.response;

import com.hot6ix.upbid.domain.auction.realtime.BidderKeyEncoder;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisLiveState;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** 진행 중 또는 MySQL 마감 저장 대기 물품의 Redis 기준 공개 상태. */
public record AuctionLiveStateResponseDto(
        Long auctionItemId,
        AuctionLiveStatus status,
        Long currentPrice,
        LocalDateTime endAt,
        Long revision,
        List<AuctionLiveLeaderboardEntryResponseDto> leaderboard
) {

    public AuctionLiveStateResponseDto {
        leaderboard = List.copyOf(leaderboard);
    }

    public static AuctionLiveStateResponseDto from(
            AuctionRedisLiveState state,
            Long viewerUserId,
            BidderKeyEncoder bidderKeyEncoder,
            ZoneId zoneId) {

        List<AuctionLiveLeaderboardEntryResponseDto> leaderboard = java.util.stream.IntStream
                .range(0, state.leaderboard().size())
                .mapToObj(index -> {
                    var entry = state.leaderboard().get(index);
                    return new AuctionLiveLeaderboardEntryResponseDto(
                            index + 1,
                            bidderKeyEncoder.encode(state.roomId(), entry.bidderUserId()),
                            entry.nickname(),
                            entry.amount(),
                            viewerUserId != null && viewerUserId == entry.bidderUserId());
                })
                .toList();

        return new AuctionLiveStateResponseDto(
                state.itemId(),
                AuctionLiveStatus.valueOf(state.status().name()),
                state.currentPrice(),
                LocalDateTime.ofInstant(Instant.ofEpochMilli(state.endAtMillis()), zoneId),
                state.revision(),
                leaderboard);
    }

}
