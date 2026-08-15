package com.hot6ix.upbid.domain.bid.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.global.redis.RateLimiterRedisClient;
import com.hot6ix.upbid.global.redis.RateLimiterRedisProperties;
import com.hot6ix.upbid.global.support.AbstractRedisContainerTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

class RedisTokenBucketBidRateLimiterTest extends AbstractRedisContainerTest {

    private static String host;
    private static int port;

    private RateLimiterRedisClient redisClient;

    @BeforeAll
    static void resolveContainerAddress() {
        LettuceConnectionFactory probe = redisConnectionFactory();
        host = probe.getHostName();
        port = probe.getPort();
        probe.destroy();
    }

    @BeforeEach
    void setUp() {
        redisClient = new RateLimiterRedisClient(
                connectionDetails(host, port), new RateLimiterRedisProperties(Duration.ofSeconds(1)));
    }

    @AfterEach
    void tearDown() {
        redisClient.destroy();
    }

    private static DataRedisConnectionDetails connectionDetails(String host, int port) {
        return new DataRedisConnectionDetails() {
            @Override
            public Standalone getStandalone() {
                return Standalone.of(host, port);
            }
        };
    }

    private RedisTokenBucketBidRateLimiter limiterWith(int capacity, int refillPerSecond) {
        return new RedisTokenBucketBidRateLimiter(
                redisClient,
                new BidRateLimitProperties(true, capacity, refillPerSecond));
    }

    @Test
    @DisplayName("버킷 용량까지는 허용하고 그 다음은 거절한다")
    void allowsUpToCapacityThenDenies() {

        RedisTokenBucketBidRateLimiter limiter = limiterWith(3, 1);
        long userId = uniqueUserId();

        for (int i = 0; i < 3; i++) {
            assertThat(limiter.isRateLimited(userId)).isFalse();
        }

        assertThat(limiter.isRateLimited(userId)).isTrue();
    }

    @Test
    @DisplayName("리필 시간이 지나면 다시 허용한다")
    void allowsAgainAfterRefillWindow() throws InterruptedException {

        // capacity 1, 초당 5개 리필 → 토큰 1개당 200ms.
        RedisTokenBucketBidRateLimiter limiter = limiterWith(1, 5);
        long userId = uniqueUserId();

        assertThat(limiter.isRateLimited(userId)).isFalse();
        assertThat(limiter.isRateLimited(userId)).isTrue();

        Thread.sleep(250);

        assertThat(limiter.isRateLimited(userId)).isFalse();
    }

    @Test
    @DisplayName("Redis 장애 시 예외를 던지지 않고 즉시 허용한다")
    void failsOpenWhenRedisIsUnavailable() {

        RateLimiterRedisClient brokenClient = new RateLimiterRedisClient(
                connectionDetails("localhost", 1), new RateLimiterRedisProperties(Duration.ofMillis(200)));

        try {
            RedisTokenBucketBidRateLimiter limiter = new RedisTokenBucketBidRateLimiter(
                    brokenClient,
                    new BidRateLimitProperties(true, 5, 2));

            assertThat(limiter.isRateLimited(uniqueUserId())).isFalse();
        } finally {
            brokenClient.destroy();
        }
    }

    private long uniqueUserId() {
        return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    }
}
