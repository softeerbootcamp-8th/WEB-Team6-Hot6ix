package com.hot6ix.upbid.domain.bid.dto.response;

import com.hot6ix.upbid.domain.bid.store.RedisBidDecision;
import com.hot6ix.upbid.domain.auction.realtime.BidderKeyEncoder;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Redis가 승인한 입찰 결과. MySQL 비동기 저장 완료 여부와는 무관하다. */
public record BidCreateResponseDto(
        String requestId,
        Long auctionItemId,
        Long amount,
        LocalDateTime acceptedAt,
        LocalDateTime endAt,
        Integer extendedSeconds,
        Long revision,
        String bidderKey
) {

    public static BidCreateResponseDto from(
            Long auctionItemId,
            RedisBidDecision.Accepted accepted,
            ZoneId zoneId,
            BidderKeyEncoder bidderKeyEncoder) {

        return new BidCreateResponseDto(
                accepted.requestId(),
                auctionItemId,
                accepted.amount(),
                LocalDateTime.ofInstant(Instant.ofEpochMilli(accepted.acceptedAtMillis()), zoneId),
                LocalDateTime.ofInstant(Instant.ofEpochMilli(accepted.endAtMillis()), zoneId),
                accepted.extendedSeconds(),
                accepted.revision(),
                bidderKeyEncoder.encode(accepted.roomId(), accepted.bidderUserId()));
    }
}
