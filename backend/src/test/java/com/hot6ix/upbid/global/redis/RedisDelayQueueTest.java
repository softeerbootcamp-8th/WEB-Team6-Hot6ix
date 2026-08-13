package com.hot6ix.upbid.global.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.global.support.AbstractRedisContainerTest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 실제 Redis 에 붙여 {@link RedisDelayQueue}를 검증한다. Lua 스크립트와 {@code ZADD NX} 같은
 * 것들이 이 클래스의 알맹이라, 가짜로 바꿔치면 검증할 것이 남지 않는다.
 *
 * <p>시각을 파라미터로 받는 덕분에 {@code Thread.sleep} 없이 "미뤄둔 시각이 지난 뒤"를 잰다.
 */
class RedisDelayQueueTest extends AbstractRedisContainerTest {

    private static final String KEY = "test:delay:due";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 12, 10, 0, 0);
    private static final Duration VISIBILITY = Duration.ofSeconds(30);
    private static final int LIMIT = 10;

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    private RedisDelayQueue delayQueue;

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = redisConnectionFactory();
        redisTemplate = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void tearDownRedis() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void clearQueue() {
        redisTemplate.delete(KEY);
        delayQueue = new RedisDelayQueue(redisTemplate, KEY);
    }

    @Test
    @DisplayName("실행 시각이 지난 예약만 집힌다")
    void claimsOnlyDueEntries() {
        delayQueue.schedule(1L, NOW.minusSeconds(1));
        delayQueue.schedule(2L, NOW.plusSeconds(1));

        List<DueEntry> claimed = delayQueue.claimDue(NOW, LIMIT, VISIBILITY);

        assertThat(claimed).extracting(DueEntry::id).containsExactly(1L);
    }

    @Test
    @DisplayName("집어온 예약에는 미루기 전의 실행 시각이 담긴다")
    void claimedEntryKeepsOriginalScheduledTime() {
        LocalDateTime scheduledFor = NOW.minusSeconds(5);
        delayQueue.schedule(1L, scheduledFor);

        List<DueEntry> claimed = delayQueue.claimDue(NOW, LIMIT, VISIBILITY);

        assertThat(claimed).singleElement()
                .extracting(DueEntry::scheduledFor)
                .isEqualTo(scheduledFor);
    }

    @Test
    @DisplayName("한 번 집은 예약은 미뤄둔 시간 동안 다시 집히지 않는다")
    void claimedEntryIsHiddenForVisibilityWindow() {
        delayQueue.schedule(1L, NOW.minusSeconds(1));

        delayQueue.claimDue(NOW, LIMIT, VISIBILITY);
        List<DueEntry> secondClaim = delayQueue.claimDue(NOW, LIMIT, VISIBILITY);

        assertThat(secondClaim).isEmpty();
    }

    @Test
    @DisplayName("처리하지 못한 채 미뤄둔 시간이 지나면 다시 집힌다")
    void claimedEntryReappearsAfterVisibilityWindow() {
        delayQueue.schedule(1L, NOW.minusSeconds(1));
        delayQueue.claimDue(NOW, LIMIT, VISIBILITY);

        List<DueEntry> reclaimed =
                delayQueue.claimDue(NOW.plus(VISIBILITY).plusSeconds(1), LIMIT, VISIBILITY);

        assertThat(reclaimed).extracting(DueEntry::id).containsExactly(1L);
    }

    @Test
    @DisplayName("처리를 끝내고 지운 예약은 미뤄둔 시간이 지나도 다시 집히지 않는다")
    void cancelledEntryDoesNotReappear() {
        delayQueue.schedule(1L, NOW.minusSeconds(1));
        delayQueue.claimDue(NOW, LIMIT, VISIBILITY);

        delayQueue.cancel(1L);

        assertThat(delayQueue.claimDue(NOW.plus(VISIBILITY).plusSeconds(1), LIMIT, VISIBILITY))
                .isEmpty();
    }

    @Test
    @DisplayName("미뤄둔 시각 그대로면 지운다")
    void cancelIfDeferredRemovesUntouchedEntry() {
        delayQueue.schedule(1L, NOW.minusSeconds(1));
        delayQueue.claimDue(NOW, LIMIT, VISIBILITY);

        assertThat(delayQueue.cancelIfDeferred(1L, NOW, VISIBILITY)).isTrue();

        assertThat(delayQueue.claimDue(NOW.plus(VISIBILITY).plusSeconds(1), LIMIT, VISIBILITY))
                .isEmpty();
    }

    @Test
    @DisplayName("처리하는 사이에 다시 예약됐으면 지우지 않는다")
    void cancelIfDeferredKeepsRescheduledEntry() {
        delayQueue.schedule(1L, NOW.minusSeconds(1));
        delayQueue.claimDue(NOW, LIMIT, VISIBILITY);

        // 알림을 발행하는 사이에 연장이 커밋돼 리스너가 새 알림 시각으로 걸어둔 상황이다.
        delayQueue.schedule(1L, NOW.plusSeconds(5));

        assertThat(delayQueue.cancelIfDeferred(1L, NOW, VISIBILITY)).isFalse();

        // 무조건 지웠으면 다음 재동기화까지 이 알림이 통째로 사라진다.
        assertThat(delayQueue.claimDue(NOW.plusSeconds(6), LIMIT, VISIBILITY))
                .extracting(DueEntry::id)
                .containsExactly(1L);
    }

    @Test
    @DisplayName("이미 지워진 예약에는 아무 일도 하지 않는다")
    void cancelIfDeferredIgnoresMissingEntry() {
        assertThat(delayQueue.cancelIfDeferred(1L, NOW, VISIBILITY)).isFalse();
    }

    @Test
    @DisplayName("같은 ID로 다시 예약하면 줄이 늘지 않고 시각만 바뀐다")
    void schedulingSameIdMovesTheEntryInstead()  {
        delayQueue.schedule(1L, NOW.minusSeconds(1));

        delayQueue.schedule(1L, NOW.plusSeconds(10));

        assertThat(delayQueue.claimDue(NOW, LIMIT, VISIBILITY)).isEmpty();
        assertThat(delayQueue.claimDue(NOW.plusSeconds(11), LIMIT, VISIBILITY))
                .extracting(DueEntry::id)
                .containsExactly(1L);
    }

    @Test
    @DisplayName("이미 있는 예약은 scheduleIfAbsent가 덮어쓰지 않는다")
    void scheduleIfAbsentKeepsExistingEntry() {
        delayQueue.schedule(1L, NOW.plusSeconds(10));

        boolean added = delayQueue.scheduleIfAbsent(1L, NOW.minusSeconds(1));

        assertThat(added).isFalse();
        assertThat(delayQueue.claimDue(NOW, LIMIT, VISIBILITY)).isEmpty();
    }

    @Test
    @DisplayName("예약이 없으면 scheduleIfAbsent가 새로 넣는다")
    void scheduleIfAbsentAddsMissingEntry() {
        boolean added = delayQueue.scheduleIfAbsent(1L, NOW.minusSeconds(1));

        assertThat(added).isTrue();
        assertThat(delayQueue.claimDue(NOW, LIMIT, VISIBILITY))
                .extracting(DueEntry::id)
                .containsExactly(1L);
    }

    @Test
    @DisplayName("집어온 예약은 미뤄둔 뒤라 scheduleIfAbsent가 원래 시각으로 되돌리지 못한다")
    void scheduleIfAbsentDoesNotUndoAClaim() {
        delayQueue.schedule(1L, NOW.minusSeconds(1));
        delayQueue.claimDue(NOW, LIMIT, VISIBILITY);

        delayQueue.scheduleIfAbsent(1L, NOW.minusSeconds(1));

        assertThat(delayQueue.claimDue(NOW, LIMIT, VISIBILITY)).isEmpty();
    }

    @Test
    @DisplayName("한 번에 집는 개수는 limit으로 제한되고 이른 것부터 나온다")
    void claimRespectsLimitAndOrder() {
        delayQueue.schedule(1L, NOW.minusSeconds(1));
        delayQueue.schedule(2L, NOW.minusSeconds(3));
        delayQueue.schedule(3L, NOW.minusSeconds(2));

        List<DueEntry> claimed = delayQueue.claimDue(NOW, 2, VISIBILITY);

        assertThat(claimed).extracting(DueEntry::id).containsExactly(2L, 3L);
    }

    @Test
    @DisplayName("밀린 예약 수는 실행 시각이 지난 것만 센다")
    void backlogCountsOnlyDueEntries() {
        delayQueue.schedule(1L, NOW.minusSeconds(1));
        delayQueue.schedule(2L, NOW.minusSeconds(2));
        delayQueue.schedule(3L, NOW.plusSeconds(1));

        assertThat(delayQueue.backlogSize(NOW)).isEqualTo(2);
    }

    @Test
    @DisplayName("집은 예약은 미뤄진 상태라 밀린 수에서 빠진다")
    void backlogExcludesClaimedEntries() {
        delayQueue.schedule(1L, NOW.minusSeconds(1));

        delayQueue.claimDue(NOW, LIMIT, VISIBILITY);

        assertThat(delayQueue.backlogSize(NOW)).isZero();
    }
}
