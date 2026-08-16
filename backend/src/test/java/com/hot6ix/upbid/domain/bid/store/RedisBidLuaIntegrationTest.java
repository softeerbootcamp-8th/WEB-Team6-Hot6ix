package com.hot6ix.upbid.domain.bid.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisKeys;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisSeed;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisStore;
import com.hot6ix.upbid.global.support.AbstractRedisContainerTest;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.hot6ix.upbid.domain.bid.stream.BidStreamMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class RedisBidLuaIntegrationTest extends AbstractRedisContainerTest {

    private static final long ITEM_ID = 101L;
    private static final long OTHER_ITEM_ID = 102L;
    private static final long ROOM_ID = 202L;
    private static final long BIDDER_ID = 11L;

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    private AuctionRedisStore store;
    private SimpleMeterRegistry meterRegistry;

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
        redis.delete(List.of(
                AuctionRedisKeys.item(ITEM_ID),
                AuctionRedisKeys.item(OTHER_ITEM_ID),
                AuctionRedisKeys.participants(ROOM_ID),
                AuctionRedisKeys.bidRequest("request-1"),
                AuctionRedisKeys.stream()));
        meterRegistry = new SimpleMeterRegistry();
        store = new AuctionRedisStore(redis, new BidStreamMetrics(meterRegistry));
    }

    @Test
    @DisplayName("승인된 입찰은 Hash 갱신과 결과 캐시와 Stream 기록을 한 번에 남긴다")
    void acceptedBidUpdatesStateAndAppendsEvent() {

        long endAt = System.currentTimeMillis() + 300_000L;
        store.seed(seed(endAt, 0, 60, 60));
        long before = redisTimeMillis();

        RedisBidDecision decision =
                store.evaluateBid(ITEM_ID, "request-1", BIDDER_ID, 10_000L, before);

        long after = redisTimeMillis();
        assertThat(decision).isInstanceOfSatisfying(RedisBidDecision.Accepted.class, accepted -> {
            assertThat(accepted.requestId()).isEqualTo("request-1");
            assertThat(accepted.acceptedAtMillis()).isBetween(before, after);
            assertThat(accepted.endAtMillis()).isEqualTo(endAt);
            assertThat(accepted.extendedSeconds()).isZero();
            assertThat(accepted.duplicate()).isFalse();
        });
        assertThat(redis.opsForHash().get(AuctionRedisKeys.item(ITEM_ID), "currentPrice"))
                .isEqualTo("10000");
        assertThat(redis.opsForHash().get(AuctionRedisKeys.item(ITEM_ID), "leaderUserId"))
                .isEqualTo("11");
        assertThat(redis.opsForHash().entries(AuctionRedisKeys.bidRequest("request-1")))
                .containsAllEntriesOf(java.util.Map.of(
                        "itemId", "101",
                        "bidderUserId", "11",
                        "amount", "10000"));
        assertThat(redis.opsForStream().size(AuctionRedisKeys.stream())).isEqualTo(1L);
        assertThat(redis.opsForStream().range(AuctionRedisKeys.stream(), Range.unbounded()))
                .singleElement()
                .satisfies(record -> assertThat(record.getValue()).containsAllEntriesOf(java.util.Map.of(
                        "type", "BID_ACCEPTED",
                        "requestId", "request-1",
                        "itemId", "101",
                        "roomId", "202",
                        "bidderUserId", "11",
                        "amount", "10000",
                        "acceptedAt", String.valueOf(((RedisBidDecision.Accepted) decision).acceptedAtMillis()),
                        "endAt", String.valueOf(endAt),
                        "extendedSeconds", "0",
                        "totalExtensionSeconds", "0")));
    }

    @Test
    @DisplayName("같은 requestId와 같은 요청 내용의 재시도는 첫 승인 결과를 돌려주고 다시 쓰지 않는다")
    void duplicateRequestReturnsFirstResultWithoutAnotherEvent() {

        long endAt = System.currentTimeMillis() + 300_000L;
        store.seed(seed(endAt, 0, 60, 60));
        RedisBidDecision first =
                store.evaluateBid(ITEM_ID, "request-1", BIDDER_ID, 10_000L, System.currentTimeMillis());

        RedisBidDecision retried =
                store.evaluateBid(ITEM_ID, "request-1", BIDDER_ID, 10_000L, System.currentTimeMillis());

        RedisBidDecision.Accepted firstAccepted = (RedisBidDecision.Accepted) first;
        assertThat(retried).isEqualTo(new RedisBidDecision.Accepted(
                firstAccepted.requestId(),
                10_000L,
                firstAccepted.acceptedAtMillis(),
                firstAccepted.endAtMillis(),
                firstAccepted.extendedSeconds(),
                true));
        assertThat(redis.opsForHash().get(AuctionRedisKeys.item(ITEM_ID), "currentPrice"))
                .isEqualTo("10000");
        assertThat(redis.opsForStream().size(AuctionRedisKeys.stream())).isEqualTo(1L);
    }

    @Test
    @DisplayName("같은 requestId를 다른 금액에 재사용하면 멱등 키 충돌로 거절한다")
    void rejectsRequestIdReusedWithDifferentAmount() {

        long endAt = System.currentTimeMillis() + 300_000L;
        store.seed(seed(ITEM_ID, endAt, 0, 60, 60));
        store.evaluateBid(ITEM_ID, "request-1", BIDDER_ID, 10_000L, System.currentTimeMillis());

        RedisBidDecision retried =
                store.evaluateBid(ITEM_ID, "request-1", BIDDER_ID, 20_000L, System.currentTimeMillis());

        assertThat(retried).isInstanceOfSatisfying(RedisBidDecision.Rejected.class,
                rejected -> assertThat(rejected.reason().name()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT"));
        assertThat(redis.opsForHash().get(AuctionRedisKeys.item(ITEM_ID), "currentPrice"))
                .isEqualTo("10000");
        assertThat(redis.opsForStream().size(AuctionRedisKeys.stream())).isEqualTo(1L);
    }

    @Test
    @DisplayName("같은 requestId를 다른 입찰자가 재사용하면 멱등 키 충돌로 거절한다")
    void rejectsRequestIdReusedByDifferentBidder() {

        long endAt = System.currentTimeMillis() + 300_000L;
        store.seed(seed(ITEM_ID, endAt, 0, 60, 60));
        redis.opsForSet().add(AuctionRedisKeys.participants(ROOM_ID), "12");
        store.evaluateBid(ITEM_ID, "request-1", BIDDER_ID, 10_000L, System.currentTimeMillis());

        RedisBidDecision retried =
                store.evaluateBid(ITEM_ID, "request-1", 12L, 10_000L, System.currentTimeMillis());

        assertThat(retried).isInstanceOfSatisfying(RedisBidDecision.Rejected.class,
                rejected -> assertThat(rejected.reason())
                        .isEqualTo(RedisBidDecision.Reason.IDEMPOTENCY_KEY_CONFLICT));
        assertThat(redis.opsForHash().get(AuctionRedisKeys.item(ITEM_ID), "leaderUserId"))
                .isEqualTo("11");
        assertThat(redis.opsForStream().size(AuctionRedisKeys.stream())).isEqualTo(1L);
    }

    @Test
    @DisplayName("같은 requestId를 다른 물품에 재사용하면 전역 멱등 키 충돌로 거절한다")
    void rejectsRequestIdReusedForDifferentItem() {

        long endAt = System.currentTimeMillis() + 300_000L;
        store.seed(seed(ITEM_ID, endAt, 0, 60, 60));
        store.seed(seed(OTHER_ITEM_ID, endAt, 0, 60, 60));
        store.evaluateBid(ITEM_ID, "request-1", BIDDER_ID, 10_000L, System.currentTimeMillis());

        RedisBidDecision retried =
                store.evaluateBid(OTHER_ITEM_ID, "request-1", BIDDER_ID, 10_000L, System.currentTimeMillis());

        assertThat(retried).isInstanceOfSatisfying(RedisBidDecision.Rejected.class,
                rejected -> assertThat(rejected.reason().name()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT"));
        assertThat(redis.opsForHash().get(AuctionRedisKeys.item(OTHER_ITEM_ID), "leaderUserId"))
                .isNull();
        assertThat(redis.opsForStream().size(AuctionRedisKeys.stream())).isEqualTo(1L);
    }

    @Test
    @DisplayName("거절된 입찰은 물품 Hash와 승인 결과와 Stream을 바꾸지 않는다")
    void rejectionHasNoSideEffect() {

        long endAt = System.currentTimeMillis() + 300_000L;
        store.seed(seed(endAt, 0, 60, 60));

        RedisBidDecision decision =
                store.evaluateBid(ITEM_ID, "request-low", BIDDER_ID, 9_000L, System.currentTimeMillis());

        assertThat(decision).isEqualTo(
                new RedisBidDecision.Rejected(RedisBidDecision.Reason.BID_AMOUNT_TOO_LOW));
        assertThat(redis.opsForHash().get(AuctionRedisKeys.item(ITEM_ID), "currentPrice"))
                .isEqualTo("10000");
        assertThat(redis.opsForHash().get(AuctionRedisKeys.item(ITEM_ID), "leaderUserId"))
                .isNull();
        assertThat(redis.hasKey(AuctionRedisKeys.bidRequest("request-low"))).isFalse();
        assertThat(redis.hasKey(AuctionRedisKeys.stream())).isFalse();
    }

    @Test
    @DisplayName("Stream 키 타입이 잘못되면 물품 Hash를 바꾸기 전에 실패한다")
    void failsBeforeMutationWhenStreamKeyHasWrongType() {

        long endAt = System.currentTimeMillis() + 300_000L;
        store.seed(seed(endAt, 0, 60, 60));
        redis.opsForValue().set(AuctionRedisKeys.stream(), "wrong-type");

        assertThatThrownBy(() -> store.evaluateBid(
                ITEM_ID, "request-1", BIDDER_ID, 10_000L, System.currentTimeMillis()))
                .isInstanceOf(RuntimeException.class);

        assertThat(redis.opsForHash().get(AuctionRedisKeys.item(ITEM_ID), "currentPrice"))
                .isEqualTo("10000");
        assertThat(redis.opsForHash().get(AuctionRedisKeys.item(ITEM_ID), "leaderUserId"))
                .isNull();
        assertThat(redis.hasKey(AuctionRedisKeys.bidRequest("request-1"))).isFalse();
        assertThat(meterRegistry.find("upbid.bid.lua.failures")
                .tag("stage", "execution").counter())
                .isNotNull()
                .extracting(counter -> counter.count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("숫자 물품 필드가 손상되면 상태와 Stream을 바꾸기 전에 실패한다")
    void failsBeforeMutationWhenNumericItemFieldIsCorrupted() {

        long endAt = System.currentTimeMillis() + 300_000L;
        store.seed(seed(endAt, 0, 60, 60));
        redis.opsForHash().put(
                AuctionRedisKeys.item(ITEM_ID), "softCloseTriggerSeconds", "not-a-number");

        assertThatThrownBy(() -> store.evaluateBid(
                ITEM_ID, "request-1", BIDDER_ID, 10_000L, System.currentTimeMillis()))
                .isInstanceOf(RuntimeException.class);

        assertThat(redis.opsForHash().get(AuctionRedisKeys.item(ITEM_ID), "leaderUserId"))
                .isNull();
        assertThat(redis.hasKey(AuctionRedisKeys.bidRequest("request-1"))).isFalse();
        assertThat(redis.hasKey(AuctionRedisKeys.stream())).isFalse();
    }

    @Test
    @DisplayName("기존 승인 결과의 숫자 fingerprint가 손상되면 충돌 응답 대신 실패한다")
    void failsWhenCachedRequestFingerprintIsCorrupted() {

        long endAt = System.currentTimeMillis() + 300_000L;
        store.seed(seed(endAt, 0, 60, 60));
        redis.opsForHash().putAll(AuctionRedisKeys.bidRequest("request-1"), java.util.Map.of(
                "itemId", "101",
                "bidderUserId", "not-a-number",
                "amount", "10000",
                "acceptedAt", "1000",
                "endAt", String.valueOf(endAt),
                "extendedSeconds", "0"));

        assertThatThrownBy(() -> store.evaluateBid(
                ITEM_ID, "request-1", BIDDER_ID, 10_000L, System.currentTimeMillis()))
                .isInstanceOf(RuntimeException.class);

        assertThat(redis.opsForHash().get(AuctionRedisKeys.item(ITEM_ID), "leaderUserId"))
                .isNull();
        assertThat(redis.hasKey(AuctionRedisKeys.stream())).isFalse();
    }

    @Test
    @DisplayName("Redis 승인 시각이 임박 구간 안이면 설정된 폭만큼 Soft Close한다")
    void extendsWhenRedisTimeIsInsideSoftCloseWindow() {

        long endAt = System.currentTimeMillis() + 30_000L;
        store.seed(seed(endAt, 0, 60, 60));

        RedisBidDecision decision =
                store.evaluateBid(ITEM_ID, "request-extend", BIDDER_ID, 10_000L, System.currentTimeMillis());

        assertThat(decision).isInstanceOfSatisfying(RedisBidDecision.Accepted.class, accepted -> {
            assertThat(accepted.endAtMillis()).isEqualTo(endAt + 60_000L);
            assertThat(accepted.extendedSeconds()).isEqualTo(60);
        });
        assertThat(redis.opsForHash().get(AuctionRedisKeys.item(ITEM_ID), "totalExtensionSeconds"))
                .isEqualTo("60");
    }

    @Test
    @DisplayName("Redis 승인 시각이 임박 구간 전이면 Soft Close하지 않는다")
    void doesNotExtendBeforeSoftCloseWindow() {

        long endAt = System.currentTimeMillis() + 120_000L;
        store.seed(seed(endAt, 0, 60, 60));

        RedisBidDecision decision =
                store.evaluateBid(ITEM_ID, "request-early", BIDDER_ID, 10_000L, System.currentTimeMillis());

        assertThat(decision).isInstanceOfSatisfying(RedisBidDecision.Accepted.class, accepted -> {
            assertThat(accepted.endAtMillis()).isEqualTo(endAt);
            assertThat(accepted.extendedSeconds()).isZero();
        });
    }

    @Test
    @DisplayName("이번 연장으로 누적 상한을 넘으면 Soft Close하지 않는다")
    void doesNotExtendBeyondMaximumTotalExtension() {

        long endAt = System.currentTimeMillis() + 30_000L;
        store.seed(seed(endAt, 3_550, 60, 60));

        RedisBidDecision decision =
                store.evaluateBid(ITEM_ID, "request-cap", BIDDER_ID, 10_000L, System.currentTimeMillis());

        assertThat(decision).isInstanceOfSatisfying(RedisBidDecision.Accepted.class, accepted -> {
            assertThat(accepted.endAtMillis()).isEqualTo(endAt);
            assertThat(accepted.extendedSeconds()).isZero();
        });
        assertThat(redis.opsForHash().get(AuctionRedisKeys.item(ITEM_ID), "totalExtensionSeconds"))
                .isEqualTo("3550");
    }

    @Test
    @DisplayName("이번 연장으로 누적 상한에 정확히 도달하면 Soft Close한다")
    void extendsUpToMaximumTotalExtension() {

        long endAt = System.currentTimeMillis() + 30_000L;
        store.seed(seed(endAt, 3_540, 60, 60));

        RedisBidDecision decision =
                store.evaluateBid(ITEM_ID, "request-exact-cap", BIDDER_ID, 10_000L, System.currentTimeMillis());

        assertThat(decision).isInstanceOfSatisfying(RedisBidDecision.Accepted.class, accepted -> {
            assertThat(accepted.endAtMillis()).isEqualTo(endAt + 60_000L);
            assertThat(accepted.extendedSeconds()).isEqualTo(60);
        });
        assertThat(redis.opsForHash().get(AuctionRedisKeys.item(ITEM_ID), "totalExtensionSeconds"))
                .isEqualTo("3600");
    }

    @Test
    @DisplayName("Soft Close 설정이 없으면 입찰은 승인하되 마감을 연장하지 않는다")
    void acceptsWithoutExtensionWhenSoftCloseIsDisabled() {

        long endAt = System.currentTimeMillis() + 30_000L;
        store.seed(seed(endAt, 0, null, null));

        RedisBidDecision decision =
                store.evaluateBid(ITEM_ID, "request-no-soft-close", BIDDER_ID, 10_000L,
                        System.currentTimeMillis());

        assertThat(decision).isInstanceOfSatisfying(RedisBidDecision.Accepted.class, accepted -> {
            assertThat(accepted.endAtMillis()).isEqualTo(endAt);
            assertThat(accepted.extendedSeconds()).isZero();
        });
    }

    @Test
    @DisplayName("도착 시각이 기존 마감 시각과 같으면 입찰을 거절한다")
    void rejectsWhenArrivedAtEqualsEndAt() {

        long endAt = System.currentTimeMillis() + 300_000L;
        store.seed(seed(endAt, 0, 60, 60));

        RedisBidDecision decision =
                store.evaluateBid(ITEM_ID, "request-deadline", BIDDER_ID, 10_000L, endAt);

        assertThat(decision).isEqualTo(
                new RedisBidDecision.Rejected(RedisBidDecision.Reason.ITEM_CLOSED));
    }

    private static AuctionRedisSeed seed(long endAtMillis, int totalExtensionSeconds,
                                         Integer triggerSeconds, Integer extendSeconds) {
        return seed(ITEM_ID, endAtMillis, totalExtensionSeconds, triggerSeconds, extendSeconds);
    }

    private static AuctionRedisSeed seed(long itemId, long endAtMillis, int totalExtensionSeconds,
                                         Integer triggerSeconds, Integer extendSeconds) {
        return new AuctionRedisSeed(
                itemId,
                ROOM_ID,
                303L,
                AuctionItemStatus.IN_PROGRESS,
                10_000L,
                10_000L,
                null,
                1_000L,
                endAtMillis,
                triggerSeconds,
                extendSeconds,
                totalExtensionSeconds,
                3_600,
                List.of(BIDDER_ID));
    }

    private static long redisTimeMillis() {
        Long time = redis.execute((RedisCallback<Long>) connection ->
                connection.serverCommands().time(TimeUnit.MILLISECONDS));
        if (time == null) {
            throw new IllegalStateException("Redis TIME이 null을 반환했다");
        }
        return time;
    }
}
