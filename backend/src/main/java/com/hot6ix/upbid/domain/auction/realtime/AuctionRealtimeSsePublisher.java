package com.hot6ix.upbid.domain.auction.realtime;

import com.hot6ix.upbid.domain.auction.store.AuctionRedisKeys;
import com.hot6ix.upbid.domain.auction.store.RedisCloseDecision;
import com.hot6ix.upbid.domain.bid.store.RedisBidDecision;
import com.hot6ix.upbid.domain.sse.dto.BidPlacedDto;
import com.hot6ix.upbid.domain.sse.dto.SoftCloseExtendedDto;
import com.hot6ix.upbid.domain.sse.dto.ItemCloseAdvancedDto;
import com.hot6ix.upbid.domain.sse.dto.ItemEndedDto;
import com.hot6ix.upbid.domain.sse.event.SseEventPublisher;
import com.hot6ix.upbid.global.event.EventType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Redis가 결정한 진행 중 경매 상태를 MySQL 커밋보다 먼저 SSE로 알린다. */
@Component
@RequiredArgsConstructor
public class AuctionRealtimeSsePublisher {

    private final SseEventPublisher sseEventPublisher;
    private final BidderKeyEncoder bidderKeyEncoder;
    private final Clock clock;

    /** 승인 직후 가격 이벤트와, 발생한 경우 Soft Close 이벤트를 최신 상태일 때만 발행한다. */
    public void publishAccepted(long itemId, RedisBidDecision.Accepted accepted) {
        String itemKey = AuctionRedisKeys.item(itemId);

        sseEventPublisher.publishIfHashMatches(
                EventType.BID_PLACED.name(),
                accepted.roomId(),
                itemKey,
                Map.of(
                        "status", "IN_PROGRESS",
                        "currentPrice", String.valueOf(accepted.amount()),
                        "leaderUserId", String.valueOf(accepted.bidderUserId()),
                        "revision", String.valueOf(accepted.revision())),
                new BidPlacedDto(
                        itemId,
                        accepted.itemName(),
                        accepted.amount(),
                        accepted.bidderNickname(),
                        bidderKeyEncoder.encode(accepted.roomId(), accepted.bidderUserId()),
                        accepted.revision()));

        if (accepted.extendedSeconds() <= 0) {
            return;
        }

        sseEventPublisher.publishIfHashMatches(
                EventType.SOFT_CLOSE_EXTENDED.name(),
                accepted.roomId(),
                itemKey,
                Map.of(
                        "status", "IN_PROGRESS",
                        "endAt", String.valueOf(accepted.endAtMillis()),
                        "revision", String.valueOf(accepted.revision())),
                new SoftCloseExtendedDto(
                        itemId,
                        accepted.itemName(),
                        accepted.extendedSeconds(),
                        toLocalDateTime(accepted.endAtMillis()),
                        accepted.revision()));
    }

    /** 판매자 마감 앞당기기를 Redis의 최신 endAt과 일치할 때만 알린다. */
    public void publishAdvanced(RedisCloseDecision.Advanced advanced) {
        sseEventPublisher.publishIfHashMatches(
                EventType.ITEM_CLOSE_ADVANCED.name(),
                advanced.roomId(),
                AuctionRedisKeys.item(advanced.itemId()),
                Map.of(
                        "status", "IN_PROGRESS",
                        "endAt", String.valueOf(advanced.endAtMillis()),
                        "revision", String.valueOf(advanced.revision())),
                new ItemCloseAdvancedDto(
                        advanced.itemId(),
                        advanced.itemName(),
                        advanced.remainingSeconds(),
                        toLocalDateTime(advanced.endAtMillis()),
                        advanced.revision()));
    }

    /** Redis가 CLOSING으로 전환한 최종 스냅샷을 기존 ITEM_ENDED 계약으로 알린다. */
    public void publishClosing(RedisCloseDecision.Closing closing) {
        boolean passed = closing.leaderUserId() == null;
        sseEventPublisher.publishIfHashMatches(
                EventType.ITEM_ENDED.name(),
                closing.roomId(),
                AuctionRedisKeys.item(closing.itemId()),
                Map.of(
                        "status", "CLOSING",
                        "currentPrice", String.valueOf(closing.currentPrice()),
                        "leaderUserId", passed ? "" : String.valueOf(closing.leaderUserId()),
                        "endAt", String.valueOf(closing.endAtMillis()),
                        "revision", String.valueOf(closing.revision())),
                new ItemEndedDto(
                        closing.itemId(),
                        closing.itemName(),
                        passed ? null : closing.currentPrice(),
                        closing.winnerNickname(),
                        passed ? null : bidderKeyEncoder.encode(
                                closing.roomId(), closing.leaderUserId()),
                        closing.revision()));
    }

    private LocalDateTime toLocalDateTime(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), clock.getZone());
    }
}
