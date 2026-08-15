package com.hot6ix.upbid.domain.sse.config;

import com.hot6ix.upbid.domain.sse.event.SseEventPublisher;
import com.hot6ix.upbid.domain.sse.event.SseEventSubscriber;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.script.RedisScript;
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
     * ID 발급, 버퍼 적재, 채널 발행을 원자적으로 실행하는 스크립트.
     *
     * <p>{@code DefaultRedisScript}가 {@code EVALSHA}를 먼저 시도하고, 서버에 스크립트가 없다는
     * 응답을 받으면 {@code EVAL}로 다시 보낸다. 스크립트 캐시가 비워져도 알아서 복구된다.
     */
    @Bean
    public RedisScript<Long> ssePublishScript() {
        return RedisScript.of(new ClassPathResource("redis/sse-publish.lua"), Long.class);
    }

    /**
     * Redis 채널 구독 메시지를 받아 {@code deliverLocal()} 로 enqueue 하는 스레드.
     *
     * <p>실제 전송은 {@link #sseVirtualThreadExecutor}의 VT 가 담당하므로 이 스레드는
     * enqueue 만 하고 즉시 반환된다. 단일 스레드로도 충분하나, 방 단위 파티셔닝이 필요해지면
     * core/max 를 늘리고 {@code roomId % poolSize} 로 dispatch 한다.
     */
    @Bean
    public ThreadPoolTaskExecutor sseEventDispatchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setThreadNamePrefix("sse-dispatch-");

        return executor;
    }

    /**
     * emitter 별 drain 을 실행하는 executor. {@link EmitterDispatcher}가 공유한다.
     *
     * <p>기본값은 가상 스레드({@code true})다. 부하 측정에서 플랫폼 스레드와 비교할 때
     * {@code SSE_USE_VIRTUAL_THREADS=false} 환경변수로 전환한다.
     *
     * <ul>
     *   <li>가상 스레드: emitter 수만큼 생성해도 캐리어 스레드 고갈이 없다.</li>
     *   <li>플랫폼 스레드: send() 블록 시 OS 스레드를 점유하며 연결 수만큼 스레드가 늘어난다.</li>
     * </ul>
     */
    @Bean(destroyMethod = "close")
    public Executor sseVirtualThreadExecutor(
            @Value("${upbid.sse.use-virtual-threads:true}") boolean useVirtualThreads,
            @Value("${upbid.sse.dispatch-pool-size:4}") int dispatchPoolSize) {
        return useVirtualThreads
                ? Executors.newVirtualThreadPerTaskExecutor()
                : Executors.newFixedThreadPool(dispatchPoolSize);
    }
}
