package com.hot6ix.upbid.domain.auction.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.domain.auction.dto.request.AuctionItemAddRequestDto;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionItemStartRequestDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemDetailResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.domain.auction.service.AuctionItemService;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.product.repository.ProductRepository;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.redis.RedisDelayQueue;
import com.hot6ix.upbid.global.support.AbstractMySqlContainerTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 마감 예약이 낡은 시각으로 남았을 때 <b>물품이 실제로 닫히는지</b> 본다.
 *
 * <p>{@link AuctionRecoveryRunnerTest}는 목이라 "스케줄러를 불렀다"까지만 확인한다. 그런데
 * #327 에서 실제로 벌어진 일은 그 뒤였다 — 큐에 옛 시각이 남아 있으면 폴러가 물품을 아예
 * 집지 않아서, 마감 시각이 지나도 물품이 진행중으로 남았다. 그 구간은 진짜 Redis 와 진짜
 * DB 가 있어야 드러난다.
 *
 * <p>재현은 <b>DB 의 {@code end_at} 만 과거로 옮기는</b> 방식이다. 마감을 앞당겼는데 재예약이
 * 실패한 상태가 정확히 이것이다. 시각이 지나기를 기다리지 않아도 되고, 예약 유실이 어느
 * 경로에서 났는지와 무관하게 같은 상태가 된다.
 *
 * <p><b>자동 스케줄러는 꺼 둔다.</b> 주기를 한 시간으로 밀어 두고 폴러와 재동기화를 직접
 * 부른다. 배경에서 도는 채로 두면 "재동기화 전에는 안 닫힌다"를 확인하는 사이에 재동기화가
 * 끼어들어 결과가 그때그때 달라진다.
 */
@SpringBootTest
class AuctionCloseRecoveryIntegrationTest extends AbstractMySqlContainerTest {

    /** 마감이 비동기라 상태가 바뀔 때까지 기다린다. 로컬에서 한 건은 1초를 넘지 않는다. */
    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(10);

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS_CONTAINER =
            new GenericContainer<>(DockerImageName.parse("redis:8.10.0")).withExposedPorts(6379);

    static {
        REDIS_CONTAINER.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {

        // 스케줄러가 스스로 돌지 않게 밀어 둔다. 기동 직후 한 번은 도는데 그때는 이 테스트의
        // 물품이 아직 없다.
        registry.add("upbid.auction.close.poll-interval-ms", () -> 3_600_000);
        registry.add("upbid.auction.close.resync-interval-ms", () -> 3_600_000);

        // 컨텍스트를 띄우는 데만 필요한 값이다. 실제로 호출하지 않는다.
        registry.add("kakao.client-id", () -> "test");
        registry.add("kakao.redirect-uri", () -> "http://localhost/test");
        registry.add("ncp.sms.access-key", () -> "test");
        registry.add("ncp.sms.secret-key", () -> "test");
        registry.add("ncp.sms.service-id", () -> "test");
        registry.add("ncp.sms.send-from", () -> "01000000000");
    }

    @Autowired
    private AuctionItemService auctionItemService;

    @Autowired
    private AuctionRecoveryRunner auctionRecoveryRunner;

    @Autowired
    private AuctionClosePoller auctionClosePoller;

    @Autowired
    @Qualifier("closeDelayQueue")
    private RedisDelayQueue closeDelayQueue;

    @Autowired
    private AuctionItemRepository auctionItemRepository;

    @Autowired
    private AuctionRoomRepository auctionRoomRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SellerProfileRepository sellerProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SellerProfile sellerProfile;
    private AuctionRoom auctionRoom;

    private final List<Long> createdItemIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        sellerProfile = newSellerProfile();
        auctionRoom = newRoom(sellerProfile);
    }

    /**
     * 만든 물품을 지운다. <b>이 테스트는 트랜잭션을 커밋하므로 롤백으로 지워지지 않고,
     * MySQL 컨테이너는 테스트 클래스끼리 함께 쓴다.</b> 진행중으로 남겨 두면
     * {@code AuctionItemRepositoryTest}처럼 진행중 물품을 <b>전부</b> 세는 테스트가 깨진다.
     */
    @AfterEach
    void tearDown() {

        createdItemIds.forEach(itemId -> {
            closeDelayQueue.cancel(itemId);
            jdbcTemplate.update("delete from auction_items where auction_item_id = ?", itemId);
        });

        createdItemIds.clear();
    }

