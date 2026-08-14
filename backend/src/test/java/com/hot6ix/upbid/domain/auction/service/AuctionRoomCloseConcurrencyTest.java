package com.hot6ix.upbid.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import com.hot6ix.upbid.domain.auction.dto.request.AuctionItemStartRequestDto;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link AuctionRoomCloseService#close}가 <b>겹친 요청 앞에서 무너지지 않는</b> 두 가지를 못 박는다.
 * 소유 확인이 엔티티를 읽지 않는 것과, 트랜잭션 격리 수준을 {@code READ_COMMITTED}로 내린 것이다.
 * 둘 다 코드만 봐서는 왜 그렇게 돼 있는지 드러나지 않아서, 되돌리면 여기서 깨지게 남겨 둔다.
 *
 * <p>타이밍에 기대지 않으려고 <b>소유 확인 조회를 훅으로 쓴다.</b> 그 자리가 판매자 프로필 조회와
 * 경매방 락 사이라, 거기서 다른 트랜잭션이 무언가를 커밋하게 하면 두 요청이 겹친 상황이 매번 같은
 * 순서로 재현된다. 서비스 코드는 실제 구현 그대로 돈다.
 *
 * <p>훅 자리가 <b>첫 일반 조회보다 뒤</b>라는 것이 격리 수준 쪽에서 중요하다. REPEATABLE READ의
 * 읽기 뷰는 판매자 프로필 조회 시점에 이미 고정돼 있어서, 그 뒤에 커밋된 것은 같은 트랜잭션의
 * 일반 조회에 잡히지 않는다.
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

    /**
     * 실제 조회는 그대로 두고 <b>소유 확인 뒤에 끼어들 자리만</b> 얻으려고 spy로 받는다.
     * 목으로 바꾸면 종료가 실제 DB를 안 보게 돼서 이 테스트가 의미를 잃는다.
     */
    @MockitoSpyBean
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
    private DomainEventPublisher domainEventPublisher;

    @Test
    @DisplayName("종료 도중에 다른 요청이 먼저 방을 닫으면 두 번째 종료는 거절된다")
    void rejectsCloseWhenAnotherRequestClosedFirst() {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoom auctionRoom = newRoom(sellerProfile);
        Long roomId = auctionRoom.getAuctionRoomId();

        LocalDateTime firstClosedAt = LocalDateTime.of(2026, 8, 4, 21, 0);
        duringOwnerCheck(() -> auctionRoomRepository.findById(roomId).orElseThrow().close(firstClosedAt));

        assertThatThrownBy(() -> auctionRoomCloseService.close(
                sellerProfile.getUser().getUserId(), roomId))
                .as("소유 확인이 방 엔티티를 읽으면 앞선 종료를 못 보고 방을 한 번 더 닫는다")
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorType", AuctionErrorType.AUCTION_ROOM_CLOSED);

        assertThat(auctionRoomRepository.findById(roomId).orElseThrow().getClosedAt())
                .as("두 번째 종료가 첫 종료 시각을 덮어쓰면 안 된다")
                .isEqualTo(firstClosedAt);
    }

    @Test
    @DisplayName("종료가 방 락을 기다리는 동안 시작된 물품도 진행 중으로 잡아 거절한다")
    void rejectsCloseWhenItemStartedWhileWaiting() {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoom auctionRoom = newRoom(sellerProfile);
        AuctionItem ready = newItem(auctionRoom, sellerProfile, AuctionItemStatus.READY);

        duringOwnerCheck(() -> auctionItemRepository.findById(ready.getAuctionItemId()).orElseThrow()
                .start(AuctionItemStartRequestDto.builder().durationMinutes(10).build(),
                        LocalDateTime.now()));

        assertThatThrownBy(() -> auctionRoomCloseService.close(
                sellerProfile.getUser().getUserId(), auctionRoom.getAuctionRoomId()))
                .as("기본 격리 수준이면 고정된 읽기 뷰 때문에 방금 시작된 물품이 안 잡혀서 방이 닫힌다")
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorType",
                        AuctionErrorType.AUCTION_ROOM_HAS_IN_PROGRESS_ITEM);

        assertThat(auctionRoomRepository.findById(auctionRoom.getAuctionRoomId())
                .orElseThrow().getStatus())
                .as("진행 중인 물품을 남겨 둔 채 방이 닫히면 안 된다")
                .isEqualTo(AuctionRoomStatus.OPEN);
    }

    /**
     * 소유 확인 조회가 불릴 때 <b>다른 트랜잭션이</b> 주어진 일을 하고 커밋하게 만든다.
     * 이 자리가 판매자 프로필 조회와 경매방 락 사이라, 요청 두 건이 겹친 순간을 그대로 재현한다.
     *
     * <p>소유 확인 <b>한 곳만</b> 결과를 고정하고(두 테스트 다 소유자가 맞는 시나리오라 실제
     * 조회 결과와 같다) 나머지 조회는 전부 실제 DB를 본다. 이 조회를 엔티티를 읽는 방식으로
     * 바꾸면 훅이 아예 안 걸려서 앞선 커밋이 일어나지 않고, 두 테스트가 함께 깨진다.
     *
     * <p>별도 스레드에서 도는 이유는 진행 중인 종료 트랜잭션에 합류하지 않게 하려는 것이다.
     * 같은 스레드의 {@code TransactionTemplate}은 열려 있는 트랜잭션에 그대로 참여한다.
     */
    private void duringOwnerCheck(Runnable interleaved) {
        doAnswer(invocation -> {
            CompletableFuture.runAsync(() ->
                    transactionTemplate.executeWithoutResult(status -> interleaved.run())).join();
            return true;
        }).when(auctionRoomRepository)
                .existsByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                        anyLong(), anyLong());
    }

    private AuctionItem newItem(AuctionRoom auctionRoom, SellerProfile sellerProfile,
                                AuctionItemStatus status) {
        return auctionItemRepository.saveAndFlush(AuctionItem.builder()
                .auctionRoom(auctionRoom)
                .product(newProduct(sellerProfile))
                .startingPrice(10_000L)
                .bidIncrement(1_000L)
                .status(status)
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
