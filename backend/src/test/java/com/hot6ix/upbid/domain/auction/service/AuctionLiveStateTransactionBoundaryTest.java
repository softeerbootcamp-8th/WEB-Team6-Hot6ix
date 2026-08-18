package com.hot6ix.upbid.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.dto.response.AuctionLiveStateResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.realtime.BidderKeyEncoder;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisInitializer;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisLiveState;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisLiveStatus;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisStore;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.product.repository.ProductRepository;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.config.JpaConfig;
import com.hot6ix.upbid.global.support.AbstractMySqlContainerTest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, AuctionLiveStateService.class, AuctionLiveStateItemReader.class,
        AuctionLiveStateTransactionBoundaryTest.ClockConfiguration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuctionLiveStateTransactionBoundaryTest extends AbstractMySqlContainerTest {

    private static final String SHARE_CODE = "live-state-boundary";
    private static final Instant END_AT = Instant.parse("2026-08-18T13:00:00Z");

    @Autowired
    private AuctionLiveStateService auctionLiveStateService;
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
    private AuctionRoomShareService auctionRoomShareService;
    @MockitoBean
    private AuctionRedisInitializer auctionRedisInitializer;
    @MockitoBean
    private AuctionRedisStore auctionRedisStore;
    @MockitoBean
    private BidderKeyEncoder bidderKeyEncoder;

    @Test
    @DisplayName("Live State의 Redis 호출은 DB 조회 트랜잭션이 끝난 뒤 실행한다")
    void readsRedisAfterDatabaseTransactionEnds() {

        ItemIds ids = saveInProgressItem();
        when(auctionRoomShareService.resolveRoomId(SHARE_CODE)).thenReturn(ids.roomId());
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .as("Redis Seed 확인 중에는 JDBC 커넥션을 붙잡지 않아야 한다")
                    .isFalse();
            return null;
        }).when(auctionRedisInitializer).initialize(anyLong());
        when(auctionRedisStore.findLiveState(ids.itemId())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .as("Redis Live State 조회 중에는 JDBC 커넥션을 붙잡지 않아야 한다")
                    .isFalse();
            return Optional.of(new AuctionRedisLiveState(
                    ids.itemId(),
                    ids.roomId(),
                    AuctionRedisLiveStatus.IN_PROGRESS,
                    10_000L,
                    null,
                    END_AT.toEpochMilli(),
                    0,
                    0L,
                    List.of()));
        });

        List<AuctionLiveStateResponseDto> result = transactionTemplate.execute(
                status -> auctionLiveStateService.getLiveStates(SHARE_CODE, null));

        assertThat(result).hasSize(1);
    }

    private ItemIds saveInProgressItem() {

        return transactionTemplate.execute(status -> {
            User user = userRepository.save(User.builder()
                    .email("live-state-boundary@hot6ix.com")
                    .password("password")
                    .nickname("경계검증")
                    .phoneNumber("010-0000-0388")
                    .build());
            SellerProfile seller = sellerProfileRepository.save(SellerProfile.builder()
                    .user(user)
                    .storeName("경계상점")
                    .build());
            AuctionRoom room = auctionRoomRepository.save(AuctionRoom.builder()
                    .sellerProfile(seller)
                    .name("경계 경매방")
                    .shareCode(SHARE_CODE)
                    .bidIncrement(1_000L)
                    .softCloseTriggerSeconds(60)
                    .softCloseExtendSeconds(60)
                    .build());
            Product product = productRepository.save(Product.builder()
                    .sellerProfile(seller)
                    .name("경계 물품")
                    .description("트랜잭션 경계 검증용")
                    .build());
            AuctionItem item = auctionItemRepository.save(AuctionItem.builder()
                    .auctionRoom(room)
                    .product(product)
                    .startingPrice(10_000L)
                    .bidIncrement(1_000L)
                    .status(AuctionItemStatus.IN_PROGRESS)
                    .endAt(LocalDateTime.ofInstant(END_AT, ZoneId.systemDefault()))
                    .build());

            return new ItemIds(room.getAuctionRoomId(), item.getAuctionItemId());
        });
    }

    private record ItemIds(long roomId, long itemId) {
    }

    @TestConfiguration
    static class ClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-08-18T12:00:00Z"), ZoneId.systemDefault());
        }
    }
}
