package com.hot6ix.upbid.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import com.hot6ix.upbid.domain.auction.config.AuctionProperties;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionItemAddRequestDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.product.repository.ProductRepository;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.config.JpaConfig;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.support.AbstractMySqlContainerTest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>자동 종료가 방을 닫는 사이에 들어온 물품 추가가 종료된 방에 물품을 넣지 못한다</b>는 것을
 * 못 박는다. PR #325 리뷰에서 나온 지적이다.
 *
 * <p>물품 추가는 원래 경매방을 <b>일반 조회</b>로 읽고 {@code OPEN}만 확인했다. 그러면 방을 읽은
 * 뒤 저장하기까지 사이에 방이 닫혀도 INSERT가 그대로 성공해서, <b>다시 열 수 없는 방에 영영
 * 시작할 수 없는 물품</b>이 남는다. 판매자가 종료를 눌러야 나던 일인데 자동 종료(#284)가 붙으면서
 * 판매자가 아무것도 안 해도 생길 수 있게 됐다.
 *
 * <p>고친 방법은 추가도 종료와 <b>같은 방 행 락</b>을 잡게 한 것이다. 그래서 이 테스트는
 * {@code AuctionItemService.add}가 락을 안 잡는 코드로 되돌아가면 깨진다.
 *
 * <p>테스트 자체의 트랜잭션을 꺼두는 이유는 셋업 데이터가 실제로 커밋돼야 다른 스레드에서
 * 보이기 때문이다({@link AuctionRoomCloseConcurrencyTest}와 같은 이유).
 *
 * <p>겹치는 순간을 만들려고 <b>자동 종료가 방 락을 잡은 뒤</b> 부르는 조회를 훅으로 쓴다.
 * 거기서 추가 요청을 다른 스레드로 띄우고 잠시 기다려 <b>락 앞에 줄 서게</b> 한 다음 종료를
 * 진행시킨다. 락이 없으면 추가가 기다리지 않고 그대로 INSERT해 버린다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, AuctionRoomCloseService.class, AuctionItemService.class,
        AuctionRoomCloseVsItemAddConcurrencyTest.PropertiesTestConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuctionRoomCloseVsItemAddConcurrencyTest extends AbstractMySqlContainerTest {

    /** 추가 요청이 방 락 앞에 줄 설 때까지 주는 시간. */
    private static final Duration QUEUE_UP = Duration.ofMillis(500);

    /** {@code @DataJpaTest}는 {@code @ConfigurationProperties}를 올려 주지 않는다. */
    @TestConfiguration
    static class PropertiesTestConfig {

        @Bean
        AuctionProperties auctionProperties() {
            return new AuctionProperties(3, null, null,
                    new AuctionProperties.Room(Duration.ofHours(12), 50));
        }
    }

    @Autowired
    private AuctionRoomCloseService auctionRoomCloseService;

    @Autowired
    private AuctionItemService auctionItemService;

    @Autowired
    private AuctionRoomRepository auctionRoomRepository;

    /** 종료가 방 락을 잡은 뒤 끼어들 자리를 얻으려고 spy 로 받는다. */
    @MockitoSpyBean
    private AuctionItemRepository auctionItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SellerProfileRepository sellerProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private DomainEventPublisher domainEventPublisher;

    @MockitoBean
    private AuctionRoomShareService auctionRoomShareService;

    @MockitoBean
    private AuctionRoomPublicCacheService auctionRoomPublicCacheService;

    @Test
    @DisplayName("자동 종료가 닫는 사이에 들어온 물품 추가는 거절되고 물품도 안 생긴다")
    void rejectsItemAddRacingWithIdleClose() throws Exception {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoom auctionRoom = newRoom(sellerProfile);
        Long roomId = auctionRoom.getAuctionRoomId();
        Long userId = sellerProfile.getUser().getUserId();
        Long productId = newProduct(sellerProfile).getProductId();

        // 13시간 전에 마감된 물품 하나. 이 방을 자동 종료 대상으로 만든다.
        LocalDateTime endAt = LocalDateTime.now().minusHours(13);
        newClosedItem(auctionRoom, sellerProfile, endAt);

        AtomicReference<CompletableFuture<Throwable>> adding = new AtomicReference<>();

        doAnswer(invocation -> {
            adding.set(CompletableFuture.supplyAsync(() -> {
                try {
                    auctionItemService.add(userId, roomId,
                            AuctionItemAddRequestDto.builder()
                                    .productId(productId).startingPrice(10_000L).build());
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            }));
            Thread.sleep(QUEUE_UP.toMillis());
            return endAt;
        }).when(auctionItemRepository).findMaxEndAt(anyLong());

        boolean closed = auctionRoomCloseService.closeIfIdle(roomId, LocalDateTime.now().minusHours(12));

        assertThat(closed).isTrue();

        Throwable addFailure = adding.get().get(10, TimeUnit.SECONDS);

        assertThat(addFailure)
                .as("추가가 방 락을 안 잡으면 닫힌 방인 줄 모르고 그대로 성공한다")
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuctionErrorType.AUCTION_ROOM_CLOSED);

        assertThat(auctionItemRepository.countByAuctionRoom_AuctionRoomIdAndStatus(
                roomId, AuctionItemStatus.READY))
                .as("종료된 방에 남으면 시작할 수도, 방을 다시 열 수도 없다")
                .isZero();
        assertThat(auctionRoomRepository.findById(roomId).orElseThrow().getStatus())
                .isEqualTo(AuctionRoomStatus.CLOSED);
    }

    private AuctionItem newClosedItem(AuctionRoom auctionRoom, SellerProfile sellerProfile,
                                      LocalDateTime endAt) {
        return auctionItemRepository.saveAndFlush(AuctionItem.builder()
                .auctionRoom(auctionRoom)
                .product(newProduct(sellerProfile))
                .startingPrice(10_000L)
                .bidIncrement(1_000L)
                .status(AuctionItemStatus.FAILED)
                .startedAt(endAt.minusMinutes(10))
                .endAt(endAt)
                .build());
    }

    private Product newProduct(SellerProfile sellerProfile) {
        return productRepository.saveAndFlush(Product.builder()
                .sellerProfile(sellerProfile)
                .name("한정판 피규어")
                .description("미개봉 정품")
                .build());
    }

    private AuctionRoom newRoom(SellerProfile sellerProfile) {
        return auctionRoomRepository.saveAndFlush(AuctionRoom.builder()
                .sellerProfile(sellerProfile)
                .name("승민의 경매방")
                .shareCode("race-" + System.nanoTime())
                .status(AuctionRoomStatus.OPEN)
                .bidIncrement(1_000L)
                .build());
    }

    private SellerProfile newSellerProfile() {
        User user = userRepository.saveAndFlush(User.builder()
                .email("close-vs-add-" + System.nanoTime() + "@hot6ix.com")
                .password("password")
                .nickname("승민")
                .phoneNumber("010-0000-0012")
                .build());

        return sellerProfileRepository.saveAndFlush(SellerProfile.builder()
                .user(user)
                .storeName("승민 스토어")
                .build());
    }
}
