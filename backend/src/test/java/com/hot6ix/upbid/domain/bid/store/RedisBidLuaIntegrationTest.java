package com.hot6ix.upbid.domain.bid.store;

import static org.assertj.core.api.Assertions.assertThat;

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

class RedisBidLuaIntegrationTest extends AbstractRedisContainerTest {

    private static final long ITEM_ID = 101L;
    private static final long ROOM_ID = 202L;
    private static final long BIDDER_ID = 11L;

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
        redis.delete(List.of(
                AuctionRedisKeys.item(ITEM_ID),
                AuctionRedisKeys.participants(ROOM_ID),
                AuctionRedisKeys.accepted(ITEM_ID),
                AuctionRedisKeys.stream()));
        store = new AuctionRedisStore(redis);
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
        assertThat(redis.opsForHash().hasKey(AuctionRedisKeys.accepted(ITEM_ID), "request-1"))
                .isTrue();
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
                        "extendedSeconds", "0")));
    }

    @Test
    @DisplayName("같은 requestId 재시도는 첫 승인 결과를 돌려주고 상태와 Stream을 다시 쓰지 않는다")
    void duplicateRequestReturnsFirstResultWithoutAnotherEvent() {

        long endAt = System.currentTimeMillis() + 300_000L;
        store.seed(seed(endAt, 0, 60, 60));
        RedisBidDecision first =
                store.evaluateBid(ITEM_ID, "request-1", BIDDER_ID, 10_000L, System.currentTimeMillis());

        RedisBidDecision retried =
                store.evaluateBid(ITEM_ID, "request-1", BIDDER_ID, 20_000L, System.currentTimeMillis());

        RedisBidDecision.Accepted firstAccepted = (RedisBidDecision.Accepted) first;
        assertThat(retried).isEqualTo(new RedisBidDecision.Accepted(
                firstAccepted.requestId(),
                firstAccepted.acceptedAtMillis(),
                firstAccepted.endAtMillis(),
                firstAccepted.extendedSeconds(),
                true));
        assertThat(redis.opsForHash().get(AuctionRedisKeys.item(ITEM_ID), "currentPrice"))
                .isEqualTo("10000");
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
        assertThat(redis.hasKey(AuctionRedisKeys.accepted(ITEM_ID))).isFalse();
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
        return new AuctionRedisSeed(
                ITEM_ID,
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
