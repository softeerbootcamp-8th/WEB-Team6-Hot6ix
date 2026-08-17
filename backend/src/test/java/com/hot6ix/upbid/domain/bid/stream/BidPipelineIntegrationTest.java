package com.hot6ix.upbid.domain.bid.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisKeys;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisSeed;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisParticipant;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisStore;
import com.hot6ix.upbid.domain.bid.config.BidStreamProperties;
import com.hot6ix.upbid.domain.bid.repository.BidRepository;
import com.hot6ix.upbid.domain.bid.service.BidStreamPersistenceService;
import com.hot6ix.upbid.domain.bid.store.RedisBidDecision;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.product.repository.ProductRepository;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.config.ClockConfig;
import com.hot6ix.upbid.global.config.JpaConfig;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import com.hot6ix.upbid.global.support.AbstractMySqlContainerTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, ClockConfig.class, BidStreamPersistenceService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@SuppressWarnings("unchecked")
class BidPipelineIntegrationTest extends AbstractMySqlContainerTest {

    private static final String STREAM = "auction:bid:stream";
    private static final String QUARANTINE_STREAM = STREAM + ":quarantine";
    private static final String QUARANTINE_INDEX = QUARANTINE_STREAM + ":index";
    private static final String GROUP = "bid-mysql-writers-integration";
    private static final String CONSUMER = "integration-writer";

    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:8.10.0")).withExposedPorts(6379);

    private static LettuceConnectionFactory redisConnectionFactory;
    private static StringRedisTemplate redis;

    @Autowired
    private BidStreamPersistenceService persistenceService;
    @Autowired
    private BidRepository bidRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SellerProfileRepository sellerProfileRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private AuctionRoomRepository auctionRoomRepository;
    @Autowired
    private AuctionItemRepository auctionItemRepository;

    @MockitoBean
    private DomainEventPublisher domainEventPublisher;

