package com.hot6ix.upbid.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.hot6ix.upbid.domain.auction.config.AuctionProperties;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemResultResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomResultResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import com.hot6ix.upbid.global.support.AbstractRedisContainerTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 캐시 자체의 왕복·TTL·장애 시 동작만 본다. "CLOSED일 때만 저장한다"는 규칙과 내 순위 병합은
 * {@link AuctionRoomService}가 결정하는 몫이라 {@code AuctionRoomServiceTest}에서 본다.
 */
class AuctionResultCacheTest extends AbstractRedisContainerTest {

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = redisConnectionFactory();
        redisTemplate = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void tearDownRedis() {
        connectionFactory.destroy();
    }

    private AuctionResultCache newCache(boolean enabled, Duration ttl) {
        AuctionProperties properties = new AuctionProperties(
                0, null, null, new AuctionProperties.ResultCache(enabled, ttl));

        return new AuctionResultCache(redisTemplate, objectMapper, properties, new SimpleMeterRegistry());
    }

    private AuctionRoomResultResponseDto sampleResult() {
        List<AuctionItemResultResponseDto> items = List.of(
                new AuctionItemResultResponseDto(
                        1L, "한정판 피규어", "https://cdn.hot6ix.com/1.png",
                        AuctionItemStatus.SOLD, 85_000L, "스니커홀릭", null, null),
                new AuctionItemResultResponseDto(
                        2L, "빈티지 자켓", null,
                        AuctionItemStatus.FAILED, null, null, null, null));

        return new AuctionRoomResultResponseDto(
                10L, "승민의 경매방", "승민상점", AuctionRoomStatus.CLOSED,
                LocalDateTime.of(2026, 8, 15, 12, 0, 0), items);
    }

    @Test
    @DisplayName("저장된 적 없는 방은 미스다")
    void find_returnsEmpty_whenMissing() {
        AuctionResultCache cache = newCache(true, Duration.ofMinutes(1));

        assertThat(cache.find("no-such-code")).isEmpty();
    }

    @Test
    @DisplayName("저장한 값이 closedAt·status·null 필드까지 그대로 돌아온다")
    void roundTrip_keepsClosedAtAndStatusAndNullFields() {
        AuctionResultCache cache = newCache(true, Duration.ofMinutes(1));
        AuctionRoomResultResponseDto original = sampleResult();

        cache.put("round-trip", original);
        Optional<AuctionRoomResultResponseDto> found = cache.find("round-trip");

        assertThat(found).contains(original);
        assertThat(found.get().closedAt()).isEqualTo(original.closedAt());
        assertThat(found.get().status()).isEqualTo(AuctionRoomStatus.CLOSED);
        assertThat(found.get().items().getFirst().myRank()).isNull();
        assertThat(found.get().items().getLast().imageUrl()).isNull();
    }

    @Test
    @DisplayName("저장한 값에 설정된 TTL이 실제로 걸린다")
    void put_setsTtl() {
        AuctionResultCache cache = newCache(true, Duration.ofMinutes(5));

        cache.put("ttl-code", sampleResult());

        Long expireSeconds = redisTemplate.getExpire("upbid:auction:results:ttl-code");

        assertThat(expireSeconds).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(5).toSeconds());
    }

    @Test
    @DisplayName("비활성화되면 저장도 조회도 하지 않는다")
    void disabled_doesNothing() {
        AuctionResultCache cache = newCache(false, Duration.ofMinutes(1));

        cache.put("disabled-code", sampleResult());

        assertThat(cache.find("disabled-code")).isEmpty();
        assertThat(redisTemplate.hasKey("upbid:auction:results:disabled-code")).isFalse();
    }

    @Test
    @DisplayName("Redis에 연결할 수 없으면 find는 빈 값, put은 예외를 던지지 않는다")
    void redisUnreachable_findIsEmptyAndPutDoesNotThrow() {
        LettuceConnectionFactory brokenFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("localhost", 1));
        brokenFactory.afterPropertiesSet();
        StringRedisTemplate brokenTemplate = new StringRedisTemplate(brokenFactory);

        AuctionResultCache cache = new AuctionResultCache(
                brokenTemplate, objectMapper,
                new AuctionProperties(0, null, null,
                        new AuctionProperties.ResultCache(true, Duration.ofMinutes(1))),
                new SimpleMeterRegistry());

        try {
            assertThat(cache.find("unreachable")).isEmpty();
            assertThatCode(() -> cache.put("unreachable", sampleResult())).doesNotThrowAnyException();
        } finally {
            brokenFactory.destroy();
        }
    }
}
