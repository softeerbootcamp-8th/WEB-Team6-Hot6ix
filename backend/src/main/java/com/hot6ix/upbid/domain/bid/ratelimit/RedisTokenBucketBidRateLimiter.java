package com.hot6ix.upbid.domain.bid.ratelimit;

import com.hot6ix.upbid.global.redis.RateLimiterRedisClient;
import io.lettuce.core.RedisException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis + Lua Token Bucket 으로 입찰 요청을 제한한다.
 *
 * <p><b>Redis 장애 시 즉시 허용(fail-open)한다.</b> 리미터는 보조 방어선일 뿐, 입찰의 실제
 * 규칙(금액·상태·중복)은 DB 행 락이 지킨다. 리미터가 죽었다고 경매를 멈출 이유가 없다.
 * {@link com.hot6ix.upbid.global.redis.RateLimiterRedisClient}가 커맨드 타임아웃을 짧게
 * 가져가므로, 이 fail-open 은 응답을 오래 기다린 뒤가 아니라 그 타임아웃만큼만 기다린 뒤
 * 즉시 일어난다.
 */
@Slf4j
@Component
@EnableConfigurationProperties(BidRateLimitProperties.class)
public class RedisTokenBucketBidRateLimiter implements BidRateLimiter {

    private static final RedisScript<Long> TOKEN_BUCKET_SCRIPT = createScript();

    private final RateLimiterRedisClient redisClient;
    private final BidRateLimitProperties properties;

    public RedisTokenBucketBidRateLimiter(
            RateLimiterRedisClient redisClient,
            BidRateLimitProperties properties) {
        this.redisClient = redisClient;
        this.properties = properties;
    }

    @Override
    public boolean isRateLimited(long userId) {

        if (!properties.enabled()) {
            return false;
        }

        String key = "rate-limit:bid:" + userId;
        long nowMillis = System.currentTimeMillis();

        try {
            Long allowed = redisClient.execute(
                    TOKEN_BUCKET_SCRIPT,
                    List.of(key),
                    String.valueOf(properties.capacity()),
                    String.valueOf(properties.refillPerSecond()),
                    "1",
                    String.valueOf(nowMillis));

            // null 은 정상 실행에서는 나오지 않지만, 나오더라도 fail-open 정책과 같은
            // 방향(허용)으로 처리한다.
            return allowed != null && allowed == 0L;

        } catch (RedisException | DataAccessException e) {
            log.warn("입찰 리미터 Redis 장애, fail-open으로 통과시킨다: {}", e.getMessage());
            return false;
        }
    }

    private static RedisScript<Long> createScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/token_bucket.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
