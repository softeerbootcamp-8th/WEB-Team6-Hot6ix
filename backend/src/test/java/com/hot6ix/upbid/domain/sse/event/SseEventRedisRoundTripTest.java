package com.hot6ix.upbid.domain.sse.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link SseEventPublisher}가 내보낸 이벤트가 <b>실제 Redis 채널을 거쳐</b> 구독자에게
 * 돌아오는지 본다. 인스턴스가 여러 대라는 것이 이 구조의 전제이므로, 구독자를 두 개 붙여
 * 둘 다 같은 이벤트를 같은 ID로 받는지까지 확인한다.
 *
 * <p>Spring 컨텍스트 없이 구성한다 — 보는 것이 Redis pub/sub 조합의 동작이라 컨텍스트
 * 로딩까지 필요하지 않다. {@link com.hot6ix.upbid.global.config.RedisLockProviderTest}와 같은 방식이다.
 */
class SseEventRedisRoundTripTest {

    private static final Long ROOM_ID = 10L;

    private static final GenericContainer<?> REDIS_CONTAINER =
            new GenericContainer<>(DockerImageName.parse("redis:8.10.0")).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate stringRedisTemplate;
    private static RedisMessageListenerContainer listenerContainer;
    private static ThreadPoolTaskExecutor dispatchExecutor;

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    /** 인스턴스 두 대가 같은 채널을 구독한 상황을 흉내 낸다. */
    private final List<SseEventMessage> instanceA = new CopyOnWriteArrayList<>();
    private final List<SseEventMessage> instanceB = new CopyOnWriteArrayList<>();

    private MessageListener listenerA;
    private MessageListener listenerB;

    static {
        REDIS_CONTAINER.start();
    }

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                REDIS_CONTAINER.getHost(), REDIS_CONTAINER.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();

        stringRedisTemplate = new StringRedisTemplate(connectionFactory);

        // 운영 설정(SseRedisConfig)과 같은 단일 스레드 디스패치를 쓴다. 이걸 안 맞추면
        // 컨테이너가 메시지마다 새 스레드를 띄워서 순서 검증이 통과했다 말았다 한다.
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
    void subscribeBothInstances() {
        stringRedisTemplate.delete("upbid:sse:room:" + ROOM_ID + ":seq");

        listenerA = collectInto(instanceA);
        listenerB = collectInto(instanceB);

        listenerContainer.addMessageListener(listenerA, topic());
        listenerContainer.addMessageListener(listenerB, topic());
    }

    /** 구독을 걷어내지 않으면 앞선 테스트의 리스너가 계속 살아 다음 테스트의 이벤트까지 받는다. */
    @AfterEach
    void unsubscribeBothInstances() {
        listenerContainer.removeMessageListener(listenerA);
        listenerContainer.removeMessageListener(listenerB);
    }

    @Test
    @DisplayName("발행한 이벤트가 채널을 구독하는 모든 인스턴스에 같은 ID로 도착한다")
    void deliversSameEventWithSameIdToEveryInstance() {

        newPublisher().publish("BID_PLACED", ROOM_ID, new TestPayload(12_000L));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(instanceA).hasSize(1);
            assertThat(instanceB).hasSize(1);
        });

        SseEventMessage received = instanceA.getFirst();
        assertThat(received.roomId()).isEqualTo(ROOM_ID);
        assertThat(received.eventName()).isEqualTo("BID_PLACED");
        assertThat(received.id())
                .as("ID는 발행 시점에 Redis INCR 로 한 번만 뽑는다. 첫 이벤트는 1이다")
                .isEqualTo(1L);
        assertThat(instanceB.getFirst().id())
                .as("두 인스턴스가 다른 ID를 보면 브라우저가 돌려주는 Last-Event-ID 의 뜻이 갈린다")
                .isEqualTo(received.id());
    }

    @Test
    @DisplayName("순차 ID는 방마다 1부터 하나씩 올라간다")
    void assignsSequentialIdPerRoom() {

        SseEventPublisher publisher = newPublisher();

        publisher.publish("BID_PLACED", ROOM_ID, new TestPayload(1_000L));
        publisher.publish("BID_PLACED", ROOM_ID, new TestPayload(2_000L));
        publisher.publish("BID_PLACED", ROOM_ID, new TestPayload(3_000L));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(instanceA).hasSize(3));

        assertThat(instanceA.stream().map(SseEventMessage::id)).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("clearSequence() 뒤에는 ID가 다시 1부터 시작한다")
    void restartsIdAfterClearSequence() {

        SseEventPublisher publisher = newPublisher();

        publisher.publish("BID_PLACED", ROOM_ID, new TestPayload(1_000L));
        publisher.clearSequence(ROOM_ID);
        publisher.publish("BID_PLACED", ROOM_ID, new TestPayload(2_000L));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(instanceA).hasSize(2));

        assertThat(instanceA.stream().map(SseEventMessage::id)).containsExactly(1L, 1L);
    }

    @Test
    @DisplayName("payload 는 클래스 이름 없이 평문 JSON 으로 오간다")
    void carriesPayloadAsPlainJson() {

        newPublisher().publish("BID_PLACED", ROOM_ID, new TestPayload(12_000L));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(instanceA).hasSize(1));

        // 받은 쪽은 DTO 타입을 모르고 Map 으로 푼다. 다시 직렬화하면 같은 JSON 이라 화면에는
        // 차이가 없고, 대신 DTO 를 옮기거나 이름을 바꿔도 롤링 배포 중 역직렬화가 안 깨진다.
        assertThat(instanceA.getFirst().data())
                .isInstanceOf(java.util.Map.class)
                .isEqualTo(java.util.Map.of("bidPrice", 12_000));
    }

    private SseEventPublisher newPublisher() {
        return new SseEventPublisher(
                stringRedisTemplate,
                objectMapper,
                Clock.fixed(Instant.parse("2026-08-12T03:04:05Z"), ZoneOffset.UTC));
    }

    private static ChannelTopic topic() {
        return new ChannelTopic(SseEventPublisher.EVENT_CHANNEL);
    }

    private MessageListener collectInto(List<SseEventMessage> received) {
        return (message, pattern) ->
                received.add(objectMapper.readValue(message.getBody(), SseEventMessage.class));
    }

    private record TestPayload(long bidPrice) {
    }
}
