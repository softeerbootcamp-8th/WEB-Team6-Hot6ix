package com.hot6ix.upbid.domain.auction.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.product.repository.ProductRepository;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.config.JpaConfig;
import com.hot6ix.upbid.global.support.AbstractMySqlContainerTest;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, AuctionRedisInitializer.class, AuctionRedisSeedSnapshotLoader.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuctionRedisInitializerTransactionBoundaryTest extends AbstractMySqlContainerTest {

    @Autowired
    private AuctionRedisInitializer auctionRedisInitializer;
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
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private AuctionRedisStore auctionRedisStore;

    @Test
    @DisplayName("Seed readiness 확인은 DB 트랜잭션이 끝난 뒤 실행한다")
    void checksSeedReadinessAfterDatabaseTransactionEnds() {

        ItemIds ids = saveInProgressItem("ready");
        when(auctionRedisStore.isSeedReady(ids.itemId(), ids.roomId()))
                .thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                            .as("Redis readiness 확인 중에는 JDBC 커넥션을 붙잡지 않아야 한다")
                            .isFalse();
                    return true;
                });

        transactionTemplate.executeWithoutResult(
                status -> auctionRedisInitializer.initialize(ids.itemId()));
    }

    @Test
    @DisplayName("Seed 쓰기는 DB 스냅샷 트랜잭션이 끝난 뒤 실행한다")
    void seedsRedisAfterDatabaseTransactionEnds() {

        ItemIds ids = saveInProgressItem("seed");
        AtomicBoolean seeded = new AtomicBoolean();
        when(auctionRedisStore.isSeedReady(ids.itemId(), ids.roomId())).thenReturn(false);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .as("Redis Seed 쓰기 중에는 JDBC 커넥션을 붙잡지 않아야 한다")
                    .isFalse();
            seeded.set(true);
            return true;
        }).when(auctionRedisStore).seed(any(AuctionRedisSeed.class));

        transactionTemplate.executeWithoutResult(
                status -> auctionRedisInitializer.initialize(ids.itemId()));

        assertThat(seeded).isTrue();
    }

    private ItemIds saveInProgressItem(String suffix) {

        return transactionTemplate.execute(status -> {
            User user = userRepository.save(User.builder()
                    .email("redis-boundary-" + suffix + "@hot6ix.com")
                    .password("password")
                    .nickname("시드경계" + suffix)
                    .phoneNumber("010-0000-" + (suffix.equals("ready") ? "1388" : "2388"))
                    .build());
            SellerProfile seller = sellerProfileRepository.save(SellerProfile.builder()
                    .user(user)
                    .storeName("시드경계상점" + suffix)
                    .build());
            AuctionRoom room = auctionRoomRepository.save(AuctionRoom.builder()
                    .sellerProfile(seller)
                    .name("시드 경계 경매방")
                    .shareCode("redis-boundary-" + suffix)
                    .bidIncrement(1_000L)
                    .softCloseTriggerSeconds(60)
                    .softCloseExtendSeconds(60)
                    .build());
            Product product = productRepository.save(Product.builder()
                    .sellerProfile(seller)
                    .name("시드 경계 물품")
                    .description("트랜잭션 경계 검증용")
                    .build());
            AuctionItem item = auctionItemRepository.save(AuctionItem.builder()
                    .auctionRoom(room)
                    .product(product)
                    .startingPrice(10_000L)
                    .bidIncrement(1_000L)
                    .status(AuctionItemStatus.IN_PROGRESS)
                    .endAt(LocalDateTime.of(2026, 8, 18, 22, 0))
                    .build());

            return new ItemIds(room.getAuctionRoomId(), item.getAuctionItemId());
        });
    }

    private record ItemIds(long roomId, long itemId) {
    }
}
