package com.hot6ix.upbid.domain.sse.config;

import com.hot6ix.upbid.domain.sse.event.ParticipantCountPublisher;
import com.hot6ix.upbid.domain.sse.event.ParticipantCountSubscriber;
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
            ParticipantCountSubscriber participantCountSubscriber,
            ThreadPoolTaskExecutor sseEventDispatchExecutor) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();

        container.setConnectionFactory(redisConnectionFactory);
        container.setTaskExecutor(sseEventDispatchExecutor);
        container.addMessageListener(sseEventSubscriber, new ChannelTopic(SseEventPublisher.EVENT_CHANNEL));
        container.addMessageListener(participantCountSubscriber,
                new ChannelTopic(ParticipantCountPublisher.PARTICIPANT_COUNT_CHANNEL));

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
     * 참여자 수 서버별 몫을 <b>절대값</b>으로 갱신 → HEXPIRE → 합산 → 발행하는 스크립트.
     * heartbeat가 방마다 주기적으로 호출해 TTL을 갱신하고 드리프트를 보정한다(#311).
     * {@code sse-publish.lua}와 달리 순차 ID를 발급하지 않고 replay 버퍼에도 안 쌓는다.
     */
    @Bean
    public RedisScript<Long> participantCountPublishScript() {
        return RedisScript.of(new ClassPathResource("redis/participant-count-publish.lua"), Long.class);
    }

    /**
     * 참여자 수 서버별 몫을 <b>증감</b>으로 반영하는 스크립트. {@code subscribe()}/
     * {@code disconnect()}가 쓴다 — 절대값을 읽어서 보내면 같은 서버의 두 호출이 순서가
     * 뒤바뀌어 도착했을 때 최신 값이 오래된 값에 덮어써질 수 있는데, 증감은 교환법칙이
     * 성립해 그 레이스가 없다(#311).
     */
    @Bean
    public RedisScript<Long> participantCountIncrementScript() {
        return RedisScript.of(new ClassPathResource("redis/participant-count-increment.lua"), Long.class);
    }

    /**
     * Redis 채널 구독 메시지를 받아 {@code deliverLocal()} 로 enqueue 하는 스레드.
     *
     * <p>실제 전송은 {@link #sseVirtualThreadExecutor}의 VT 가 담당하므로 이 스레드는
     * enqueue 만 하고 즉시 반환된다. 단일 스레드로도 충분하나, 방 단위 파티셔닝이 필요해지면
     * core/max 를 늘리고 {@code roomId % poolSize} 로 dispatch 한다.
     */
    // TODO: 트래픽 증가 시 방 단위 파티셔닝으로 전환 (방별 순서 보장 + 방 간 병렬 처리)
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
     * <p>가상 스레드를 사용한다. emitter 수만큼 생성해도 캐리어 스레드 고갈이 없고,
     * 실제 네트워크 지연이 있는 운영 환경에서 I/O 블로킹 시 캐리어 스레드를 반납해 CPU를 낭비하지 않는다.
     */
    @Bean(destroyMethod = "close")
    public Executor sseVirtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
