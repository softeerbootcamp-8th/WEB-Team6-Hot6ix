package com.hot6ix.upbid.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

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
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link AuctionRoomCloseService#close}가 소유 확인에 <b>엔티티를 읽지 않는</b> 이유를 못 박는다.
 *
 * <p>종료 흐름은 "소유 확인 → 물품 마감 → 경매방 락"이라, 소유 확인이 방 엔티티를 읽어버리면
 * 그 인스턴스가 영속성 컨텍스트에 남는다. 그러면 마지막 락 조회가 락은 잡으면서도 <b>그때 읽은
 * 상태를</b> 돌려주고, 종료 요청 두 건이 겹쳤을 때 뒤늦게 락을 잡은 쪽이 앞선 종료를 보지 못해
 * 방을 한 번 더 닫는다 — 종료 시각이 덮어써지고 {@code RoomClosed}가 두 번 나간다.
 *
 * <p>타이밍에 기대지 않으려고 <b>물품 마감 단계를 훅으로 쓴다.</b> 그 자리가 소유 확인과 방 락
 * 사이라, 거기서 다른 트랜잭션이 방을 닫고 커밋하게 하면 두 요청이 겹친 상황이 매번 같은 순서로
 * 재현된다. 서비스 코드는 실제 구현 그대로 돈다.
 *
 * <p>테스트 자체의 트랜잭션을 꺼두는 이유는 셋업 데이터가 실제로 커밋돼야 별도 트랜잭션에서
 * 보이기 때문이다({@link AuctionRoomServiceIntegrationTest}와 같은 이유).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, AuctionRoomCloseService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuctionRoomCloseConcurrencyTest extends AbstractMySqlContainerTest {

    @Autowired
    private AuctionRoomCloseService auctionRoomCloseService;

    @Autowired
    private AuctionRoomRepository auctionRoomRepository;

    @Autowired
    private AuctionItemRepository auctionItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SellerProfileRepository sellerProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private AuctionItemCloseService auctionItemCloseService;

    @MockitoBean
    private DomainEventPublisher domainEventPublisher;

    @Test
    @DisplayName("종료 도중에 다른 요청이 먼저 방을 닫으면 두 번째 종료는 거절된다")
    void rejectsCloseWhenAnotherRequestClosedFirst() {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoom auctionRoom = newRoom(sellerProfile);
        newInProgressItem(auctionRoom, sellerProfile);

        LocalDateTime firstClosedAt = LocalDateTime.of(2026, 8, 4, 21, 0);
        closeRoomWhileItemsAreClosing(auctionRoom.getAuctionRoomId(), firstClosedAt);

        assertThatThrownBy(() -> auctionRoomCloseService.close(
                sellerProfile.getUser().getUserId(), auctionRoom.getAuctionRoomId()))
                .as("소유 확인이 방 엔티티를 읽으면 앞선 종료를 못 보고 방을 한 번 더 닫는다")
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorType", AuctionErrorType.AUCTION_ROOM_CLOSED);

        AuctionRoom closed = auctionRoomRepository
                .findById(auctionRoom.getAuctionRoomId()).orElseThrow();
        assertThat(closed.getClosedAt())
                .as("두 번째 종료가 첫 종료 시각을 덮어쓰면 안 된다")
                .isEqualTo(firstClosedAt);
    }

    /**
     * 물품 마감이 불릴 때 <b>다른 트랜잭션이</b> 방을 닫고 커밋하게 만든다. 이 자리가 소유 확인과
     * 경매방 락 사이라, 종료 요청 두 건이 겹친 순간을 그대로 재현한다.
     *
     * <p>별도 스레드에서 도는 이유는 진행 중인 종료 트랜잭션에 합류하지 않게 하려는 것이다.
     * 같은 스레드의 {@code TransactionTemplate}은 열려 있는 트랜잭션에 그대로 참여한다.
     */
    private void closeRoomWhileItemsAreClosing(Long auctionRoomId, LocalDateTime closedAt) {
        doAnswer(invocation -> {
            CompletableFuture.runAsync(() -> transactionTemplate.executeWithoutResult(status ->
                    auctionRoomRepository.findById(auctionRoomId)
                            .orElseThrow()
                            .close(closedAt))).join();
            return null;
        }).when(auctionItemCloseService).close(anyLong());
    }

    private void newInProgressItem(AuctionRoom auctionRoom, SellerProfile sellerProfile) {
        auctionItemRepository.saveAndFlush(AuctionItem.builder()
                .auctionRoom(auctionRoom)
                .product(newProduct(sellerProfile))
                .startingPrice(10_000L)
                .bidIncrement(1_000L)
                .status(AuctionItemStatus.IN_PROGRESS)
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
                .shareCode("concurrent-" + System.nanoTime())
                .status(AuctionRoomStatus.OPEN)
                .bidIncrement(1_000L)
                .build());
    }

    private SellerProfile newSellerProfile() {
        User user = userRepository.saveAndFlush(User.builder()
                .email("concurrent-close-" + System.nanoTime() + "@hot6ix.com")
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
