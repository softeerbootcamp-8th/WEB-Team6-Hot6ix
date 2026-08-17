package com.hot6ix.upbid.domain.auction.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.bid.store.RedisBidDecision;
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
    private static final String PARTICIPANT_NICKNAMES_KEY =
            "auction:room:202:participant-nicknames";

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
        redis.delete(List.of(ITEM_KEY, PARTICIPANTS_KEY, PARTICIPANT_NICKNAMES_KEY));
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
                .contains("11", "12");
    }

    @Test
    @DisplayName("이미 공개된 물품을 다시 seed해도 Redis의 최신 입찰 상태를 덮어쓰지 않는다")
    void seedDoesNotOverwriteExistingBidState() {

        assertThat(store.seed(seed(List.of(11L)))).isTrue();
        redis.opsForHash().put(ITEM_KEY, "currentPrice", "15000");
        redis.opsForHash().put(ITEM_KEY, "leaderUserId", "11");
        redis.opsForHash().delete(ITEM_KEY, "itemName");
        redis.delete(PARTICIPANT_NICKNAMES_KEY);

        boolean createdAgain = store.seed(seed(List.of(11L, 12L)));

        assertThat(createdAgain).isFalse();
        assertThat(redis.opsForHash().get(ITEM_KEY, "currentPrice")).isEqualTo("15000");
        assertThat(redis.opsForHash().get(ITEM_KEY, "leaderUserId")).isEqualTo("11");
        assertThat(redis.opsForHash().get(ITEM_KEY, "itemName")).isEqualTo("한정판 피규어");
        assertThat(redis.opsForSet().members(PARTICIPANTS_KEY))
                .contains("11", "12");
        assertThat(redis.opsForHash().entries(PARTICIPANT_NICKNAMES_KEY))
                .containsAllEntriesOf(Map.of("11", "user-11", "12", "user-12"));
    }

    @Test
    @DisplayName("정상 Seed를 다시 요청해도 새 DB 스냅샷의 참여자를 다시 넣지 않는다")
    void seedSkipsParticipantRewriteWhenStateIsReady() {

        assertThat(store.seed(seed(List.of(11L)))).isTrue();

        boolean createdAgain = store.seed(seed(List.of(11L, 12L)));

        assertThat(createdAgain).isFalse();
        assertThat(redis.opsForSet().members(PARTICIPANTS_KEY)).contains("11").doesNotContain("12");
    }

    @Test
    @DisplayName("동의 참여자가 없어도 참여자 Seed 준비 상태를 Redis에 남긴다")
    void seedMarksEmptyParticipantsAsReady() {

        assertThat(store.seed(seed(List.of()))).isTrue();

        assertThat(redis.hasKey(PARTICIPANTS_KEY)).isTrue();
        assertThat(redis.hasKey(PARTICIPANT_NICKNAMES_KEY)).isTrue();
        assertThat(store.isSeedReady(ITEM_ID, ROOM_ID)).isTrue();
    }

    @Test
    @DisplayName("초기화 표시자가 실제 입찰 참여자를 대신하지 않는다")
    void seedMarkerDoesNotAuthorizeBidder() {

        assertThat(store.seed(seed(List.of()))).isTrue();

        RedisBidDecision decision = store.evaluateBid(ITEM_ID, "request-1", 11L, 10_000L);

        assertThat(decision).isEqualTo(
                new RedisBidDecision.Rejected(RedisBidDecision.Reason.TERMS_NOT_AGREED));
    }

    @Test
    @DisplayName("참여자 추가로 키만 생기고 Seed 표시자가 없으면 복구 대상으로 판단한다")
    void participantKeysWithoutSeedMarkerAreNotReady() {

        redis.opsForHash().putAll(ITEM_KEY, Map.of(
                "status", "IN_PROGRESS",
                "itemName", "한정판 피규어"));
        redis.opsForSet().add(PARTICIPANTS_KEY, "11");
        redis.opsForHash().put(PARTICIPANT_NICKNAMES_KEY, "11", "user-11");

        assertThat(store.isSeedReady(ITEM_ID, ROOM_ID)).isFalse();
    }

    @Test
    @DisplayName("itemName이 없는 구버전 물품 Hash는 Seed 복구 대상으로 판단한다")
    void missingItemNameIsNotReady() {

        assertThat(store.seed(seed(List.of(11L)))).isTrue();
        redis.opsForHash().delete(ITEM_KEY, "itemName");

        // 준비됨으로 보면 Seed 복구가 조기 반환해서 아래 보충이 영영 안 돈다. 그동안 그 물품은
        // 입찰도 마감도 Lua 에서 itemName 없음으로 실패한다 (#374).
        assertThat(store.isSeedReady(ITEM_ID, ROOM_ID)).isFalse();
    }

    @Test
    @DisplayName("itemName이 없는 물품 Hash를 다시 Seed하면 입찰 상태는 그대로 두고 itemName만 채운다")
    void seedBackfillsMissingItemNameOnly() {

        assertThat(store.seed(seed(List.of(11L)))).isTrue();
        redis.opsForHash().delete(ITEM_KEY, "itemName");
        redis.opsForHash().put(ITEM_KEY, "currentPrice", "99000");

        assertThat(store.seed(seed(List.of(11L)))).isFalse();

        assertThat(redis.opsForHash().get(ITEM_KEY, "itemName")).isEqualTo("한정판 피규어");
        assertThat(redis.opsForHash().get(ITEM_KEY, "currentPrice")).isEqualTo("99000");
        assertThat(store.isSeedReady(ITEM_ID, ROOM_ID)).isTrue();
    }

    @Test
    @DisplayName("진행 중 물품의 마감 시각을 한 번에 읽고 Hash가 없는 물품은 빼고 돌려준다")
    void readsEndAtMillisInBulk() {

        assertThat(store.seed(seed(List.of(11L)))).isTrue();

        assertThat(store.findEndAtMillis(List.of(ITEM_ID, ITEM_ID + 1)))
                .containsOnlyKeys(ITEM_ID);
        assertThat(store.findEndAtMillis(List.of())).isEmpty();
    }

    @Test
    @DisplayName("참여자 nickname Hash가 유실되면 Seed 복구 대상으로 판단한다")
    void missingParticipantNicknamesIsNotReady() {

        assertThat(store.seed(seed(List.of(11L)))).isTrue();
        redis.delete(PARTICIPANT_NICKNAMES_KEY);

        assertThat(store.isSeedReady(ITEM_ID, ROOM_ID)).isFalse();
    }

    @Test
    @DisplayName("물품 Seed 키 타입이 잘못되면 준비 상태로 숨기지 않고 실패한다")
    void seedReadinessRejectsInvalidItemKeyType() {

        redis.opsForValue().set(ITEM_KEY, "invalid");

        assertThatThrownBy(() -> store.isSeedReady(ITEM_ID, ROOM_ID))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("참여자 Seed 키 타입이 잘못되면 준비 상태로 숨기지 않고 실패한다")
    void seedReadinessRejectsInvalidParticipantsKeyType() {

        redis.opsForHash().put(ITEM_KEY, "status", "IN_PROGRESS");
        redis.opsForValue().set(PARTICIPANTS_KEY, "invalid");

        assertThatThrownBy(() -> store.isSeedReady(ITEM_ID, ROOM_ID))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("물품 Hash가 이미 있어도 누락된 참여자 Set은 DB 스냅샷으로 다시 채운다")
    void seedRestoresMissingParticipantsWithoutOverwritingBidState() {

        assertThat(store.seed(seed(List.of(11L, 12L)))).isTrue();
        redis.opsForHash().put(ITEM_KEY, "currentPrice", "15000");
        redis.opsForHash().put(ITEM_KEY, "leaderUserId", "11");
        redis.delete(PARTICIPANTS_KEY);

        boolean createdAgain = store.seed(seed(List.of(11L, 12L)));

        assertThat(createdAgain).isFalse();
        assertThat(redis.opsForSet().members(PARTICIPANTS_KEY))
                .contains("11", "12");
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
                "한정판 피규어",
                participantUserIds.stream()
                        .map(userId -> new AuctionRedisParticipant(userId, "user-" + userId))
                        .toList());
    }
}
