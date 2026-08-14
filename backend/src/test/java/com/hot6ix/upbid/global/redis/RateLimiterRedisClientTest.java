package com.hot6ix.upbid.global.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hot6ix.upbid.global.support.AbstractRedisContainerTest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

class RateLimiterRedisClientTest extends AbstractRedisContainerTest {

    private static final RedisScript<String> SET_AND_GET_SCRIPT = new DefaultRedisScript<>(
            "redis.call('SET', KEYS[1], ARGV[1]) return redis.call('GET', KEYS[1])",
            String.class);

    /** Redis 가 한동안 다른 명령을 못 받게 만들어 커맨드 타임아웃을 실제로 재현한다. */
    private static final RedisScript<Long> SLEEP_SCRIPT = new DefaultRedisScript<>(
            "redis.call('DEBUG', 'SLEEP', ARGV[1]) return 1",
            Long.class);

    private static String host;
    private static int port;

    @BeforeAll
    static void resolveContainerAddress() {
        LettuceConnectionFactory probe = redisConnectionFactory();
        host = probe.getHostName();
        port = probe.getPort();
        probe.destroy();
    }

    private static DataRedisConnectionDetails connectionDetails() {
        return new DataRedisConnectionDetails() {
            @Override
            public Standalone getStandalone() {
                return Standalone.of(host, port);
            }
        };
    }

    @Test
    @DisplayName("스크립트를 정상 실행하고 결과를 돌려준다")
    void executesScript() {
        RateLimiterRedisClient client =
                new RateLimiterRedisClient(connectionDetails(), new RateLimiterRedisProperties(Duration.ofSeconds(1)));

        try {
            String key = "rate-limiter-client-test:" + UUID.randomUUID();
            String result = client.execute(SET_AND_GET_SCRIPT, List.of(key), "value");

            assertThat(result).isEqualTo("value");
        } finally {
            client.destroy();
        }
    }

    @Test
    @DisplayName("커맨드 타임아웃이 설정값만큼 짧게 적용된다")
    void commandTimeoutAppliesToSlowCommands() {
        RateLimiterRedisClient client =
                new RateLimiterRedisClient(connectionDetails(), new RateLimiterRedisProperties(Duration.ofMillis(100)));

        try {
            Instant start = Instant.now();

            assertThatThrownBy(() -> client.execute(SLEEP_SCRIPT, List.of(), "0.3"))
                    .isInstanceOf(DataAccessException.class);

            // Redis 가 실제로 잠드는 시간(1초)보다 훨씬 짧게 실패해야 커맨드 타임아웃이
            // 걸린 것이다. 넉넉히 잡아도 절반 미만이어야 한다.
            assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofMillis(500));
        } finally {
            client.destroy();
        }
    }
}
