package com.hot6ix.upbid.domain.sse.config;

import com.hot6ix.upbid.domain.sse.event.SseEventPublisher;
import com.hot6ix.upbid.domain.sse.event.SseEventSubscriber;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
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
     * SSE 전송을 실행하는 VT executor. {@code RoomSseManager}가 쓴다.
     *
     * <p>VT 는 OS 스레드와 1:1 이 아니라 캐리어 스레드 위에서 멀티플렉싱되므로, 전송 건수만큼
     * 생성해도 캐리어 스레드 고갈이 일어나지 않는다. 다만 {@code SseEmitter.send()} 내부가
     * {@code synchronized} 라, 같은 emitter 에 VT 가 여럿 붙으면 monitor 대기가 캐리어를
     * 붙잡는다(Java 21 pinning). 그 영향은 {@code jdk.VirtualThreadPinned} 로 잰다.
     */
    @Bean(destroyMethod = "close")
    public Executor sseVirtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
