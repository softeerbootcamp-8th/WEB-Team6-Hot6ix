package com.hot6ix.upbid.domain.auction.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.global.support.AbstractRedisContainerTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.hot6ix.upbid.domain.bid.stream.BidStreamMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class AuctionRedisStoreTest extends AbstractRedisContainerTest {

    private static final long ITEM_ID = 101L;
    private static final long ROOM_ID = 202L;
    private static final String ITEM_KEY = "auction:item:101";
    private static final String PARTICIPANTS_KEY = "auction:room:202:participants";

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
    void clearKeys() {
        redis.delete(List.of(ITEM_KEY, PARTICIPANTS_KEY));
        store = new AuctionRedisStore(redis, new BidStreamMetrics(new SimpleMeterRegistry()));
    }

    @Test
    @DisplayName("물품 seed가 공개되면 입찰 판정에 필요한 Hash와 참여자 Set이 모두 준비돼 있다")
    void seedPublishesCompleteBidState() {

        boolean created = store.seed(seed(List.of(11L, 12L)));

        assertThat(created).isTrue();
        assertThat(redis.opsForHash().entries(ITEM_KEY)).containsAllEntriesOf(Map.ofEntries(
                Map.entry("roomId", "202"),
                Map.entry("sellerUserId", "303"),
                Map.entry("status", "IN_PROGRESS"),
                Map.entry("startingPrice", "10000"),
                Map.entry("currentPrice", "10000"),
                Map.entry("bidIncrement", "1000"),
                Map.entry("endAt", "1786636800000"),
                Map.entry("softCloseTriggerSeconds", "60"),
                Map.entry("softCloseExtendSeconds", "60"),
                Map.entry("totalExtensionSeconds", "0"),
                Map.entry("maxTotalExtensionSeconds", "3600")));
        assertThat(redis.opsForSet().members(PARTICIPANTS_KEY))
                .containsExactlyInAnyOrder("11", "12");
    }

    @Test
    @DisplayName("이미 공개된 물품을 다시 seed해도 Redis의 최신 입찰 상태를 덮어쓰지 않는다")
    void seedDoesNotOverwriteExistingBidState() {

        assertThat(store.seed(seed(List.of(11L)))).isTrue();
        redis.opsForHash().put(ITEM_KEY, "currentPrice", "15000");
        redis.opsForHash().put(ITEM_KEY, "leaderUserId", "11");

        boolean createdAgain = store.seed(seed(List.of(11L, 12L)));

        assertThat(createdAgain).isFalse();
        assertThat(redis.opsForHash().get(ITEM_KEY, "currentPrice")).isEqualTo("15000");
        assertThat(redis.opsForHash().get(ITEM_KEY, "leaderUserId")).isEqualTo("11");
    }

    private static AuctionRedisSeed seed(List<Long> participantUserIds) {
        return new AuctionRedisSeed(
                ITEM_ID,
                ROOM_ID,
                303L,
                AuctionItemStatus.IN_PROGRESS,
                10_000L,
                10_000L,
                null,
                1_000L,
                1_786_636_800_000L,
                60,
                60,
                0,
                3_600,
                participantUserIds);
    }
}
