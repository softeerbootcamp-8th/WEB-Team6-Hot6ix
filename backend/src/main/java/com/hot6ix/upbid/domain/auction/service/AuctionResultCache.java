package com.hot6ix.upbid.domain.auction.service;

import com.hot6ix.upbid.domain.auction.config.AuctionProperties;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomResultResponseDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 종료된 경매방 결과 조회의 <b>공개 부분</b>을 Redis에 캐시한다(#329). 요청자의 순위 같은
 * 개인화된 값은 여기 담기지 않는다 — {@link AuctionRoomService}가 히트한 값 위에 매번
 * 새로 채운다.
 *
 * <p>{@code SseEventBuffer}와 같은 패턴이다. Spring Cache({@code @Cacheable})를 새로
 * 들이지 않고 {@code StringRedisTemplate}을 직접 쓴다 — "방이 CLOSED일 때만 저장한다"는
 * 규칙이 애너테이션의 조건식보다 코드로 표현하기 쉽다.
 *
 * <p>Redis가 죽으면 캐시가 없는 것으로 본다. 결과 조회는 캐시가 없어도 DB로 그대로 되므로,
 * 예외를 밖으로 내보내지 않고 미스로 떨어뜨린다.
 */
@Slf4j
@Component
public class AuctionResultCache {

    private static final String KEY_PREFIX = "upbid:auction:results:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AuctionProperties.ResultCache properties;
    private final Counter hitCounter;
    private final Counter missCounter;

    public AuctionResultCache(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            AuctionProperties auctionProperties,
            MeterRegistry registry) {

        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.properties = auctionProperties.resultCache();

        // 첫 요청 전에도 시계열이 보여야 run.sh 의 index.csv 칸이 NaN 을 안 받는다
        // (DealMetrics 가 award 타이머를 생성자에서 미리 만드는 것과 같은 이유).
        this.hitCounter = Counter.builder("upbid.auction.result.cache").tag("result", "hit").register(registry);
        this.missCounter = Counter.builder("upbid.auction.result.cache").tag("result", "miss").register(registry);
    }

    /**
     * 캐시에서 비로그인 응답을 읽는다.
     *
     * <p>비활성·미스·Redis 예외를 전부 빈 값으로 합친다. 호출하는 쪽({@link AuctionRoomService})은
     * "DB로 가야 하는 이유"를 구분할 필요가 없다 — 어느 경우든 똑같이 다시 조회하면 된다.
     */
    public Optional<AuctionRoomResultResponseDto> find(String shareCode) {
        if (!properties.enabled()) {
            return Optional.empty();
        }

        try {
            String json = stringRedisTemplate.opsForValue().get(key(shareCode));

            if (json == null) {
                missCounter.increment();
                return Optional.empty();
            }

            hitCounter.increment();
            return Optional.of(objectMapper.readValue(json, AuctionRoomResultResponseDto.class));

        } catch (RuntimeException e) {
            log.warn("경매 결과 캐시 조회 실패라 DB로 간다: shareCode={}", shareCode, e);
            missCounter.increment();
            return Optional.empty();
        }
    }

    /**
     * 방이 {@code CLOSED}일 때만 부른다({@link AuctionRoomService}). 저장 실패는 로그만
     * 남기고 삼킨다 — 캐시를 못 채웠다고 결과 조회 응답 자체가 실패하면 안 된다.
     */
    public void put(String shareCode, AuctionRoomResultResponseDto guestView) {
        if (!properties.enabled()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(guestView);
            stringRedisTemplate.opsForValue().set(key(shareCode), json, properties.ttl());

        } catch (RuntimeException e) {
            log.warn("경매 결과 캐시 저장 실패: shareCode={}", shareCode, e);
        }
    }

    private String key(String shareCode) {
        return KEY_PREFIX + shareCode;
    }
}
