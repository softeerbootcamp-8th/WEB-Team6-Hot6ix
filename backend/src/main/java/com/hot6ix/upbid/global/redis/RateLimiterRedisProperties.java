package com.hot6ix.upbid.global.redis;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Rate limiter 전용 Redis 연결 설정.
 *
 * <p>일반 애플리케이션 연결(마감 예약, ShedLock)은 {@code spring.data.redis.timeout}(5s)을
 * 쓰고, 이 값은 그보다 훨씬 짧게 잡아 Redis 장애 시 요청이 오래 붙들리지 않게 한다.
 * {@link RateLimiterRedisClient} 참고.
 */
@ConfigurationProperties(prefix = "upbid.rate-limit.redis")
public record RateLimiterRedisProperties(
        Duration commandTimeout
) {
}
