package com.hot6ix.upbid.domain.sse.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.hot6ix.upbid.domain.sse.config.SseProperties;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisKeys;
import com.hot6ix.upbid.domain.sse.service.BufferedEvent;
import com.hot6ix.upbid.domain.sse.service.SseEventBuffer;
import com.hot6ix.upbid.domain.sse.service.SseMetrics;
import com.hot6ix.upbid.global.support.AbstractRedisContainerTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link SseEventPublisher}가 내보낸 이벤트가 <b>실제 Redis 를 거쳐</b> 구독자에게 돌아오고,
 * 동시에 replay 버퍼에도 남는지 본다. 인스턴스가 여러 대라는 것이 이 구조의 전제이므로,
 * 구독자를 두 개 붙여 둘 다 같은 이벤트를 같은 ID로 받는지까지 확인한다.
 */
class SseEventRedisRoundTripTest extends AbstractRedisContainerTest {

    private static final Long ROOM_ID = 10L;
    private static final long ITEM_ID = 101L;
    private static final int BUFFER_SIZE = 50;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-12T03:04:05Z"), ZoneOffset.UTC);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RedisMessageListenerContainer listenerContainer;
    private static ThreadPoolTaskExecutor dispatchExecutor;

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final SseEventBuffer sseEventBuffer = new SseEventBuffer(redisTemplate, objectMapper);

    /** 인스턴스 두 대가 같은 채널을 구독한 상황을 흉내 낸다. */
    private final List<SseEventEnvelope> instanceA = new CopyOnWriteArrayList<>();
    private final List<SseEventEnvelope> instanceB = new CopyOnWriteArrayList<>();

    private MessageListener listenerA;
    private MessageListener listenerB;

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = redisConnectionFactory();
        redisTemplate = new StringRedisTemplate(connectionFactory);

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
        sseEventBuffer.clear(ROOM_ID);
        redisTemplate.delete(AuctionRedisKeys.item(ITEM_ID));

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

        SseEventEnvelope received = instanceA.getFirst();
        assertThat(received.message().roomId()).isEqualTo(ROOM_ID);
        assertThat(received.message().eventName()).isEqualTo("BID_PLACED");
        assertThat(received.id())
                .as("ID는 발행 스크립트가 INCR로 한 번만 뽑는다. 첫 이벤트는 1이다")
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