    @Test
    @DisplayName("예약이 낡은 시각으로 남으면 폴러만으로는 물품이 닫히지 않는다")
    void pollerAloneCannotCloseWhenScheduleIsStale() {

        Long itemId = startedItem();
        expireEndAt(itemId);

        auctionClosePoller.pollAndClose();

        // 큐에 걸린 시각이 아직 미래라 아무것도 집히지 않는다. #327 에서 물품이 원래 마감까지
        // 열려 있던 이유가 이것이다.
        assertThat(statusOf(itemId)).isEqualTo(AuctionItemStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("재동기화가 낡은 예약을 고쳐서 물품이 닫힌다")
    void resyncFixesStaleScheduleAndItemCloses() {

        Long itemId = startedItem();
        expireEndAt(itemId);

        auctionRecoveryRunner.restoreCloseSchedules();
        auctionClosePoller.pollAndClose();

        // 입찰이 없었으므로 유찰이다. 상태가 바뀌었다는 것은 폴러가 집어서 실제로 마감까지
        // 갔다는 뜻이다.
        assertThat(awaitClosed(itemId)).isEqualTo(AuctionItemStatus.FAILED);
    }

    @Test
    @DisplayName("예약이 통째로 사라져도 재동기화가 채워서 물품이 닫힌다")
    void resyncRefillsMissingScheduleAndItemCloses() {

        Long itemId = startedItem();
        expireEndAt(itemId);
        closeDelayQueue.cancel(itemId);

        auctionRecoveryRunner.restoreCloseSchedules();
        auctionClosePoller.pollAndClose();

        assertThat(awaitClosed(itemId)).isEqualTo(AuctionItemStatus.FAILED);
    }

    /**
     * 물품을 시작한다. 커밋이 끝나면 리스너가 마감 예약을 <b>미래 시각으로</b> 걸어 둔다.
     * 이 예약이 뒤이은 {@link #expireEndAt}과 어긋나면서 낡은 예약이 된다.
     */
    private Long startedItem() {

        AuctionItemDetailResponseDto item = auctionItemService.add(userId(), roomId(),
                new AuctionItemAddRequestDto(newProduct().getProductId(), 10_000L));

        auctionItemService.start(item.auctionItemId(), userId(), new AuctionItemStartRequestDto(10));

        createdItemIds.add(item.auctionItemId());

        return item.auctionItemId();
    }

    /**
     * 마감 시각만 과거로 옮긴다. <b>큐는 건드리지 않는다.</b> 그래야 DB 와 예약이 어긋난
     * 상태, 곧 앞당기기 재예약이 실패했을 때와 같은 상태가 된다.
     *
     * <p>엔티티에 {@code end_at} 을 임의로 바꾸는 길이 없어 SQL 로 직접 고친다. 시각이
     * 지나기를 기다리는 것과 같은 상태를 만들면서 테스트가 10분을 쓰지 않게 하려는 것이다.
     */
    private void expireEndAt(Long auctionItemId) {
        jdbcTemplate.update("update auction_items set end_at = ? where auction_item_id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)), auctionItemId);
    }

    /** 마감은 실행 풀에서 도므로 상태가 바뀔 때까지 기다린다. */
    private AuctionItemStatus awaitClosed(Long auctionItemId) {

        long deadline = System.nanoTime() + CLOSE_TIMEOUT.toNanos();

        while (System.nanoTime() < deadline) {

            AuctionItemStatus status = statusOf(auctionItemId);

            if (status != AuctionItemStatus.IN_PROGRESS) {
                return status;
            }
            sleepBriefly();
        }

        return statusOf(auctionItemId);
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("마감을 기다리다 중단됐다", e);
        }
    }

    private AuctionItemStatus statusOf(Long auctionItemId) {
        return auctionItemRepository.findById(auctionItemId)
                .map(AuctionItem::getStatus)
                .orElseThrow();
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
                .shareCode("close-recovery-" + System.nanoTime())
                .status(AuctionRoomStatus.BEFORE)
                .bidIncrement(1_000L)
                .softCloseTriggerSeconds(60)
                .softCloseExtendSeconds(60)
                .build());
    }

    private SellerProfile newSellerProfile() {

        User user = userRepository.saveAndFlush(User.builder()
                .email("close-recovery-" + System.nanoTime() + "@hot6ix.com")
                .password("password")
                .nickname("승민")
                .phoneNumber("010-0000-0022")
                .build());

        return sellerProfileRepository.saveAndFlush(SellerProfile.builder()
                .user(user)
                .storeName("승민 스토어")
                .build());
    }
}
