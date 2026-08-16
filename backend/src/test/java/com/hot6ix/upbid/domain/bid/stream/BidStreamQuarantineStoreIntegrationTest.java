package com.hot6ix.upbid.domain.bid.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hot6ix.upbid.global.support.AbstractRedisContainerTest;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

class BidStreamQuarantineStoreIntegrationTest extends AbstractRedisContainerTest {

    private static final String STREAM = "auction:bid:stream";
    private static final String QUARANTINE = STREAM + ":quarantine";
    private static final String INDEX = QUARANTINE + ":index";

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    private BidStreamQuarantineStore store;

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = redisConnectionFactory();
        redis = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void closeRedis() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        try (RedisConnection connection = redis.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushAll();
        }
        store = new BidStreamQuarantineStore(redis, new ObjectMapper());
    }

    @Test
    @DisplayName("같은 원본 이벤트를 여러 번 격리해도 격리 Stream에는 한 번만 기록한다")
    void quarantinesOriginalRecordOnce() {
        RecordId originalId = RecordId.of("1786636800000-0");
        Map<String, String> fields = Map.of(
                "type", "BID_ACCEPTED",
                "requestId", "request-1",
                "amount", "10000");
        BidStreamFailure failure = new BidStreamFailure(
                BidStreamFailure.Kind.PERMANENT,
                BidStreamFailureCode.EVENT_FORMAT_INVALID);
        RuntimeException error = new RuntimeException("필수 필드가 없다");

        BidStreamQuarantineStore.QuarantineResult first = store.quarantine(
                STREAM, originalId, fields, failure, 1, "writer-1",
                1_786_636_805_000L, error);
        BidStreamQuarantineStore.QuarantineResult duplicate = store.quarantine(
                STREAM, originalId, fields, failure, 2, "writer-2",
                1_786_636_865_000L, error);

        assertThat(first.created()).isTrue();
        assertThat(duplicate.created()).isFalse();
        assertThat(duplicate.recordId()).isEqualTo(first.recordId());
        assertThat(redis.opsForStream().size(QUARANTINE)).isEqualTo(1L);
        assertThat(redis.opsForHash().get(INDEX, originalId.getValue()))
                .isEqualTo(first.recordId());

        MapRecord<String, Object, Object> quarantined = redis.opsForStream()
                .range(QUARANTINE, Range.unbounded()).getFirst();
        assertThat(quarantined.getValue())
                .containsEntry("originalStreamKey", STREAM)
                .containsEntry("originalRecordId", originalId.getValue())
                .containsEntry("eventType", "BID_ACCEPTED")
                .containsEntry("failureKind", "PERMANENT")
                .containsEntry("failureCode", "EVENT_FORMAT_INVALID")
                .containsEntry("deliveryCount", "1")
                .containsEntry("quarantinedAt", "1786636805000")
                .containsEntry("consumerName", "writer-1");
        assertThat(String.valueOf(quarantined.getValue().get("rawFields")))
                .contains("request-1", "10000");
    }

    @Test
    @DisplayName("격리 Key 타입이 잘못되면 격리 원문과 Index를 부분 생성하지 않는다")
    void rejectsWrongKeyTypeBeforeWriting() {
        redis.opsForValue().set(QUARANTINE, "wrong-type");

        assertThatThrownBy(() -> store.quarantine(
                STREAM,
                RecordId.of("1786636800000-0"),
                Map.of("type", "BID_ACCEPTED"),
                new BidStreamFailure(
                        BidStreamFailure.Kind.RETRY_EXHAUSTED,
                        BidStreamFailureCode.RETRY_EXHAUSTED),
                5,
                "writer-1",
                1_786_636_805_000L,
                new RuntimeException("DB unavailable")))
                .isInstanceOf(RuntimeException.class);

        assertThat(redis.hasKey(INDEX)).isFalse();
        assertThat(redis.opsForValue().get(QUARANTINE)).isEqualTo("wrong-type");
    }
}
