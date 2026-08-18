package com.hot6ix.upbid.domain.sse.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.domain.sse.config.SseProperties;
import com.hot6ix.upbid.domain.sse.dto.SseEventsLostDto;
import com.hot6ix.upbid.domain.sse.event.SseEventEnvelope;
import com.hot6ix.upbid.domain.sse.event.SseEventMessage;
import com.hot6ix.upbid.domain.sse.event.SseEventPublisher;
import com.hot6ix.upbid.domain.sse.event.SseRedisKeys;
import com.hot6ix.upbid.global.support.AbstractRedisContainerTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import tools.jackson.databind.json.JsonMapper;

/**
 * 버퍼에 쌓고 자르는 것은 발행 스크립트가 하고, 이 클래스는 읽기만 한다. 그래서 여기서도
 * 발행자를 써서 데이터를 넣고 읽는 쪽을 본다.
 *
 * <p>ID 발급도 이 클래스의 일이 아니다. 스크립트가 {@code INCR}로 정한 값을 score 로 달고
 * 들어온 것을 그대로 읽어 돌려줄 뿐이다.
 */
class SseEventBufferTest extends AbstractRedisContainerTest {

    private static final Long ROOM_A = 101L;
    private static final Long ROOM_B = 102L;
    private static final int BUFFER_SIZE = 3;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-12T03:04:05Z"), ZoneOffset.UTC);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final SseEventBuffer buffer = new SseEventBuffer(redisTemplate, objectMapper);

    private final SseEventPublisher publisher = new SseEventPublisher(
            redisTemplate,
            RedisScript.of(new ClassPathResource("redis/sse-publish.lua"), Long.class),
            objectMapper,
            new SseProperties(0L, 0L, BUFFER_SIZE, "local", 128, 1_000L),
            new SseMetrics(new SimpleMeterRegistry()),
            CLOCK);

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = redisConnectionFactory();
        redisTemplate = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void tearDownRedis() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void clearRooms() {
        buffer.clear(ROOM_A);
        buffer.clear(ROOM_B);
    }

    private void publish(Long roomId) {
        publisher.publish("EVENT", roomId, "data");
    }

    @Test
    @DisplayName("발행된 이벤트를 ID와 발행 시각 그대로 돌려준다")
    void keepsIdAndOccurredAtFromPublisher() {
        publisher.publish("BID_PLACED", ROOM_A, "data");

        List<BufferedEvent> result = buffer.getAllEvents(ROOM_A);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(1L);
        assertThat(result.getFirst().eventName()).isEqualTo("BID_PLACED");
        assertThat(result.getFirst().occurredAt())
                .as("인스턴스마다 다시 재면 같은 이벤트의 시각이 서로 달라진다")
                .isEqualTo(CLOCK.instant());
    }

    @Test
    @DisplayName("N개 초과 시 가장 오래된 이벤트가 밀려난다")
    void evictsOldestWhenOverBufferSize() {
        publish(ROOM_A);
        publish(ROOM_A);
        publish(ROOM_A);
        publish(ROOM_A);  // id=1이 밀려남

        List<BufferedEvent> result = buffer.getEventsAfter(ROOM_A, 0).events();

        assertThat(result).hasSize(BUFFER_SIZE);
        assertThat(result).extracting(BufferedEvent::id).containsExactly(2L, 3L, 4L);
    }

    @Test
    @DisplayName("getEventsAfter()는 lastEventId 이후 이벤트만 반환한다")
    void getEventsAfter_returnsOnlyEventsAfterLastId() {
        publish(ROOM_A);
        publish(ROOM_A);
        publish(ROOM_A);

        ReplayResult result = buffer.getEventsAfter(ROOM_A, 1);

        assertThat(result.events()).extracting(BufferedEvent::id).containsExactly(2L, 3L);
        assertThat(result.hasLoss())
                .as("버퍼가 lastEventId 바로 다음부터 이어지면 놓친 구간이 없다")
                .isFalse();
    }

    /**
     * ZSET 은 score 순으로 읽히므로 들어간 순서가 뒤집혀 있어도 ID 오름차순으로 나온다.
     *
     * <p>인메모리 큐를 쓸 때는 삽입 순서로 읽혀서, ID 순서와 도착 순서가 어긋나면
     * {@code dropWhile}이 첫 번째로 큰 ID 에서 멈춰 이미 보낸 이벤트를 다시 내보냈다.
     * 발행이 원자적이라 지금은 역순으로 들어올 일도 없지만, 읽는 쪽이 그 가정에 기대지
     * 않는다는 것을 고정해 둔다.
     */
    @Test
    @DisplayName("삽입 순서가 ID 순서와 달라도 ID 오름차순으로 반환한다")
    void getEventsAfter_isIndependentOfInsertionOrder() {
        addDirectly(ROOM_A, 4L);
        addDirectly(ROOM_A, 6L);
        addDirectly(ROOM_A, 5L);

        List<BufferedEvent> result = buffer.getEventsAfter(ROOM_A, 5).events();

        assertThat(result).extracting(BufferedEvent::id).containsExactly(6L);
    }

