package com.hot6ix.upbid.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.domain.auction.dto.request.AuctionItemAddRequestDto;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionItemStartRequestDto;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomUpdateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemDetailResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomPublicResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.product.repository.ProductRepository;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.support.AbstractMySqlContainerTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 공개 조회 캐시가 <b>실제로 지워지는지</b> 본다.
 *
 * <p>{@link AuctionRoomPublicCacheServiceTest}는 목이라 "evict 를 불렀다"까지만 확인한다.
 * 그런데 이 캐시에서 무서운 것은 <b>부르는 걸 빠뜨렸거나 커밋 전에 불러서 옛 값이 도로
 * 담기는 것</b>이고, 그건 진짜 Redis 와 진짜 트랜잭션이 있어야 드러난다. 여기서 그걸 본다.
 *
 * <p>확인하는 지점은 넷이다. 방 수정, 물품 추가, 첫 물품 시작, 방 종료. 이슈(#320)에는 둘로
 * 적혀 있었는데 실제로는 넷이었다 — 물품 수와 방 상태도 응답에 들어 있기 때문이다.
 */
@SpringBootTest
class AuctionRoomPublicCacheIntegrationTest extends AbstractMySqlContainerTest {

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS_CONTAINER =
            new GenericContainer<>(DockerImageName.parse("redis:8.10.0")).withExposedPorts(6379);

    static {
        REDIS_CONTAINER.start();
    }

    /** 컨텍스트를 띄우는 데만 필요한 값이다. 실제로 호출하지 않는다. */
    @DynamicPropertySource
    static void externalCredentials(DynamicPropertyRegistry registry) {
        registry.add("kakao.client-id", () -> "test");
        registry.add("kakao.redirect-uri", () -> "http://localhost/test");
        registry.add("ncp.sms.access-key", () -> "test");
        registry.add("ncp.sms.secret-key", () -> "test");
        registry.add("ncp.sms.service-id", () -> "test");
        registry.add("ncp.sms.send-from", () -> "01000000000");
    }

    @Autowired
    private AuctionRoomService auctionRoomService;

    @Autowired
    private AuctionRoomCloseService auctionRoomCloseService;

    @Autowired
    private AuctionItemService auctionItemService;

    @Autowired
    private AuctionRoomRepository auctionRoomRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SellerProfileRepository sellerProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    private SellerProfile sellerProfile;
    private AuctionRoom auctionRoom;

    @BeforeEach
    void setUp() {
        sellerProfile = newSellerProfile();
        auctionRoom = newRoom(sellerProfile);
    }

    @Test
    @DisplayName("한 번 조회하면 캐시에 담기고 두 번째 조회는 DB를 안 본다")
    void caches() {

        auctionRoomService.getRoomByShareCode(shareCode(), null);

        assertThat(cacheManager.getCache(AuctionRoomPublicCacheService.ROOM_PUBLIC_CACHE)
                .get(shareCode()))
                .as("공유 코드로 담긴다")
                .isNotNull();

        // 캐시를 두고 DB 행을 몰래 바꾼다. 캐시를 안 탔다면 바뀐 이름이 나올 것이다.
        renameOutsideOfService("몰래 바꾼 이름");

        assertThat(auctionRoomService.getRoomByShareCode(shareCode(), null).name())
                .as("두 번째 조회는 캐시에서 나온다")
                .isEqualTo("승민의 경매방");
    }

    /**
     * 만료를 기다리지 않고 키에 걸린 시간을 본다. 5분을 기다릴 수는 없고, 확인하려는 것은
     * "TTL 설정이 실제로 먹었는가"이지 Redis 가 시간을 지키는지가 아니다.
     */
    @Test
    @DisplayName("담긴 키에 TTL 5분이 걸려 있다")
    void hasTimeToLive() {

        auctionRoomService.getRoomByShareCode(shareCode(), null);

        Long ttl = redisTemplate.getExpire(
                AuctionRoomPublicCacheService.ROOM_PUBLIC_CACHE + "::" + shareCode());

        assertThat(ttl).isBetween(240L, 300L);
    }

    @Test
    @DisplayName("한 번 맞으면 히트로 세어진다")
    void countsHits() {

        auctionRoomService.getRoomByShareCode(shareCode(), null);
        auctionRoomService.getRoomByShareCode(shareCode(), null);

        assertThat(cacheGets("hit"))
                .as("두 번 읽었으니 한 번은 맞아야 한다. 배포에서 캐시가 정말 맞고 있는지 볼 지표다")
                .isGreaterThanOrEqualTo(1);
        assertThat(cacheGets("miss"))
                .as("첫 조회는 빗나간다")
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("경매방을 수정하면 다음 조회에 새 이름이 나온다")
    void evictsOnUpdate() {

        auctionRoomService.getRoomByShareCode(shareCode(), null);

        auctionRoomService.update(userId(), roomId(),
                new AuctionRoomUpdateRequestDto("고친 경매방", null, null, null, null, null));

        assertThat(auctionRoomService.getRoomByShareCode(shareCode(), null).name())
                .isEqualTo("고친 경매방");
    }

    @Test
    @DisplayName("물품을 추가하면 다음 조회에 물품 수가 늘어난다")
    void evictsOnItemAdd() {

        assertThat(auctionRoomService.getRoomByShareCode(shareCode(), null).itemCount()).isZero();

        auctionItemService.add(userId(), roomId(),
                new AuctionItemAddRequestDto(newProduct().getProductId(), 10_000L));

        assertThat(auctionRoomService.getRoomByShareCode(shareCode(), null).itemCount())
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("첫 물품을 시작하면 다음 조회에 방이 방송 중으로 나온다")
    void evictsOnFirstItemStart() {

        AuctionItemDetailResponseDto item = auctionItemService.add(userId(), roomId(),
                new AuctionItemAddRequestDto(newProduct().getProductId(), 10_000L));

        assertThat(auctionRoomService.getRoomByShareCode(shareCode(), null).status())
                .isEqualTo(AuctionRoomStatus.BEFORE);

        auctionItemService.start(item.auctionItemId(), userId(), new AuctionItemStartRequestDto(10));

        assertThat(auctionRoomService.getRoomByShareCode(shareCode(), null).status())
                .isEqualTo(AuctionRoomStatus.OPEN);
    }

    @Test
    @DisplayName("경매방을 종료하면 다음 조회에 종료로 나온다")
    void evictsOnClose() {

        auctionRoomService.getRoomByShareCode(shareCode(), null);

        auctionRoomCloseService.close(userId(), roomId());

        AuctionRoomPublicResponseDto response =
                auctionRoomService.getRoomByShareCode(shareCode(), null);

        assertThat(response.status()).isEqualTo(AuctionRoomStatus.CLOSED);
        assertThat(response.closedAt()).isNotNull();
    }

    /**
     * 서비스를 거치지 않고 DB 행만 바꾼다. 캐시를 안 지우므로, 다음 조회가 옛 값을 주면
     * 캐시를 탄 것이고 새 값을 주면 캐시를 안 탄 것이다.
     */
    private void renameOutsideOfService(String name) {
        AuctionRoom found = auctionRoomRepository.findById(roomId()).orElseThrow();
        found.update(new AuctionRoomUpdateRequestDto(name, null, null, null, null, null));
        auctionRoomRepository.saveAndFlush(found);
    }

    private double cacheGets(String result) {
        return meterRegistry.get("cache.gets")
                .tag("cache", AuctionRoomPublicCacheService.ROOM_PUBLIC_CACHE)
                .tag("result", result)
                .functionCounter()
                .count();
    }

    private String shareCode() {
        return auctionRoom.getShareCode();
    }

    private Long roomId() {
        return auctionRoom.getAuctionRoomId();
    }

    private Long userId() {
        return sellerProfile.getUser().getUserId();
    }

    private Product newProduct() {
        return productRepository.saveAndFlush(Product.builder()
                .sellerProfile(sellerProfile)
                .name("한정판 피규어")
                .description("미개봉 정품")
                .imageUrl("https://cdn.hot6ix.com/item.png")
                .referenceUrl("https://instagram.com/hot6ix")
                .build());
    }

    private AuctionRoom newRoom(SellerProfile owner) {
        return auctionRoomRepository.saveAndFlush(AuctionRoom.builder()
                .sellerProfile(owner)
                .name("승민의 경매방")
                .shareCode("cache-" + System.nanoTime())
                .status(AuctionRoomStatus.BEFORE)
                .bidIncrement(1_000L)
                .softCloseTriggerSeconds(60)
                .softCloseExtendSeconds(60)
                .build());
    }

    private SellerProfile newSellerProfile() {
        User user = userRepository.saveAndFlush(User.builder()
                .email("cache-" + System.nanoTime() + "@hot6ix.com")
                .password("password")
                .nickname("승민")
                .phoneNumber("010-0000-0011")
                .build());

        return sellerProfileRepository.saveAndFlush(SellerProfile.builder()
                .user(user)
                .storeName("승민 스토어")
                .build());
    }
}
