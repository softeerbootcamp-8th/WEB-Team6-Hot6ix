package com.hot6ix.upbid.domain.sse.config;

import com.hot6ix.upbid.domain.sse.event.SseEventPublisher;
import com.hot6ix.upbid.domain.sse.event.SseEventSubscriber;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
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
     *
     * <p>플랫폼 스레드 경로는 {@link java.util.concurrent.Executors#newFixedThreadPool} 대신
     * {@link ThreadPoolExecutor}를 직접 생성한다. {@code newFixedThreadPool}은 내부적으로
     * 무제한 {@link java.util.concurrent.LinkedBlockingQueue}를 사용해 큐 포화를 감지할 수
     * 없다. {@link EmitterDispatcher}의 {@link java.util.concurrent.SubmissionPublisher}가
     * emitter 당 drain 태스크를 최대 1개로 제한하므로 실제로는 emitter 수가 상한이 되지만,
     * 이 보장을 executor 설정에 명시하지 않으면 설계 변경 시 조용히 깨진다.
     * bounded 큐와 명시적 rejection handler 로 의도를 드러낸다.
     */
    @Bean(destroyMethod = "close")
    public Executor sseVirtualThreadExecutor(
            @Value("${upbid.sse.use-virtual-threads:true}") boolean useVirtualThreads,
            @Value("${upbid.sse.dispatch-pool-size:4}") int dispatchPoolSize,
            @Value("${upbid.sse.drain-executor-queue-capacity:2000}") int drainQueueCapacity) {
        if (useVirtualThreads) {
            return Executors.newVirtualThreadPerTaskExecutor();
        }
        return new ThreadPoolExecutor(
                dispatchPoolSize, dispatchPoolSize,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(drainQueueCapacity),
                (task, executor) -> log.warn("sse drain executor 포화 — drain 태스크 drop. 활성 emitter 수가 drain-executor-queue-capacity({})를 초과했다.", drainQueueCapacity));
    }

    /**
     * {@code useQueue=false} 일 때 emitter 에 직접 전송하는 executor.
     *
     * <p>queue 모드의 {@link #sseVirtualThreadExecutor}는 emitter 당 drain 태스크가 최대 1개라
     * executor 내부 큐가 자연스럽게 emitter 수에 바인딩된다. no-queue 모드는 이벤트마다 태스크를
     * 직접 제출하므로 FixedPool 의 {@code LinkedBlockingQueue}(무제한)를 그대로 쓰면
     * 부하 시 큐가 폭발한다. {@link ArrayBlockingQueue}로 상한을 두고 포화 시 drop 한다.
     *
     * <p>VT 는 태스크마다 VirtualThread 를 새로 만들어 내부 큐가 없으므로 bounded 설정이 무의미하다.
     * 그래서 VT 일 때는 {@link #sseVirtualThreadExecutor}와 동일하게 만든다.
     */
    @Bean(destroyMethod = "close")
    public Executor sseNoQueueExecutor(
            @Value("${upbid.sse.use-virtual-threads:true}") boolean useVirtualThreads,
            @Value("${upbid.sse.dispatch-pool-size:4}") int dispatchPoolSize,
            @Value("${upbid.sse.no-queue-executor-capacity:1024}") int noQueueCapacity) {
        if (useVirtualThreads) {
            return Executors.newVirtualThreadPerTaskExecutor();
        }
        return new ThreadPoolExecutor(
                dispatchPoolSize, dispatchPoolSize,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(noQueueCapacity),
                new ThreadPoolExecutor.DiscardPolicy());
    }
}