    @Test
    @DisplayName("lastEventId가 버퍼에서 밀려난 경우 남아 있는 이벤트를 전부 반환하고 유실을 알린다")
    void getEventsAfter_returnsAllWhenLastIdEvicted() {
        publish(ROOM_A);
        publish(ROOM_A);
        publish(ROOM_A);
        publish(ROOM_A);
        publish(ROOM_A);  // id=1, 2가 밀려남

        // id=1은 이미 버퍼에 없음 → 가능한 이벤트를 최대한 복구
        ReplayResult result = buffer.getEventsAfter(ROOM_A, 1);

        assertThat(result.events()).extracting(BufferedEvent::id).containsExactly(3L, 4L, 5L);
        assertThat(result.lostReason())
                .as("id=2 를 메울 수 없으므로 화면이 상태를 다시 읽어야 한다")
                .isEqualTo(SseEventsLostDto.BUFFER_OVERFLOW);
    }

    @Test
    @DisplayName("lastEventId가 최신 id와 같으면 빈 리스트를 반환한다")
    void getEventsAfter_returnsEmptyWhenUpToDate() {
        publish(ROOM_A);
        publish(ROOM_A);

        ReplayResult result = buffer.getEventsAfter(ROOM_A, 2);

        assertThat(result.events()).isEmpty();
        assertThat(result.hasLoss()).isFalse();
    }

    @Test
    @DisplayName("버퍼가 없는 방은 빈 리스트를 반환한다")
    void getEventsAfter_returnsEmptyForUnknownRoom() {
        ReplayResult result = buffer.getEventsAfter(ROOM_A, 0);

        assertThat(result.events()).isEmpty();
        assertThat(result.hasLoss())
                .as("받은 이벤트가 없다고 들고 왔으면 놓친 것도 없다")
                .isFalse();
    }

    /**
     * 유실 규모가 가장 큰 경우다. Redis 재시작·failover·TTL 만료로 버퍼가 통째로 사라지면
     * 남은 이벤트가 없어 replay 로 아무것도 메울 수 없다.
     *
     * <p>예전에는 이 분기에서 빈 목록만 돌려주고 유실 판정을 건너뛰어서 로그 한 줄도
     * 남지 않았다.
     */
    @Test
    @DisplayName("버퍼가 비었는데 클라이언트가 ID를 들고 오면 유실로 판정한다")
    void getEventsAfter_reportsLossWhenBufferGone() {
        ReplayResult result = buffer.getEventsAfter(ROOM_A, 57);

        assertThat(result.events()).isEmpty();
        assertThat(result.lostReason()).isEqualTo(SseEventsLostDto.BUFFER_MISSING);
    }

    /**
     * Redis 가 데이터 없이 올라오면 순차 ID 카운터가 1부터 다시 시작한다. 이때 클라이언트가
     * 든 ID 가 버퍼의 마지막 ID 보다 커서, <b>필터가 남은 이벤트를 전부 걸러낸다.</b>
     *
     * <p>버퍼 시작 ID 만 보던 판정은 이 방향을 못 잡아서(57 ≥ 1) 클라이언트가 아무것도 받지
     * 못한 채 조용히 낡았다.
     */
    @Test
    @DisplayName("순차 ID가 되감겨 클라이언트 ID가 더 크면 유실로 판정한다")
    void getEventsAfter_reportsLossWhenSequenceRewound() {
        publish(ROOM_A);
        publish(ROOM_A);

        ReplayResult result = buffer.getEventsAfter(ROOM_A, 57);

        assertThat(result.events()).isEmpty();
        assertThat(result.lostReason()).isEqualTo(SseEventsLostDto.SEQUENCE_RESET);
    }

    @Test
    @DisplayName("getAllEvents()는 버퍼에 남은 이벤트를 오래된 것부터 전부 반환한다")
    void getAllEvents_returnsAllInAscendingOrder() {
        publish(ROOM_A);
        publish(ROOM_A);

        assertThat(buffer.getAllEvents(ROOM_A)).extracting(BufferedEvent::id).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("clear()는 버퍼와 순차 ID 카운터를 함께 지운다")
    void clear_removesBufferAndSequence() {
        publish(ROOM_A);
        publish(ROOM_A);

        buffer.clear(ROOM_A);

        assertThat(buffer.getAllEvents(ROOM_A)).isEmpty();

        // 카운터까지 지워졌으면 다음 발행이 다시 1번부터 시작한다.
        publish(ROOM_A);
        assertThat(buffer.getAllEvents(ROOM_A).getFirst().id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("clear()는 해당 방만 삭제하고 다른 방에 영향을 주지 않는다")
    void clear_doesNotAffectOtherRooms() {
        publish(ROOM_A);
        publish(ROOM_B);

        buffer.clear(ROOM_A);

        assertThat(buffer.getAllEvents(ROOM_A)).isEmpty();
        assertThat(buffer.getAllEvents(ROOM_B)).hasSize(1);
    }

    /**
     * 스크립트를 거치지 않고 ZSET 에 직접 넣는다. 발행이 원자적이라 정상 경로로는 만들 수 없는
     * 배치(삽입 순서 ≠ ID 순서)를 읽는 쪽에 물려 보기 위한 것이다.
     */
    private void addDirectly(Long roomId, long id) {
        String body = objectMapper.writeValueAsString(
                new SseEventMessage(roomId, "EVENT", "data", CLOCK.instant()));

        redisTemplate.opsForZSet()
                .add(SseRedisKeys.events(roomId), SseEventEnvelope.encode(id, body), id);
    }
}
