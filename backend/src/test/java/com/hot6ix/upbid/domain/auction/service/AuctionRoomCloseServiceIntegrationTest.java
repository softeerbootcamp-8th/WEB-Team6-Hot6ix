package com.hot6ix.upbid.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방 종료가 실제 DB·트랜잭션 경계에서 동작하는지 확인한다. {@link AuctionRoomCloseServiceTest}는
 * 목만 쓰는 단위 테스트라 상태가 정말 DB에 남는지, 자동 종료 대상을 고르는 JPQL이 실제로 도는지를
 * 확인하지 못한다. 대상 조회는 서브쿼리 두 개짜리라 목으로는 아무것도 검증되지 않는다.
 *
 * <p><b>여기서 확인하지 못하는 것도 적어 둔다.</b> 소유 확인이 엔티티를 읽지 않는 이유(영속성
 * 컨텍스트에 남은 인스턴스 때문에 뒤의 락 조회가 앞선 종료를 못 보는 문제)는 <b>동시 요청이
 * 겹쳐야</b> 드러나므로 아래 순차 호출로는 재현되지 않는다. 진행 중 물품 검사가
 * {@code READ_COMMITTED}를 필요로 하는 것도 마찬가지다. 둘 다
 * {@link AuctionRoomCloseConcurrencyTest}가 맡는다.
 *
 * <p>테스트 자체의 트랜잭션을 끄는 이유는 셋업이 실제로 커밋돼야 서비스 호출이 자기 트랜잭션과
 * 영속성 컨텍스트를 새로 얻기 때문이다({@link AuctionRoomServiceIntegrationTest}와 같은 이유).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, AuctionRoomCloseService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuctionRoomCloseServiceIntegrationTest extends AbstractMySqlContainerTest {

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

    @MockitoBean
    private DomainEventPublisher domainEventPublisher;

    // 조회 캐시는 Redis가 필요해 이 슬라이스에 없다. 종료가 캐시를 지우는지는 단위 테스트에서 본다.
    @MockitoBean
    private AuctionRoomPublicCacheService auctionRoomPublicCacheService;

    @Test
    @DisplayName("진행 중인 물품이 남아 있으면 종료가 거절되고 방도 물품도 그대로다")
    void rejectsRoomWithInProgressItem() {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoom auctionRoom = newRoom(sellerProfile, AuctionRoomStatus.OPEN);
        AuctionItem inProgress = newItem(auctionRoom, sellerProfile,
                AuctionItemStatus.IN_PROGRESS, LocalDateTime.now().plusMinutes(5));

        assertThatThrownBy(() -> auctionRoomCloseService.close(
                sellerProfile.getUser().getUserId(), auctionRoom.getAuctionRoomId()))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorType",
                        AuctionErrorType.AUCTION_ROOM_HAS_IN_PROGRESS_ITEM);

        assertThat(findRoom(auctionRoom).getStatus()).isEqualTo(AuctionRoomStatus.OPEN);
        assertThat(auctionItemRepository.findStatus(inProgress.getAuctionItemId()))
                .as("거절된 종료가 물품을 건드리면 안 된다")
                .contains(AuctionItemStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("아직 시작하지 않은 물품만 남았으면 종료할 수 있고 그 물품은 그대로 둔다")
    void closesRoomWithOnlyReadyItems() {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoom auctionRoom = newRoom(sellerProfile, AuctionRoomStatus.OPEN);
        AuctionItem ready = newItem(auctionRoom, sellerProfile, AuctionItemStatus.READY, null);
        newItem(auctionRoom, sellerProfile, AuctionItemStatus.FAILED, LocalDateTime.now().minusHours(1));

        auctionRoomCloseService.close(
                sellerProfile.getUser().getUserId(), auctionRoom.getAuctionRoomId());

        AuctionRoom closed = findRoom(auctionRoom);
        assertThat(closed.getStatus()).isEqualTo(AuctionRoomStatus.CLOSED);
        assertThat(closed.getClosedAt()).isNotNull();
        assertThat(auctionItemRepository.findStatus(ready.getAuctionItemId()))
                .as("시작한 적 없는 물품을 유찰로 적으면 실제 유찰과 결과 집계에서 섞인다")
                .contains(AuctionItemStatus.READY);
    }

    @Test
    @DisplayName("이미 종료된 방을 다시 종료하면 4004로 거절한다")
    void rejectsSecondClose() {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoom auctionRoom = newRoom(sellerProfile, AuctionRoomStatus.OPEN);
        Long userId = sellerProfile.getUser().getUserId();
        Long roomId = auctionRoom.getAuctionRoomId();

        auctionRoomCloseService.close(userId, roomId);

        assertThatThrownBy(() -> auctionRoomCloseService.close(userId, roomId))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorType", AuctionErrorType.AUCTION_ROOM_CLOSED);
    }

    @Test
    @DisplayName("마지막 물품이 기준보다 앞서 마감된 방은 대상으로 잡히고 자동 종료된다")
    void closesIdleRoom() {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoom auctionRoom = newRoom(sellerProfile, AuctionRoomStatus.OPEN);
        newItem(auctionRoom, sellerProfile, AuctionItemStatus.SOLD, LocalDateTime.now().minusHours(20));
        newItem(auctionRoom, sellerProfile, AuctionItemStatus.FAILED, LocalDateTime.now().minusHours(13));

        LocalDateTime idleBefore = LocalDateTime.now().minusHours(12);

        assertThat(findIdleRoomIds(idleBefore)).contains(auctionRoom.getAuctionRoomId());

        assertThat(auctionRoomCloseService.closeIfIdle(auctionRoom.getAuctionRoomId(), idleBefore))
                .isTrue();
        assertThat(findRoom(auctionRoom).getStatus()).isEqualTo(AuctionRoomStatus.CLOSED);
    }

    @Test
    @DisplayName("마지막 물품이 기준 안쪽에 마감된 방은 대상이 아니다")
    void skipsRecentlyClosedRoom() {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoom auctionRoom = newRoom(sellerProfile, AuctionRoomStatus.OPEN);
        newItem(auctionRoom, sellerProfile, AuctionItemStatus.SOLD, LocalDateTime.now().minusHours(20));
        newItem(auctionRoom, sellerProfile, AuctionItemStatus.FAILED, LocalDateTime.now().minusHours(1));

        LocalDateTime idleBefore = LocalDateTime.now().minusHours(12);

        assertThat(findIdleRoomIds(idleBefore))
                .as("가장 늦게 마감된 물품이 기준이라 20시간 전 물품에 끌려가면 안 된다")
                .doesNotContain(auctionRoom.getAuctionRoomId());
        assertThat(auctionRoomCloseService.closeIfIdle(auctionRoom.getAuctionRoomId(), idleBefore))
                .isFalse();
    }

    @Test
    @DisplayName("아직 시작하지 않은 물품이 남은 방은 자동 종료하지 않는다")
    void skipsRoomWithReadyItem() {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoom auctionRoom = newRoom(sellerProfile, AuctionRoomStatus.OPEN);
        newItem(auctionRoom, sellerProfile, AuctionItemStatus.SOLD, LocalDateTime.now().minusHours(20));
        newItem(auctionRoom, sellerProfile, AuctionItemStatus.READY, null);

        LocalDateTime idleBefore = LocalDateTime.now().minusHours(12);

        assertThat(findIdleRoomIds(idleBefore))
                .as("판매자가 이어서 진행할 수 있는 방이라 수동 종료를 기다린다")
                .doesNotContain(auctionRoom.getAuctionRoomId());
        assertThat(auctionRoomCloseService.closeIfIdle(auctionRoom.getAuctionRoomId(), idleBefore))
                .isFalse();
    }

    @Test
    @DisplayName("아직 시작하지 않은 방(BEFORE)은 자동 종료 대상이 아니다")
    void skipsRoomBeforeAnyItemStarted() {

        SellerProfile sellerProfile = newSellerProfile();
        AuctionRoom auctionRoom = newRoom(sellerProfile, AuctionRoomStatus.BEFORE);

        LocalDateTime idleBefore = LocalDateTime.now().minusHours(12);

        assertThat(findIdleRoomIds(idleBefore)).doesNotContain(auctionRoom.getAuctionRoomId());
        assertThat(auctionRoomCloseService.closeIfIdle(auctionRoom.getAuctionRoomId(), idleBefore))
                .as("물품을 하나도 시작하지 않은 방은 방치된 방송이 아니다")
                .isFalse();
    }

    private List<Long> findIdleRoomIds(LocalDateTime idleBefore) {
        return auctionRoomRepository.findIdleRoomIds(idleBefore, Limit.of(100));
    }

    private AuctionRoom findRoom(AuctionRoom auctionRoom) {
        return auctionRoomRepository.findById(auctionRoom.getAuctionRoomId()).orElseThrow();
    }

    private AuctionItem newItem(AuctionRoom auctionRoom, SellerProfile sellerProfile,
                                AuctionItemStatus status, LocalDateTime endAt) {
        return auctionItemRepository.saveAndFlush(AuctionItem.builder()
                .auctionRoom(auctionRoom)
                .product(newProduct(sellerProfile))
                .startingPrice(10_000L)
                .bidIncrement(1_000L)
                .status(status)
                .startedAt(endAt != null ? endAt.minusMinutes(10) : null)
                .endAt(endAt)
                .build());
    }

    private Product newProduct(SellerProfile sellerProfile) {
        return productRepository.saveAndFlush(Product.builder()
                .sellerProfile(sellerProfile)
                .name("한정판 피규어")
                .description("미개봉 정품")
                .imageUrl("https://cdn.hot6ix.com/item.png")
                .referenceUrl("https://instagram.com/hot6ix")
                .build());
    }

    private AuctionRoom newRoom(SellerProfile sellerProfile, AuctionRoomStatus status) {
        return auctionRoomRepository.saveAndFlush(AuctionRoom.builder()
                .sellerProfile(sellerProfile)
                .name("승민의 경매방")
                .shareCode("close-" + System.nanoTime())
                .status(status)
                .bidIncrement(1_000L)
                .build());
    }

    private SellerProfile newSellerProfile() {
        User user = userRepository.saveAndFlush(User.builder()
                .email("room-close-" + System.nanoTime() + "@hot6ix.com")
                .password("password")
                .nickname("승민")
                .phoneNumber("010-0000-0010")
                .build());

        return sellerProfileRepository.saveAndFlush(SellerProfile.builder()
                .user(user)
                .storeName("승민 스토어")
                .build());
    }
}