    private AuctionRedisStore store;
    private BidStreamProperties properties;
    private final List<Long> createdItemIds = new ArrayList<>();
    private final List<Long> createdRoomIds = new ArrayList<>();
    private final List<Long> createdProductIds = new ArrayList<>();
    private final List<Long> createdSellerProfileIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        redisConnectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(6379)));
        redisConnectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(redisConnectionFactory);
    }

    @AfterAll
    static void stopRedis() {
        redisConnectionFactory.destroy();
        REDIS.stop();
    }

    @BeforeEach
    void setUp() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        properties = new BidStreamProperties(
                STREAM, GROUP, CONSUMER, Duration.ofMillis(10), Duration.ofMillis(1),
                Duration.ofSeconds(60), Duration.ZERO, 5, Duration.ZERO, 20,
                true, 50, Duration.ofSeconds(1));
        redis.execute((RedisCallback<String>) connection ->
                connection.streamCommands().xGroupCreate(
                        STREAM.getBytes(StandardCharsets.UTF_8), GROUP,
                        ReadOffset.from("0-0"), true));
        store = new AuctionRedisStore(redis, new BidStreamMetrics(new SimpleMeterRegistry()));
    }

    @AfterEach
    void cleanCommittedFixtures() {
        bidRepository.deleteAllInBatch(bidRepository.findAll().stream()
                .filter(bid -> bid.getRequestId() != null && bid.getRequestId().startsWith("pipeline-"))
                .toList());
        auctionItemRepository.deleteAllByIdInBatch(createdItemIds);
        auctionRoomRepository.deleteAllByIdInBatch(createdRoomIds);
        productRepository.deleteAllByIdInBatch(createdProductIds);
        sellerProfileRepository.deleteAllByIdInBatch(createdSellerProfileIds);
        userRepository.deleteAllByIdInBatch(createdUserIds);
    }

    @Test
    @DisplayName("Lua 승인 후 앱 Consumer가 다시 만들어져도 Stream 이벤트를 MySQL에 한 번 저장한다")
    void persistsApprovedBidAfterConsumerRestart() {
        Fixture fixture = fixture();
        seed(fixture);

        RedisBidDecision decision = store.evaluateBid(
                fixture.item().getAuctionItemId(), "pipeline-request-1",
                fixture.bidder().getUserId(), 10_000L);

        assertThat(decision).isInstanceOf(RedisBidDecision.Accepted.class);
        assertThat(bidRepository.findByRequestId("pipeline-request-1")).isEmpty();

        // 앱 재시작을 흉내 내 새 Consumer 인스턴스로 기존 Stream을 이어 읽는다.
        consumer().poll();

        assertThat(bidRepository.findByRequestId("pipeline-request-1"))
                .isPresent()
                .get()
                .satisfies(bid -> {
                    assertThat(bid.getAmount()).isEqualTo(10_000L);
                    assertThat(bid.getBidder().getUserId()).isEqualTo(fixture.bidder().getUserId());
                });
        assertThat(redis.opsForStream().size(STREAM)).isZero();
    }

    @Test
    @DisplayName("MySQL 커밋 후 ACK 전에 종료되어도 pending 재처리는 입찰을 중복 저장하지 않는다")
    void reprocessesPendingIdempotentlyAfterCommitBeforeAck() {
        Fixture fixture = fixture();
        seed(fixture);
        RedisBidDecision.Accepted accepted = (RedisBidDecision.Accepted) store.evaluateBid(
                fixture.item().getAuctionItemId(), "pipeline-ack-gap",
                fixture.bidder().getUserId(), 10_000L);

        List<MapRecord<String, Object, Object>> delivered = redis.opsForStream().read(
                Consumer.from(GROUP, "crashed-writer"),
                StreamReadOptions.empty().count(1),
                StreamOffset.create(STREAM, ReadOffset.lastConsumed()));
        assertThat(delivered).hasSize(1);

        // DB 커밋은 끝났지만 ACK 호출 전에 프로세스가 죽은 상황을 만든다.
        persistenceService.persist(acceptedEvent(fixture, "pipeline-ack-gap", accepted));

        consumer().poll();

        assertThat(bidRepository.findAll().stream()
                .filter(bid -> "pipeline-ack-gap".equals(bid.getRequestId())))
                .hasSize(1);
        assertThat(redis.opsForStream().size(STREAM)).isZero();
    }

    @Test
    @DisplayName("같은 requestId를 동시에 재전송해도 승인 결과와 Stream과 DB는 각각 하나다")
    void deduplicatesConcurrentRetriesEndToEnd() throws Exception {
        Fixture fixture = fixture();
        seed(fixture);
        Callable<RedisBidDecision> request = () -> store.evaluateBid(
                fixture.item().getAuctionItemId(), "pipeline-duplicate",
                fixture.bidder().getUserId(), 10_000L);

        try (var executor = Executors.newFixedThreadPool(8)) {
            List<RedisBidDecision> decisions = executor.invokeAll(java.util.Collections.nCopies(20, request))
                    .stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .toList();

            assertThat(decisions).allSatisfy(decision -> {
                assertThat(decision).isInstanceOf(RedisBidDecision.Accepted.class);
                RedisBidDecision.Accepted accepted = (RedisBidDecision.Accepted) decision;
                assertThat(accepted.requestId()).isEqualTo("pipeline-duplicate");
                assertThat(accepted.amount()).isEqualTo(10_000L);
            });
        }

        assertThat(redis.opsForStream().size(STREAM)).isEqualTo(1L);
        consumer().poll();
        assertThat(bidRepository.findAll().stream()
                .filter(bid -> "pipeline-duplicate".equals(bid.getRequestId())))
                .hasSize(1);
    }

    @Test
    @DisplayName("높은 입찰을 먼저 DB에 반영한 뒤 낮은 입찰이 도착해도 현재가는 내려가지 않는다")
    void doesNotRegressCurrentPriceWhenDatabaseOrderIsReversed() {
        Fixture fixture = fixture();
        User highBidder = userRepository.saveAndFlush(User.builder()
                .email("pipeline-high-" + System.nanoTime() + "@hot6ix.com")
                .nickname("고액 입찰자")
                .build());
        createdUserIds.add(highBidder.getUserId());
        long endAt = toMillis(fixture.item().getEndAt());
        long acceptedAt = System.currentTimeMillis();

        persistenceService.persist(new BidStreamEvent.BidAccepted(
                "pipeline-high", fixture.item().getAuctionItemId(), fixture.room().getAuctionRoomId(),
                highBidder.getUserId(), 20_000L, acceptedAt + 1, endAt + 60_000L, 60, 60));
        persistenceService.persist(new BidStreamEvent.BidAccepted(
                "pipeline-low", fixture.item().getAuctionItemId(), fixture.room().getAuctionRoomId(),
                fixture.bidder().getUserId(), 10_000L, acceptedAt, endAt, 0, 0));

        AuctionItem reloaded = auctionItemRepository.findById(fixture.item().getAuctionItemId()).orElseThrow();
        assertThat(reloaded.getCurrentPrice()).isEqualTo(20_000L);
        assertThat(reloaded.getLeaderUser().getUserId()).isEqualTo(highBidder.getUserId());
    }

    @Test
    @DisplayName("두 입찰과 마감을 한 poll에서 Stream 순서대로 MySQL에 반영한다")
    void drainsBidsAndCloseInOnePoll() {
        Fixture fixture = fixture();
        seed(fixture);
        User secondBidder = userRepository.saveAndFlush(User.builder()
                .email("pipeline-second-" + System.nanoTime() + "@hot6ix.com")
                .nickname("두 번째 입찰자")
                .build());
        createdUserIds.add(secondBidder.getUserId());
        store.addParticipant(
                fixture.room().getAuctionRoomId(),
                secondBidder.getUserId(),
                secondBidder.getNickname());

        store.evaluateBid(
                fixture.item().getAuctionItemId(), "pipeline-first",
                fixture.bidder().getUserId(), 10_000L);
        assertThat(store.evaluateBid(
                fixture.item().getAuctionItemId(), "pipeline-second",
                secondBidder.getUserId(), 20_000L))
                .isInstanceOf(RedisBidDecision.Accepted.class);
        redis.opsForHash().put(
                AuctionRedisKeys.item(fixture.item().getAuctionItemId()),
                "endAt",
                String.valueOf(System.currentTimeMillis() - 1_000L));
        store.requestNaturalClose(fixture.item().getAuctionItemId());

        consumer().poll();

        AuctionItem closed = auctionItemRepository.findById(fixture.item().getAuctionItemId()).orElseThrow();
        assertThat(bidRepository.findByRequestId("pipeline-first")).isPresent();
        assertThat(bidRepository.findByRequestId("pipeline-second")).isPresent();
        assertThat(closed.getStatus()).isEqualTo(AuctionItemStatus.SOLD);
        assertThat(closed.getLeaderUser().getUserId()).isEqualTo(secondBidder.getUserId());
        assertThat(closed.getCurrentPrice()).isEqualTo(20_000L);
        assertThat(redis.opsForStream().size(STREAM)).isZero();
    }

    @Test
    @DisplayName("파싱할 수 없는 pending 이벤트는 ACK하지 않고 뒤의 정상 이벤트도 처리하지 않는다")
    void stopsAtMalformedPendingEvent() {
        redis.opsForStream().add(StreamRecords.mapBacked(Map.of(
                "type", "BID_ACCEPTED",
                "requestId", "malformed-with-missing-fields")).withStreamKey(STREAM));
        Fixture fixture = fixture();
        seed(fixture);
        assertThat(store.evaluateBid(
                fixture.item().getAuctionItemId(), "pipeline-after-malformed",
                fixture.bidder().getUserId(), 10_000L))
                .isInstanceOf(RedisBidDecision.Accepted.class);
        redis.opsForHash().put(
                AuctionRedisKeys.item(fixture.item().getAuctionItemId()),
                "endAt",
                String.valueOf(System.currentTimeMillis() - 1_000L));
        store.requestNaturalClose(fixture.item().getAuctionItemId());

        assertThatThrownBy(() -> consumer().poll()).isInstanceOf(RuntimeException.class);

        assertThat(bidRepository.findByRequestId("pipeline-after-malformed")).isEmpty();
        assertThat(auctionItemRepository.findById(fixture.item().getAuctionItemId()).orElseThrow()
                .getStatus()).isEqualTo(AuctionItemStatus.IN_PROGRESS);
        assertThat(redis.opsForStream().size(STREAM)).isEqualTo(3L);
        assertThat(redis.opsForStream().pending(STREAM, GROUP).getTotalPendingMessages()).isEqualTo(1L);
        assertThat(redis.opsForStream().size(QUARANTINE_STREAM)).isEqualTo(1L);
        assertThat(redis.opsForHash().size(QUARANTINE_INDEX)).isEqualTo(1L);
    }

    private BidStreamConsumer consumer() {
        return new BidStreamConsumer(redis, persistenceService, properties,
                new BidStreamMetrics(new SimpleMeterRegistry()),
                new BidStreamFailureClassifier(),
                new BidStreamQuarantineStore(redis, new ObjectMapper()),
                Clock.systemDefaultZone());
    }

    private void seed(Fixture fixture) {
        store.seed(new AuctionRedisSeed(
                fixture.item().getAuctionItemId(), fixture.room().getAuctionRoomId(),
                fixture.seller().getUserId(), AuctionItemStatus.IN_PROGRESS,
                10_000L, 10_000L, null, 1_000L, toMillis(fixture.item().getEndAt()),
                60, 60, 0, AuctionItem.MAX_TOTAL_EXTENSION_SECONDS,
                fixture.item().getProduct().getName(),
                List.of(new AuctionRedisParticipant(
                        fixture.bidder().getUserId(), fixture.bidder().getNickname()))));
    }

    private static BidStreamEvent.BidAccepted acceptedEvent(
            Fixture fixture, String requestId, RedisBidDecision.Accepted accepted) {
        return new BidStreamEvent.BidAccepted(
                requestId,
                fixture.item().getAuctionItemId(),
                fixture.room().getAuctionRoomId(),
                fixture.bidder().getUserId(),
                accepted.amount(),
                accepted.acceptedAtMillis(),
                accepted.endAtMillis(),
                accepted.extendedSeconds(),
                accepted.extendedSeconds());
    }

    private Fixture fixture() {
        String suffix = String.valueOf(System.nanoTime());
        User seller = userRepository.saveAndFlush(User.builder()
                .email("pipeline-seller-" + suffix + "@hot6ix.com")
                .nickname("판매자")
                .build());
        createdUserIds.add(seller.getUserId());
        User bidder = userRepository.saveAndFlush(User.builder()
                .email("pipeline-bidder-" + suffix + "@hot6ix.com")
                .nickname("입찰자")
                .build());
        createdUserIds.add(bidder.getUserId());
        SellerProfile profile = sellerProfileRepository.saveAndFlush(SellerProfile.builder()
                .user(seller)
                .storeName("파이프라인 상점")
                .build());
        createdSellerProfileIds.add(profile.getSellerProfileId());
        AuctionRoom room = auctionRoomRepository.saveAndFlush(AuctionRoom.builder()
                .sellerProfile(profile)
                .name("파이프라인 경매방")
                .shareCode("pipeline-" + suffix)
                .status(AuctionRoomStatus.OPEN)
                .bidIncrement(1_000L)
                .softCloseTriggerSeconds(60)
                .softCloseExtendSeconds(60)
                .build());
        createdRoomIds.add(room.getAuctionRoomId());
        Product product = productRepository.saveAndFlush(Product.builder()
                .sellerProfile(profile)
                .name("파이프라인 상품")
                .build());
        createdProductIds.add(product.getProductId());
        AuctionItem item = auctionItemRepository.saveAndFlush(AuctionItem.builder()
                .auctionRoom(room)
                .product(product)
                .startingPrice(10_000L)
                .bidIncrement(1_000L)
                .status(AuctionItemStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now().minusMinutes(1))
                .originalEndAt(LocalDateTime.now().plusMinutes(5))
                .endAt(LocalDateTime.now().plusMinutes(5))
                .build());
        createdItemIds.add(item.getAuctionItemId());
        return new Fixture(seller, bidder, room, item);
    }

    private static long toMillis(LocalDateTime value) {
        return value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private record Fixture(User seller, User bidder, AuctionRoom room, AuctionItem item) {
    }
}
