package com.hot6ix.upbid.domain.auction.config;

import com.hot6ix.upbid.global.redis.RedisDelayQueue;
import java.util.concurrent.Executor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 마감 예약을 담는 Redis 큐와 마감을 실행하는 스레드 풀을 만든다.
 *
 * <p>{@link RedisDelayQueue}는 키 하나당 인스턴스 하나라서 컴포넌트 스캔으로 못 만들고
 * 여기서 키를 주며 등록한다. 마감 임박 알림도 같은 클래스에 다른 키로 빈을 하나 더 만든다.
 */
@Configuration
@EnableConfigurationProperties(AuctionProperties.class)
public class AuctionCloseConfig {

    /**
     * 저장소의 첫 Redis 키다. 앞으로 붙을 세션과 SSE 도 {@code 도메인:용도} 두 칸으로 맞춘다.
     * 서비스 전용 Redis 라 서비스명 접두어는 붙이지 않는다.
     */
    public static final String CLOSE_DUE_KEY = "auction:close:due";

    @Bean
    public RedisDelayQueue closeDelayQueue(StringRedisTemplate redisTemplate) {
        return new RedisDelayQueue(redisTemplate, CLOSE_DUE_KEY);
    }

    /**
     * 마감을 실제로 실행하는 풀. 예약을 집어오는 폴링 스레드와 갈라 둔다.
     *
     * <p><b>큐가 차면 거절하게 두고 늘리지 않는다.</b> 거절된 물품은 미뤄 둔 시각이 지나면
     * 다시 집히기 때문이다. 무한 큐로 두면 밀리는 동안 같은 물품이 계속 쌓이는데, 그게 실행될
     * 때는 이미 다른 서버가 닫은 뒤다.
     *
     * <p>큐 크기를 한 번에 집는 개수에 맞춰 <b>한 배치는 들어가되 그 이상은 안 쌓이게</b> 한다.
     * 거절이 나기 시작하면 처리가 못 따라간다는 신호다.
     */
    @Bean
    public Executor auctionCloseExecutor(AuctionProperties auctionProperties) {

        AuctionProperties.Close close = auctionProperties.close();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(close.workerPoolSize());
        executor.setMaxPoolSize(close.workerPoolSize());
        executor.setQueueCapacity(close.claimBatchSize());
        executor.setThreadNamePrefix("auction-close-");
        executor.initialize();

        return executor;
    }
}
