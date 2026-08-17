package com.hot6ix.upbid.domain.auction.realtime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.store.AuctionRedisKeys;
import com.hot6ix.upbid.domain.auction.store.RedisCloseDecision;
import com.hot6ix.upbid.domain.bid.store.RedisBidDecision;
import com.hot6ix.upbid.domain.sse.dto.BidPlacedDto;
import com.hot6ix.upbid.domain.sse.dto.SoftCloseExtendedDto;
import com.hot6ix.upbid.domain.sse.dto.ItemCloseAdvancedDto;
import com.hot6ix.upbid.domain.sse.dto.ItemEndedDto;
import com.hot6ix.upbid.domain.sse.event.SseEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuctionRealtimeSsePublisherTest {

    private static final long ITEM_ID = 101L;
    private static final long ROOM_ID = 202L;
    private static final long BIDDER_ID = 11L;
    private static final long END_AT = 1_786_000_030_000L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Mock
    private SseEventPublisher sseEventPublisher;

    @Mock
    private BidderKeyEncoder bidderKeyEncoder;

    private AuctionRealtimeSsePublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new AuctionRealtimeSsePublisher(
                sseEventPublisher,
                bidderKeyEncoder,
                Clock.fixed(Instant.parse("2026-08-17T00:00:00Z"), ZONE));
    }

    @Test
    @DisplayName("입찰 승인 스냅샷이 아직 최신이면 기존 BID_PLACED DTO로 발행한다")
    void publishesAcceptedBidWhenPriceAndLeaderStillMatch() {

        RedisBidDecision.Accepted accepted = accepted(0);
        when(bidderKeyEncoder.encode(ROOM_ID, BIDDER_ID)).thenReturn("bidder-a");

        publisher.publishAccepted(ITEM_ID, accepted);

        verify(sseEventPublisher).publishIfHashMatches(
                "BID_PLACED",
                ROOM_ID,
                AuctionRedisKeys.item(ITEM_ID),
                Map.of(
                        "status", "IN_PROGRESS",
                        "currentPrice", "12000",
                        "leaderUserId", "11",
                        "revision", "1"),
                new BidPlacedDto(
                        ITEM_ID, "한정판 피규어", 12_000L, "한기", "bidder-a", 1L));
    }

    @Test
    @DisplayName("입찰로 Soft Close가 발생하면 최신 endAt과 맞을 때만 연장 이벤트를 발행한다")
    void publishesSoftCloseWhenEndAtStillMatches() {

        RedisBidDecision.Accepted accepted = accepted(30);
        when(bidderKeyEncoder.encode(ROOM_ID, BIDDER_ID)).thenReturn("bidder-a");

        publisher.publishAccepted(ITEM_ID, accepted);

        verify(sseEventPublisher).publishIfHashMatches(
                "SOFT_CLOSE_EXTENDED",
                ROOM_ID,
                AuctionRedisKeys.item(ITEM_ID),
                Map.of(
                        "status", "IN_PROGRESS",
                        "endAt", String.valueOf(END_AT),
                        "revision", "1"),
                new SoftCloseExtendedDto(
                        ITEM_ID,
                        "한정판 피규어",
                        30,
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(END_AT), ZONE),
                        1L));
    }

    private static RedisBidDecision.Accepted accepted(int extendedSeconds) {
        return new RedisBidDecision.Accepted(
                "request-1",
                ROOM_ID,
                "한정판 피규어",
                BIDDER_ID,
                "한기",
                12_000L,
                1_786_000_000_123L,
                END_AT,
                extendedSeconds,
                1L,
                false);
    }

    @Test
    @DisplayName("판매자 앞당기기 결과는 최신 endAt과 맞을 때 기존 DTO로 발행한다")
    void publishesSellerAdvanceWhenEndAtStillMatches() {
        RedisCloseDecision.Advanced advanced = new RedisCloseDecision.Advanced(
                ROOM_ID, ITEM_ID, "한정판 피규어", END_AT, 60, END_AT - 60_000L, 2L);

        publisher.publishAdvanced(advanced);

        verify(sseEventPublisher).publishIfHashMatches(
                "ITEM_CLOSE_ADVANCED",
                ROOM_ID,
                AuctionRedisKeys.item(ITEM_ID),
                Map.of(
                        "status", "IN_PROGRESS",
                        "endAt", String.valueOf(END_AT),
                        "revision", "2"),
                new ItemCloseAdvancedDto(
                        ITEM_ID,
                        "한정판 피규어",
                        60,
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(END_AT), ZONE),
                        2L));
    }

    @Test
    @DisplayName("자연 마감 결과는 CLOSING 스냅샷과 맞을 때 ITEM_ENDED로 발행한다")
    void publishesEndedItemWhenClosingSnapshotStillMatches() {
        RedisCloseDecision.Closing closing = new RedisCloseDecision.Closing(
                ROOM_ID, ITEM_ID, "한정판 피규어", 12_000L,
                BIDDER_ID, "한기", END_AT, END_AT + 10L, 3L);
        when(bidderKeyEncoder.encode(ROOM_ID, BIDDER_ID)).thenReturn("bidder-a");

        publisher.publishClosing(closing);

        verify(sseEventPublisher).publishIfHashMatches(
                "ITEM_ENDED",
                ROOM_ID,
                AuctionRedisKeys.item(ITEM_ID),
                Map.of(
                        "status", "CLOSING",
                        "currentPrice", "12000",
                        "leaderUserId", "11",
                        "endAt", String.valueOf(END_AT),
                        "revision", "3"),
                new ItemEndedDto(
                        ITEM_ID, "한정판 피규어", 12_000L, "한기", "bidder-a", 3L));
    }

    @Test
    @DisplayName("유찰도 기존 프론트 계약대로 ITEM_ENDED와 빈 낙찰 정보로 발행한다")
    void publishesPassedItemAsItemEndedWithoutWinner() {
        RedisCloseDecision.Closing closing = new RedisCloseDecision.Closing(
                ROOM_ID, ITEM_ID, "한정판 피규어", 10_000L,
                null, null, END_AT, END_AT + 10L, 3L);

        publisher.publishClosing(closing);

        verify(sseEventPublisher).publishIfHashMatches(
                "ITEM_ENDED",
                ROOM_ID,
                AuctionRedisKeys.item(ITEM_ID),
                Map.of(
                        "status", "CLOSING",
                        "currentPrice", "10000",
                        "leaderUserId", "",
                        "endAt", String.valueOf(END_AT),
                        "revision", "3"),
                new ItemEndedDto(ITEM_ID, "한정판 피규어", null, null, null, 3L));
    }
}
