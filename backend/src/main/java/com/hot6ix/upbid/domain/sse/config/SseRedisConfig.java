package com.hot6ix.upbid.domain.sse.config;

import com.hot6ix.upbid.domain.sse.event.SseEventPublisher;
import com.hot6ix.upbid.domain.sse.event.SseEventSubscriber;
import java.util.concurrent.Executor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(SseProperties.class)
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
     * <p>실제 전송은 {@link #sseWorkerExecutor}의 고정 풀 스레드가 담당하므로 이 스레드는
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
     * emitter 별 drain 을 실행하는 고정 스레드 풀. {@link EmitterDispatcher}가 공유한다.
     *
     * <p>가상 스레드 대신 플랫폼 스레드 풀을 사용한다. 스레드 수는
     * {@code upbid.sse.worker-pool-size}로 제어하며, 기본값은
     * {@code SSE_WORKER_POOL_SIZE} 환경변수로 주입한다.
     *
     * <p>SubmissionPublisher 가 emitter 당 순차 drain 을 보장하므로,
     * 스레드 수보다 많은 emitter 가 붙으면 drain 이 풀에서 순번을 기다린다.
     * 스레드가 모자라면 지연이 쌓이므로 부하 측정 결과에 맞춰 크기를 조정한다.
     */
    @Bean
    public Executor sseWorkerExecutor(SseProperties sseProperties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(sseProperties.workerPoolSize());
        executor.setMaxPoolSize(sseProperties.workerPoolSize());
        executor.setQueueCapacity(sseProperties.workerQueueCapacity());
        executor.setThreadNamePrefix("sse-sender-");
        executor.initialize();
        return executor;
    }
}
