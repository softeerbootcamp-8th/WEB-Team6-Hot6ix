package com.hot6ix.upbid.domain.auction.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.bid.store.RedisBidDecision;
import com.hot6ix.upbid.global.support.AbstractRedisContainerTest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.hot6ix.upbid.domain.bid.stream.BidStreamMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class RedisCloseLuaIntegrationTest extends AbstractRedisContainerTest {

    private static final long ITEM_ID = 701L;
    private static final long ROOM_ID = 702L;
    private static final long SELLER_ID = 703L;
    private static final long BIDDER_ID = 704L;

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;
    private AuctionRedisStore store;

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = redisConnectionFactory();
        redis = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void tearDownRedis() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        redis.delete(List.of(AuctionRedisKeys.item(ITEM_ID),
                AuctionRedisKeys.participants(ROOM_ID), AuctionRedisKeys.stream()));
        store = new AuctionRedisStore(redis, new BidStreamMetrics(new SimpleMeterRegistry()));
    }

    @Test
    @DisplayName("마감 시각이 지나면 CLOSING 상태와 ITEM_CLOSING 스냅샷을 원자적으로 남긴다")
    void closesDueItemWithSnapshot() {
        long endAt = System.currentTimeMillis() - 1_000L;
        seed(endAt);

        RedisCloseDecision decision = store.requestNaturalClose(ITEM_ID, System.currentTimeMillis());

        assertThat(decision).isInstanceOf(RedisCloseDecision.Closing.class);
        assertThat(redis.opsForHash().get(AuctionRedisKeys.item(ITEM_ID), "status"))
                .isEqualTo("CLOSING");
        assertThat(streamRecords()).singleElement().satisfies(record ->
                assertThat(record.getValue()).containsAllEntriesOf(Map.of(
                        "type", "ITEM_CLOSING",
                        "itemId", String.valueOf(ITEM_ID),
                        "roomId", String.valueOf(ROOM_ID),
                        "currentPrice", "10000",
                        "leaderUserId", "",
                        "endAt", String.valueOf(endAt),
                        "totalExtensionSeconds", "0",
                        "closeReason", "NATURAL")));
    }

    @Test
    @DisplayName("낡은 예약은 마감하지 않고 Redis의 최신 endAt을 돌려준다")
    void rejectsNaturalCloseBeforeLatestEndAt() {
        long endAt = System.currentTimeMillis() + 60_000L;
        seed(endAt);

        RedisCloseDecision decision = store.requestNaturalClose(ITEM_ID, System.currentTimeMillis());

        assertThat(decision).isEqualTo(new RedisCloseDecision.Rejected(
                RedisCloseDecision.Reason.NOT_DUE, endAt));
        assertThat(redis.opsForHash().get(AuctionRedisKeys.item(ITEM_ID), "status"))
                .isEqualTo("IN_PROGRESS");
        assertThat(streamRecords()).isEmpty();
    }

    @Test
    @DisplayName("판매자가 아니면 마감 앞당기기를 거절하고 Hash와 Stream을 바꾸지 않는다")
    void rejectsAdvanceByNonOwner() {
        long endAt = System.currentTimeMillis() + 600_000L;
        seed(endAt);

        RedisCloseDecision decision = store.requestSellerAdvance(ITEM_ID, SELLER_ID + 1);

        assertThat(decision).isEqualTo(new RedisCloseDecision.Rejected(
                RedisCloseDecision.Reason.NOT_OWNER, endAt));
        assertThat(redis.opsForHash().get(AuctionRedisKeys.item(ITEM_ID), "endAt"))
                .isEqualTo(String.valueOf(endAt));
        assertThat(streamRecords()).isEmpty();
    }

    @Test
    @DisplayName("판매자 앞당기기는 IN_PROGRESS를 유지하고 DB 반영 이벤트를 남긴다")
    void advancesEndAtWithoutClosing() {
        seed(System.currentTimeMillis() + 600_000L);

        RedisCloseDecision decision = store.requestSellerAdvance(ITEM_ID, SELLER_ID);

        assertThat(decision).isInstanceOfSatisfying(RedisCloseDecision.Advanced.class, advanced -> {
            assertThat(advanced.remainingSeconds()).isEqualTo(60);
            assertThat(advanced.endAtMillis() - advanced.advancedAtMillis()).isEqualTo(60_000L);
        });
        assertThat(redis.opsForHash().get(AuctionRedisKeys.item(ITEM_ID), "status"))
                .isEqualTo("IN_PROGRESS");
        assertThat(streamRecords()).singleElement().satisfies(record ->
                assertThat(record.getValue().get("type")).isEqualTo("ITEM_CLOSE_ADVANCED"));
    }

    @Test
    @DisplayName("입찰과 자연 마감이 경합해도 Stream 순서와 승인 결과가 모순되지 않는다")
    void serializesBidAndNaturalClose() throws Exception {
        for (int attempt = 0; attempt < 20; attempt++) {
            int requestNumber = attempt;
            redis.delete(List.of(AuctionRedisKeys.item(ITEM_ID), AuctionRedisKeys.bidRequest("request-" + requestNumber),
                    AuctionRedisKeys.participants(ROOM_ID), AuctionRedisKeys.stream()));
            long dueAt = System.currentTimeMillis();
            seed(dueAt);
            CountDownLatch start = new CountDownLatch(1);

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                Future<RedisBidDecision> bid = executor.submit(() -> {
                    start.await();
                    return store.evaluateBid(ITEM_ID, "request-" + requestNumber, BIDDER_ID,
                            10_000L, dueAt - 1);
                });
                Future<RedisCloseDecision> close = executor.submit(() -> {
                    start.await();
                    return store.requestNaturalClose(ITEM_ID, dueAt);
                });
                start.countDown();

                RedisBidDecision bidResult = bid.get();
                RedisCloseDecision closeResult = close.get();
                List<MapRecord<String, Object, Object>> records = streamRecords();
                if (bidResult instanceof RedisBidDecision.Accepted) {
                    assertThat(closeResult).isInstanceOfSatisfying(
                            RedisCloseDecision.Rejected.class,
                            rejected -> assertThat(rejected.reason())
                                    .isEqualTo(RedisCloseDecision.Reason.NOT_DUE));
                    assertThat(records).extracting(record -> record.getValue().get("type"))
                            .containsExactly("BID_ACCEPTED");
                } else {
                    assertThat(closeResult).isInstanceOf(RedisCloseDecision.Closing.class);
                    assertThat(records).extracting(record -> record.getValue().get("type"))
                            .containsExactly("ITEM_CLOSING");
                }
            }
        }
    }

    private void seed(long endAt) {
        store.seed(new AuctionRedisSeed(
                ITEM_ID, ROOM_ID, SELLER_ID, AuctionItemStatus.IN_PROGRESS,
                10_000L, 10_000L, null, 1_000L, endAt,
                60, 60, 0, 3_600, List.of(BIDDER_ID)));
    }

    private List<MapRecord<String, Object, Object>> streamRecords() {
        return redis.opsForStream().range(AuctionRedisKeys.stream(), Range.unbounded());
    }
}
