package com.hot6ix.upbid.domain.sse.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.sse.service.SseMetrics;
import com.hot6ix.upbid.domain.sse.service.SseServerIdentifier;
import com.hot6ix.upbid.global.support.AbstractRedisContainerTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * {@link ParticipantCountPublisher}가 <b>실제 Redis</b>를 거쳐 서버별 몫을 합산·발행하는지
 * 본다. 서버가 여러 대라는 것이 이 구조의 전제이므로, 서버 식별자만 다른 발행자를 두 개
 * 만들어 "서버 두 대"를 흉내 낸다.
 */
class ParticipantCountPublisherTest extends AbstractRedisContainerTest {

    private static final Long ROOM_ID = 20L;

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RedisMessageListenerContainer listenerContainer;
    private static ThreadPoolTaskExecutor dispatchExecutor;
    private static RedisScript<Long> script;
    private static RedisScript<Long> incrementScript;

    private final List<String> received = new CopyOnWriteArrayList<>();
    private MessageListener listener;

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = redisConnectionFactory();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        script = RedisScript.of(new ClassPathResource("redis/participant-count-publish.lua"), Long.class);
        incrementScript = RedisScript.of(new ClassPathResource("redis/participant-count-increment.lua"), Long.class);

        dispatchExecutor = new ThreadPoolTaskExecutor();
        dispatchExecutor.setCorePoolSize(1);
        dispatchExecutor.setMaxPoolSize(1);
        dispatchExecutor.afterPropertiesSet();

        listenerContainer = new RedisMessageListenerContainer();
        listenerContainer.setConnectionFactory(connectionFactory);
        listenerContainer.setTaskExecutor(dispatchExecutor);
        listenerContainer.afterPropertiesSet();
        listenerContainer.start();
    }

    @AfterAll
    static void tearDownRedis() {
        listenerContainer.stop();
        dispatchExecutor.shutdown();
        connectionFactory.destroy();
    }

    @BeforeEach
    void subscribeAndClear() {
        redisTemplate.delete(SseRedisKeys.counts(ROOM_ID));
        received.clear();
        listener = (message, pattern) -> received.add(new String(message.getBody(), StandardCharsets.UTF_8));
        listenerContainer.addMessageListener(listener, topic());
    }

    @AfterEach
    void unsubscribe() {
        listenerContainer.removeMessageListener(listener);
    }

    @Test
    @DisplayName("한 서버가 발행하면 그 서버 몫이 곧 전체 합계로 발행된다")
    void singleServerPublishesOwnCountAsTotal() {

        newPublisher("host-a:8080").publish(ROOM_ID, 3);

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(received).containsExactly(ROOM_ID + ":3"));
    }

    @Test
    @DisplayName("서버 두 대가 각자 발행하면 합계는 두 서버 몫을 더한 값이다")
    void aggregatesCountsAcrossMultipleServers() {

        ParticipantCountPublisher serverA = newPublisher("host-a:8080");
        ParticipantCountPublisher serverB = newPublisher("host-b:8080");

        serverA.publish(ROOM_ID, 2);
        serverB.publish(ROOM_ID, 1);

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(received.getLast()).isEqualTo(ROOM_ID + ":3"));
    }

    @Test
    @DisplayName("같은 서버가 다시 발행하면 이전 몫을 덮어쓴다(누적되지 않는다)")
    void overwritesOwnShareRatherThanAccumulating() {

        ParticipantCountPublisher serverA = newPublisher("host-a:8080");

        serverA.publish(ROOM_ID, 5);
        serverA.publish(ROOM_ID, 2);

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(received.getLast()).isEqualTo(ROOM_ID + ":2"));
    }

    @Test
    @DisplayName("발행하면 자기 몫 필드에 TTL이 걸린다")
    void appliesFieldTtlOnPublish() {

        newPublisher("host-a:8080").publish(ROOM_ID, 1);

        List<Long> ttls = redisTemplate.execute((RedisCallback<List<Long>>) connection ->
                connection.hashCommands().hTtl(
                        SseRedisKeys.counts(ROOM_ID).getBytes(StandardCharsets.UTF_8),
                        TimeUnit.SECONDS,
                        "host-a:8080".getBytes(StandardCharsets.UTF_8)));

        assertThat(ttls).hasSize(1);
        assertThat(ttls.getFirst())
                .as("HEXPIRE 1분이 걸려 있어야 한다 — heartbeat(30초)를 한 번 놓쳐도 버틸 여유")
                .isGreaterThan(0)
                .isLessThanOrEqualTo(60);
    }

    @Test
    @DisplayName("clear()는 집계 키 전체를 지운다")
    void clearRemovesTheWholeCountsKey() {

        ParticipantCountPublisher serverA = newPublisher("host-a:8080");
        ParticipantCountPublisher serverB = newPublisher("host-b:8080");
        serverA.publish(ROOM_ID, 2);
        serverB.publish(ROOM_ID, 1);

        serverA.clear(ROOM_ID);

        assertThat(redisTemplate.hasKey(SseRedisKeys.counts(ROOM_ID))).isFalse();
    }

    private ParticipantCountPublisher newPublisher(String serverId) {
        SseServerIdentifier identifier = mock(SseServerIdentifier.class);
        when(identifier.id()).thenReturn(serverId);

        return new ParticipantCountPublisher(
                redisTemplate, script, incrementScript, identifier, new SseMetrics(new SimpleMeterRegistry()));
    }

    private static ChannelTopic topic() {
        return new ChannelTopic(ParticipantCountPublisher.PARTICIPANT_COUNT_CHANNEL);
    }
}
