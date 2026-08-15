package com.hot6ix.upbid.global.config;

import com.hot6ix.upbid.domain.auction.service.AuctionRoomPublicCacheService;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Duration;
import java.util.Set;
import java.util.function.ToLongFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.cache.CacheStatistics;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

/**
 * 조회 캐시를 Redis에 둔다.
 *
 * <p><b>캐시는 없어도 되는 것으로 다룬다.</b> Redis가 죽으면 조회는 DB로 가면 그만이라,
 * 마감 예약(Redis가 원본인 곳)과 달리 여기서는 예외를 밖으로 내보내지 않는다.
 * {@link #errorHandler()}가 그 역할을 한다.
 *
 * <p>키는 {@code 도메인:용도::식별자} 꼴이다. 캐시 이름이 앞부분이고 스프링이 {@code ::}와
 * 키를 붙인다 (예: {@code auction:room:public::AbCd1234}). 마감 예약의
 * {@code auction:close:due}와 같은 규칙이다.
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private final Duration timeToLive;

    public CacheConfig(@Value("${app.cache.time-to-live:5m}") Duration timeToLive) {
        this.timeToLive = timeToLive;
    }

    /**
     * <b>히트율을 지표로 남긴다.</b> 캐시가 실제로 맞고 있는지는 응답 시간만 봐서는 안 갈린다 —
     * DB가 한가하면 캐시가 하나도 안 맞아도 빠르기 때문이다. {@code cache_gets_total} 의
     * {@code result="hit"|"miss"} 로 본다.
     *
     * <p>그래서 캐시 이름을 미리 등록한다. Micrometer는 부팅 때 있는 캐시에만 계량기를 붙이는데,
     * Redis 캐시는 처음 쓰일 때 만들어져서 그냥 두면 지표가 영영 안 붙는다.
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(cacheConfiguration())
                .initialCacheNames(Set.of(AuctionRoomPublicCacheService.ROOM_PUBLIC_CACHE))
                .enableStatistics()
                .build();
    }

    /**
     * 히트율을 Prometheus로 내보낸다. <b>Spring Boot 4에는 Redis 캐시 지표 자동 설정이 없어서</b>
     * (Micrometer가 들고 있는 것은 Caffeine·Guava·JCache뿐이다) 직접 붙인다.
     *
     * <p>{@code cache_gets_total{cache="auction:room:public", result="hit"}} 꼴로 나온다.
     * 응답 시간만 봐서는 캐시가 맞고 있는지 안 갈린다 — DB가 한가하면 하나도 안 맞아도 빠르다.
     */
    @Bean
    public MeterBinder roomPublicCacheMetrics(RedisCacheManager cacheManager) {
        return registry -> {
            Cache cache = cacheManager.getCache(AuctionRoomPublicCacheService.ROOM_PUBLIC_CACHE);

            if (!(cache instanceof RedisCache redisCache)) {
                return;
            }

            String name = redisCache.getName();

            counter(registry, redisCache, name, "hit", CacheStatistics::getHits);
            counter(registry, redisCache, name, "miss", CacheStatistics::getMisses);

            FunctionCounter.builder("cache.puts", redisCache,
                            c -> c.getStatistics().getPuts())
                    .tag("cache", name)
                    .register(registry);
        };
    }

    private void counter(MeterRegistry registry, RedisCache cache, String name, String result,
                         ToLongFunction<CacheStatistics> value) {
        FunctionCounter.builder("cache.gets", cache, c -> value.applyAsLong(c.getStatistics()))
                .tag("cache", name)
                .tag("result", result)
                .register(registry);
    }

    /**
     * 값은 JSON으로 담는다. 자바 직렬화를 쓰면 필드를 하나 고칠 때마다 캐시에 남아 있던 값이
     * 통째로 못 읽는 값이 되고, redis-cli로 열어볼 수도 없다.
     *
     * <p>타입 정보를 값 안에 함께 적는다({@code @class}). 그래야 꺼낼 때 {@code Map}이 아니라
     * 원래 record로 돌아온다. 대신 그 이름을 그대로 믿고 아무 클래스나 만들면 위험하므로
     * 우리 패키지와 표준 라이브러리만 허용한다.
     */
    private RedisCacheConfiguration cacheConfiguration() {
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.hot6ix.upbid.")
                .allowIfSubType("java.")
                .build();

        GenericJacksonJsonRedisSerializer valueSerializer = GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(typeValidator)
                .build();

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(timeToLive)
                // 없는 방을 조회하면 예외가 나므로 null이 담길 일이 없다. 켜 두면 존재하지 않는
                // 공유 코드를 넣어보는 것만으로 캐시를 채울 수 있다.
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(StringRedisSerializer.UTF_8))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(valueSerializer));
    }

    /**
     * Redis가 죽었을 때 조회가 멈추지 않게 한다. 읽기 실패는 캐시 미스로 보고 DB로 가고,
     * 쓰기 실패는 그냥 버린다.
     *
     * <p><b>느려지는 것까지 막지는 못한다.</b> 연결이 거절되면 바로 떨어지지만, 서버가 받기만
     * 하고 응답을 안 주면 {@code spring.data.redis.timeout}(5초)만큼 기다렸다가 DB로 간다.
     */
    @Override
    @Bean
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {

            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("캐시 조회 실패라 DB로 간다: cache={}, key={}", cache.getName(), key, exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("캐시 저장 실패: cache={}, key={}", cache.getName(), key, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("캐시 삭제 실패: cache={}, key={}", cache.getName(), key, exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("캐시 비우기 실패: cache={}", cache.getName(), exception);
            }
        };
    }
}
