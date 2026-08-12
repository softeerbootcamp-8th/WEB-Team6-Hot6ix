package com.hot6ix.upbid.domain.sse.config;

import com.hot6ix.upbid.domain.sse.event.SseEventPublisher;
import com.hot6ix.upbid.domain.sse.event.SseEventSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class SseRedisConfig {

    @Bean
    public RedisMessageListenerContainer sseRedisMessageListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            SseEventSubscriber sseEventSubscriber,
            ThreadPoolTaskExecutor sseEventDispatchExecutor) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();

        container.setConnectionFactory(redisConnectionFactory);
        container.setTaskExecutor(sseEventDispatchExecutor);
        container.addMessageListener(sseEventSubscriber, new ChannelTopic(SseEventPublisher.EVENT_CHANNEL));

        return container;
    }

    /**
     * 받은 메시지를 리스너에 넘기는 스레드. <b>반드시 1개여야 한다.</b>
     *
     * <p>이 값을 안 주면 컨테이너가 {@code SimpleAsyncTaskExecutor}를 만들어 <b>메시지마다 새
     * 스레드</b>로 넘긴다. 그러면 Redis 가 순서대로 보낸 이벤트도 리스너에 도착하는 순서가
     * 뒤집힌다(테스트에서 발행 순서 1·2·3이 2·1·3으로 들어오는 것을 확인했다). SSE 는 순서가
     * 곧 의미다 — 입찰가가 거꾸로 올라오고, 마지막이어야 할 {@code ROOM_CLOSED} 가 먼저 도착해
     * 뒤 이벤트가 끊긴 연결로 나간다.
     *
     * <p>대신 모든 방의 fan-out 이 이 스레드 하나를 거친다. 방이 늘면 여기가 병목이 될 수
     * 있는데, 방별로 순서를 지키면서 병렬로 넘기려면 방 단위 파티셔닝이 필요하다. 지금 규모에서
     * 필요한지는 부하 측정으로 확인하고 정한다.
     */
    @Bean
    public ThreadPoolTaskExecutor sseEventDispatchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setThreadNamePrefix("sse-dispatch-");

        return executor;
    }
}