        assertThat(instanceA.stream().map(SseEventEnvelope::id)).containsExactly(1L, 2L, 3L);
    }

    /**
     * ID 발급({@code INCR})과 발행({@code PUBLISH})이 나뉘어 있으면, 낮은 ID를 받은 스레드가
     * 높은 ID를 받은 스레드보다 늦게 발행해 브라우저가 이벤트를 역순으로 받는다. 실제로 16개
     * 스레드로 재현했을 때 id=1이 14번째로 도착했다.
     *
     * <p>발행 스크립트가 둘을 한 번에 실행하면서 그 틈이 사라졌다. 구독 컨테이너의 디스패치
     * 스레드가 1개라 <b>수신 순서 = 도착 순서</b>이므로, 여기서 순서가 어긋나면 원인은 발행 쪽뿐이다.
     */
    @Test
    @DisplayName("동시 발행이 몰려도 도착 순서가 ID 순서와 같다")
    void preservesIdOrderUnderConcurrentPublish() throws Exception {

        SseEventPublisher publisher = newPublisher();

        int threads = 16;
        int perThread = 20;
        int total = threads * perThread;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        // 스레드를 다 띄운 뒤 한꺼번에 출발시킨다.
        // 안 그러면 먼저 뜬 스레드가 발행을 끝내버려 겹치는 구간이 생기지 않는다.
        CountDownLatch start = new CountDownLatch(1);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                start.await();
                for (int i = 0; i < perThread; i++) {
                    publisher.publish("BID_PLACED", ROOM_ID, new TestPayload(i));
                }
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(instanceA).hasSize(total));

        assertThat(instanceA.stream().map(SseEventEnvelope::id))
                .as("도착 순서가 ID 순서와 같아야 한다")
                .isSorted();
    }

    @Test
    @DisplayName("발행과 동시에 replay 버퍼에도 남아, 발행하지 않은 인스턴스가 읽을 수 있다")
    void leavesEventInBufferReadableByAnyInstance() {

        SseEventPublisher publisher = newPublisher();

        publisher.publish("BID_PLACED", ROOM_ID, new TestPayload(1_000L));
        publisher.publish("ITEM_ENDED", ROOM_ID, new TestPayload(2_000L));

        // 이 버퍼는 발행자와 아무 상태도 공유하지 않는다. 방금 뜬 인스턴스와 같은 조건이다.
        SseEventBuffer otherInstance = new SseEventBuffer(redisTemplate, objectMapper);

        List<BufferedEvent> missed = otherInstance.getEventsAfter(ROOM_ID, 1L).events();

        assertThat(missed).hasSize(1);
        assertThat(missed.getFirst().id()).isEqualTo(2L);
        assertThat(missed.getFirst().eventName()).isEqualTo("ITEM_ENDED");
        assertThat(missed.getFirst().occurredAt())
                .as("발행 시각은 인스턴스마다 다시 재지 않고 발행자가 찍은 값을 그대로 쓴다")
                .isEqualTo(CLOCK.instant());
    }

    @Test
    @DisplayName("payload 는 클래스 이름 없이 평문 JSON 으로 오간다")
    void carriesPayloadAsPlainJson() {

        newPublisher().publish("BID_PLACED", ROOM_ID, new TestPayload(12_000L));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(instanceA).hasSize(1));

        // 받은 쪽은 DTO 타입을 모르고 Map 으로 푼다. 다시 직렬화하면 같은 JSON 이라 화면에는
        // 차이가 없고, 대신 DTO 를 옮기거나 이름을 바꿔도 롤링 배포 중 역직렬화가 안 깨진다.
        assertThat(instanceA.getFirst().message().data())
                .isInstanceOf(java.util.Map.class)
                .isEqualTo(java.util.Map.of("bidPrice", 12_000));
    }

    @Test
    @DisplayName("현재 물품 상태와 같은 이벤트만 ID를 발급하고 replay 버퍼와 채널에 남긴다")
    void publishesOnlyWhenAuctionHashMatchesExpectedState() {

        String itemKey = AuctionRedisKeys.item(ITEM_ID);
        redisTemplate.opsForHash().putAll(itemKey, java.util.Map.of(
                "status", "IN_PROGRESS",
                "currentPrice", "12000",
                "leaderUserId", "11"));
        SseEventPublisher publisher = newPublisher();

        boolean currentPublished = publisher.publishIfHashMatches(
                "BID_PLACED",
                ROOM_ID,
                itemKey,
                java.util.Map.of(
                        "status", "IN_PROGRESS",
                        "currentPrice", "12000",
                        "leaderUserId", "11"),
                new TestPayload(12_000L));
        boolean stalePublished = publisher.publishIfHashMatches(
                "BID_PLACED",
                ROOM_ID,
                itemKey,
                java.util.Map.of(
                        "status", "IN_PROGRESS",
                        "currentPrice", "11000",
                        "leaderUserId", "11"),
                new TestPayload(11_000L));

        assertThat(currentPublished).isTrue();
        assertThat(stalePublished).isFalse();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(instanceA).hasSize(1));
        assertThat(instanceA.getFirst().message().data())
                .isEqualTo(java.util.Map.of("bidPrice", 12_000));
        assertThat(redisTemplate.opsForValue().get(SseRedisKeys.sequence(ROOM_ID))).isEqualTo("1");
        assertThat(sseEventBuffer.getEventsAfter(ROOM_ID, 0L).events()).hasSize(1);
    }

    private SseEventPublisher newPublisher() {
        return new SseEventPublisher(
                redisTemplate,
                RedisScript.of(new ClassPathResource("redis/sse-publish.lua"), Long.class),
                objectMapper,
                new SseProperties(0L, 0L, BUFFER_SIZE, "local", 128, 1_000L),
                new SseMetrics(new SimpleMeterRegistry()),
                CLOCK);
    }

    private static ChannelTopic topic() {
        return new ChannelTopic(SseEventPublisher.EVENT_CHANNEL);
    }

    private MessageListener collectInto(List<SseEventEnvelope> received) {
        return (message, pattern) -> received.add(SseEventEnvelope.decode(
                new String(message.getBody(), StandardCharsets.UTF_8), objectMapper));
    }

    private record TestPayload(long bidPrice) {
    }
}
