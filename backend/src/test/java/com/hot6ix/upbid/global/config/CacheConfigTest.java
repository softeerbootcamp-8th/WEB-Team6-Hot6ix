package com.hot6ix.upbid.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.domain.auction.dto.cache.AuctionRoomPublicSnapshot;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomPublicResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import com.hot6ix.upbid.domain.auction.service.AuctionRoomPublicCacheService;
import com.hot6ix.upbid.global.support.AbstractRedisContainerTest;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * 캐시에 담은 값이 그대로 돌아오는지 진짜 Redis로 확인한다.
 *
 * <p>목으로는 못 잡는 것을 본다. 값을 JSON으로 담기 때문에 record 안의 {@code LocalDateTime}과
 * enum, 그리고 중첩된 record가 꺼낼 때 원래 타입으로 돌아오지 않으면 조회가 통째로 깨진다.
 * 그런데 그건 컴파일에 안 걸리고 캐시가 처음 맞는 순간에야 드러난다.
 */
class CacheConfigTest extends AbstractRedisContainerTest {

    private LettuceConnectionFactory connectionFactory;
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        connectionFactory = redisConnectionFactory();
        cacheManager = new CacheConfig(Duration.ofMinutes(5)).cacheManager(connectionFactory);
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    @DisplayName("방 스냅샷을 담았다 꺼내면 필드가 그대로다")
    void roundTripSnapshot() {

        AuctionRoomPublicSnapshot snapshot = newSnapshot();

        Cache cache = cacheManager.getCache(AuctionRoomPublicCacheService.ROOM_PUBLIC_CACHE);
        cache.put("aBcD1234aBcD1234", snapshot);

        AuctionRoomPublicSnapshot found =
                cache.get("aBcD1234aBcD1234", AuctionRoomPublicSnapshot.class);

        assertThat(found).isEqualTo(snapshot);
        assertThat(found.room().createdAt()).isEqualTo(LocalDateTime.of(2026, 8, 13, 19, 30, 15));
        assertThat(found.room().status()).isEqualTo(AuctionRoomStatus.OPEN);
    }

    private AuctionRoomPublicSnapshot newSnapshot() {
        return new AuctionRoomPublicSnapshot(
                AuctionRoomPublicResponseDto.builder()
                        .auctionRoomId(10L)
                        .shareCode("aBcD1234aBcD1234")
                        .name("승민의 경매방")
                        .description("설명")
                        .status(AuctionRoomStatus.OPEN)
                        .bidIncrement(1_000L)
                        .softCloseTriggerSeconds(60)
                        .softCloseExtendSeconds(60)
                        .sellerStoreName("승민상점")
                        .createdAt(LocalDateTime.of(2026, 8, 13, 19, 30, 15))
                        .closedAt(null)
                        .itemCount(3L)
                        .isOwner(false)
                        .agreedToTerms(null)
                        .build(),
                7L);
    }

    @Test
    @DisplayName("지운 값은 다시 안 나온다")
    void evict() {

        Cache cache = cacheManager.getCache(AuctionRoomPublicCacheService.ROOM_PUBLIC_CACHE);
        cache.put("aBcD1234aBcD1234", newSnapshot());

        cache.evict("aBcD1234aBcD1234");

        assertThat(cache.get("aBcD1234aBcD1234")).isNull();
    }
}
