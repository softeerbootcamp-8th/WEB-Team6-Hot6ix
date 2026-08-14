package com.hot6ix.upbid.domain.auction.config;

import com.hot6ix.upbid.global.redis.RedisDelayQueue;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 마감과 마감 임박 알림의 예약을 담는 Redis 큐, 그리고 마감을 실행하는 스레드 풀을 만든다.
 *
 * <p>{@link RedisDelayQueue}는 키 하나당 인스턴스 하나라서 컴포넌트 스캔으로 못 만들고
 * 여기서 키를 주며 등록한다. 마감과 알림이 <b>같은 클래스를 키만 달리해</b> 쓴다.
 */
@Configuration
@EnableConfigurationProperties(AuctionProperties.class)
public class AuctionSchedulingConfig {

    /**
     * 저장소의 첫 Redis 키다. 앞으로 붙을 세션과 SSE 도 {@code 도메인:용도} 두 칸으로 맞춘다.
     * 서비스 전용 Redis 라 서비스명 접두어는 붙이지 않는다.
     */
    public static final String CLOSE_DUE_KEY = "auction:close:due";

    /**
     * 마감 임박 알림 예약. <b>마감과 키를 나눈다.</b> 같은 물품이라도 알림 시각과 마감 시각이
     * 달라서 한 ZSET 에 담으면 member 가 겹쳐 서로를 덮어쓴다.
     */
    public static final String CLOSING_SOON_DUE_KEY = "auction:closing-soon:due";

    @Bean
    public RedisDelayQueue closeDelayQueue(StringRedisTemplate redisTemplate) {
        return new RedisDelayQueue(redisTemplate, CLOSE_DUE_KEY);
    }

    @Bean
    public RedisDelayQueue closingSoonDelayQueue(StringRedisTemplate redisTemplate) {
        return new RedisDelayQueue(redisTemplate, CLOSING_SOON_DUE_KEY);
    }

    /**
     * 마감을 실제로 실행하는 풀. 예약을 집어오는 폴링 스레드와 갈라 둔다.
     *
     * <p><b>큐가 차면 거절하게 두고 늘리지 않는다.</b> 거절된 물품은 미뤄 둔 시각이 지나면
     * 다시 집히기 때문이다. 무한 큐로 두면 밀리는 동안 같은 물품이 계속 쌓이는데, 그게 실행될
     * 때는 이미 다른 서버가 닫은 뒤다.
     *
     * <p>반환 타입이 구현 클래스인 것은 {@code AuctionClosePoller} 가 <b>남은 자리를 물어보고
     * 그만큼만 집기</b> 때문이다. {@code Executor} 로 받으면 그걸 물어볼 방법이 없다.
     */
    @Bean
    public ThreadPoolTaskExecutor auctionCloseExecutor(AuctionProperties auctionProperties) {

        AuctionProperties.Close close = auctionProperties.close();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(close.workerPoolSize());
        executor.setMaxPoolSize(close.workerPoolSize());
        executor.setQueueCapacity(close.queueCapacity());
        executor.setThreadNamePrefix("auction-close-");
        executor.initialize();

        return executor;
    }
}
