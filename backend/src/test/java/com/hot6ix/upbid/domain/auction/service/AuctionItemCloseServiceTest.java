package com.hot6ix.upbid.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemCloseEarlyResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.CloseContextProjection;
import com.hot6ix.upbid.domain.auction.scheduler.AuctionCloseMetrics;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.exception.SellerProfileErrorType;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.global.event.DomainEvent;
import com.hot6ix.upbid.global.event.payload.ItemCloseAdvanced;
import com.hot6ix.upbid.global.event.payload.ItemEnded;
import com.hot6ix.upbid.global.event.payload.ItemPassed;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import com.hot6ix.upbid.global.exception.ApplicationException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuctionItemCloseServiceTest {

    private static final Long ROOM_ID = 10L;
    private static final Long ITEM_ID = 30L;
    private static final Long BIDDER_ID = 40L;
    private static final Long USER_ID = 50L;
    private static final Long SELLER_PROFILE_ID = 60L;

    @Mock
    private AuctionItemRepository auctionItemRepository;

    @Mock
    private SellerProfileRepository sellerProfileRepository;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    /** 계측은 목이 아니라 진짜를 쓴다. 목이면 넘긴 조회를 실행하지 않아 락 조회 자체가 안 일어난다. */
    @Spy
    private AuctionCloseMetrics auctionCloseMetrics = new AuctionCloseMetrics(new SimpleMeterRegistry());

    @InjectMocks
    private AuctionItemCloseService auctionItemCloseService;

    @Test
    @DisplayName("입찰이 있던 물품은 낙찰로 마감되고 ItemEnded가 발행된다")
    void closeSoldItem() {

        AuctionItem auctionItem = givenLockedItem(AuctionItemStatus.IN_PROGRESS);
        auctionItem.applyBid(user("한기"), 12_000L);

        auctionItemCloseService.close(ITEM_ID);

        assertThat(auctionItem.getStatus()).isEqualTo(AuctionItemStatus.SOLD);
        assertThat(publishedEvent()).isInstanceOfSatisfying(ItemEnded.class, event -> {
            assertThat(event.roomId()).isEqualTo(ROOM_ID);
            assertThat(event.itemId()).isEqualTo(ITEM_ID);
            assertThat(event.itemName()).isEqualTo("한정판 피규어");
            assertThat(event.finalPrice()).isEqualTo(12_000L);
            assertThat(event.winnerNickname()).isEqualTo("한기");
        });
    }

    @Test
    @DisplayName("입찰이 없던 물품은 유찰로 마감되고 ItemPassed가 발행된다")
    void closePassedItem() {

        AuctionItem auctionItem = givenLockedItem(AuctionItemStatus.IN_PROGRESS);

        auctionItemCloseService.close(ITEM_ID);

        assertThat(auctionItem.getStatus()).isEqualTo(AuctionItemStatus.FAILED);
        assertThat(publishedEvent()).isInstanceOfSatisfying(ItemPassed.class, event -> {
            assertThat(event.roomId()).isEqualTo(ROOM_ID);
            assertThat(event.itemId()).isEqualTo(ITEM_ID);
            assertThat(event.itemName()).isEqualTo("한정판 피규어");
        });
    }

    @Test
    @DisplayName("이미 마감된 물품은 다시 마감되지 않는다")
    void ignoresAlreadyClosedItem() {

        AuctionItem auctionItem = givenLockedItem(AuctionItemStatus.SOLD);
        auctionItem.applyBid(user("한기"), 12_000L);

        auctionItemCloseService.close(ITEM_ID);

        assertThat(auctionItem.getStatus())
                .as("낙찰자가 정해진 뒤 다시 닫히면 거래 상태가 뒤집힌다")
                .isEqualTo(AuctionItemStatus.SOLD);
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("아직 시작하지 않은 물품은 마감되지 않는다")
    void ignoresReadyItem() {

        AuctionItem auctionItem = givenLockedItem(AuctionItemStatus.READY);

        auctionItemCloseService.close(ITEM_ID);

        assertThat(auctionItem.getStatus()).isEqualTo(AuctionItemStatus.READY);
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("예약된 사이에 제외된 물품은 예외 없이 넘어간다")
    void ignoresMissingItem() {

        when(auctionItemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.empty());

        auctionItemCloseService.close(ITEM_ID);

        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("마감 시각이 아직 남았으면 닫지 않고 그 시각을 돌려준다")
    void deferWhenNotDueYet() {

        LocalDateTime endAt = LocalDateTime.now().plusSeconds(30);
        AuctionItem auctionItem = givenLockedItem(AuctionItemStatus.IN_PROGRESS, endAt);

        Optional<LocalDateTime> rescheduleAt = auctionItemCloseService.closeIfDue(ITEM_ID);

        assertThat(rescheduleAt)
                .as("락을 기다리는 사이 Soft Close 연장이 커밋된 경우다. 여기서 닫으면 연장이 무시된다")
                .contains(endAt);
        assertThat(auctionItem.getStatus()).isEqualTo(AuctionItemStatus.IN_PROGRESS);
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("마감 시각이 지났으면 닫고 다시 예약할 시각을 남기지 않는다")
    void closeWhenDue() {

        AuctionItem auctionItem =
                givenLockedItem(AuctionItemStatus.IN_PROGRESS, LocalDateTime.now().minusSeconds(1));

        Optional<LocalDateTime> rescheduleAt = auctionItemCloseService.closeIfDue(ITEM_ID);

        assertThat(rescheduleAt).isEmpty();
        assertThat(auctionItem.getStatus()).isEqualTo(AuctionItemStatus.FAILED);
        assertThat(publishedEvent()).isInstanceOf(ItemPassed.class);
    }

    @Test
    @DisplayName("판매자가 방을 종료할 때는 마감 시각이 남아 있어도 닫는다")
    void closeIgnoresRemainingTime() {

        AuctionItem auctionItem =
                givenLockedItem(AuctionItemStatus.IN_PROGRESS, LocalDateTime.now().plusMinutes(10));

        auctionItemCloseService.close(ITEM_ID);

        assertThat(auctionItem.getStatus())
                .as("방송이 끝났는데 물품만 계속 열려 있으면 안 된다")
                .isEqualTo(AuctionItemStatus.FAILED);
    }

    @Test
    @DisplayName("마감을 앞당기면 지금부터 트리거 초 뒤로 밀리고 ItemCloseAdvanced가 발행된다")
    void closeEarlyAdvancesEndAt() {

        LocalDateTime before = LocalDateTime.now();
        AuctionItem auctionItem =
                givenOwnedItem(AuctionItemStatus.IN_PROGRESS, before.plusMinutes(10));

        AuctionItemCloseEarlyResponseDto response =
                auctionItemCloseService.closeEarly(USER_ID, ITEM_ID);

        int trigger = AuctionItem.DEFAULT_SOFT_CLOSE_TRIGGER_SECONDS;
        assertThat(response.remainingSeconds()).isEqualTo(trigger);
        assertThat(response.endAt())
                .isEqualTo(auctionItem.getEndAt())
                .isBetween(before.plusSeconds(trigger), LocalDateTime.now().plusSeconds(trigger));
        assertThat(auctionItem.getStatus())
                .as("앞당기기는 마감 시각만 옮긴다. 닫는 것은 새 시각에 깨어난 예약이다")
                .isEqualTo(AuctionItemStatus.IN_PROGRESS);
        assertThat(publishedEvent()).isInstanceOfSatisfying(ItemCloseAdvanced.class, event -> {
            assertThat(event.roomId()).isEqualTo(ROOM_ID);
            assertThat(event.itemId()).isEqualTo(ITEM_ID);
            assertThat(event.itemName()).isEqualTo("한정판 피규어");
            assertThat(event.remainingSeconds()).isEqualTo(trigger);
            assertThat(event.endAt()).isEqualTo(response.endAt());
        });
    }

    @Test
    @DisplayName("남의 방 물품은 마감을 앞당길 수 없고 물품이 없을 때와 같은 응답을 준다")
    void closeEarlyRejectsOtherSellersItem() {

        givenLockOnly(AuctionItemStatus.IN_PROGRESS, LocalDateTime.now().plusMinutes(10));
        givenSellerProfile(SELLER_PROFILE_ID + 1);

        assertThatThrownBy(() -> auctionItemCloseService.closeEarly(USER_ID, ITEM_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(AuctionErrorType.AUCTION_ITEM_NOT_FOUND);
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("진행 중이 아닌 물품은 마감을 앞당길 수 없다")
    void closeEarlyRejectsNotInProgressItem() {

        givenOwnedItem(AuctionItemStatus.READY, null);

        assertThatThrownBy(() -> auctionItemCloseService.closeEarly(USER_ID, ITEM_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(AuctionErrorType.AUCTION_ITEM_NOT_IN_PROGRESS);
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("이미 마감이 임박했으면 거절하고 마감 시각을 그대로 둔다")
    void closeEarlyRejectsAlreadyClosingSoonItem() {

        LocalDateTime endAt = LocalDateTime.now().plusSeconds(10);
        AuctionItem auctionItem = givenOwnedItem(AuctionItemStatus.IN_PROGRESS, endAt);

        assertThatThrownBy(() -> auctionItemCloseService.closeEarly(USER_ID, ITEM_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(AuctionErrorType.AUCTION_ITEM_ALREADY_CLOSING_SOON);
        assertThat(auctionItem.getEndAt())
                .as("받아주면 앞당기기가 오히려 마감을 뒤로 민다")
                .isEqualTo(endAt);
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("판매자 프로필이 없으면 마감을 앞당길 수 없다")
    void closeEarlyRequiresSellerProfile() {

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> auctionItemCloseService.closeEarly(USER_ID, ITEM_ID))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND);
    }

    /** 앞당기기는 요청자를 보므로 물품 락과 판매자 프로필 조회를 함께 준비한다. */
    private AuctionItem givenOwnedItem(AuctionItemStatus status, LocalDateTime endAt) {
        AuctionItem auctionItem = givenLockOnly(status, endAt);
        givenSellerProfile(SELLER_PROFILE_ID);
        return auctionItem;
    }

    private void givenSellerProfile(Long sellerProfileId) {
        SellerProfile sellerProfile = newSellerProfile();
        ReflectionTestUtils.setField(sellerProfile, "sellerProfileId", sellerProfileId);
        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Optional.of(sellerProfile));
    }

    private DomainEvent publishedEvent() {
        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(domainEventPublisher).publish(captor.capture());
        return captor.getValue();
    }

    private AuctionItem givenLockedItem(AuctionItemStatus status) {
        return givenLockedItem(status, null);
    }

    /**
     * 마감 경로({@code close}, {@code closeIfDue})용. 락 조회에 더해, 락을 잡기 전에 읽는
     * 방 ID와 상품명 프로젝션까지 준비한다.
     *
     * <p>앞당기기는 이 프로젝션을 읽지 않으므로 {@link #givenLockOnly}를 쓴다. 여기 스텁을
     * 그대로 가져가면 쓰이지 않는 스텁이 되어 테스트가 깨진다.
     */
    private AuctionItem givenLockedItem(AuctionItemStatus status, LocalDateTime endAt) {
        AuctionItem auctionItem = givenLockOnly(status, endAt);
        when(auctionItemRepository.findCloseContext(ITEM_ID))
                .thenReturn(Optional.of(new CloseContextProjection(ROOM_ID, "한정판 피규어")));
        return auctionItem;
    }

    /** 물품 락 조회만 준비한다. */
    private AuctionItem givenLockOnly(AuctionItemStatus status, LocalDateTime endAt) {
        AuctionItem auctionItem = newItem(status, endAt);
        when(auctionItemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(auctionItem));
        return auctionItem;
    }

    private AuctionItem newItem(AuctionItemStatus status, LocalDateTime endAt) {
        SellerProfile sellerProfile = newSellerProfile();
        AuctionItem auctionItem = AuctionItem.builder()
                .auctionRoom(newRoom(sellerProfile))
                .product(newProduct(sellerProfile))
                .startingPrice(10_000L)
                .bidIncrement(1_000L)
                .status(status)
                .endAt(endAt)
                .build();
        ReflectionTestUtils.setField(auctionItem, "auctionItemId", ITEM_ID);
        return auctionItem;
    }

    private AuctionRoom newRoom(SellerProfile sellerProfile) {
        AuctionRoom auctionRoom = AuctionRoom.builder()
                .sellerProfile(sellerProfile)
                .name("승민의 경매방")
                .status(AuctionRoomStatus.OPEN)
                .bidIncrement(1_000L)
                .build();
        ReflectionTestUtils.setField(auctionRoom, "auctionRoomId", ROOM_ID);
        return auctionRoom;
    }

    private Product newProduct(SellerProfile sellerProfile) {
        return Product.builder()
                .sellerProfile(sellerProfile)
                .name("한정판 피규어")
                .description("미개봉 정품")
                .imageUrl("https://cdn.hot6ix.com/item.png")
                .referenceUrl("https://instagram.com/hot6ix")
                .build();
    }

    private SellerProfile newSellerProfile() {
        SellerProfile sellerProfile = SellerProfile.builder()
                .user(user("승민"))
                .storeName("승민 스토어")
                .build();
        ReflectionTestUtils.setField(sellerProfile, "sellerProfileId", SELLER_PROFILE_ID);
        return sellerProfile;
    }

    private User user(String nickname) {
        User user = User.builder()
                .email(nickname + "@hot6ix.com")
                .password("password")
                .nickname(nickname)
                .phoneNumber("010-1234-5678")
                .build();
        ReflectionTestUtils.setField(user, "userId", BIDDER_ID);
        return user;
    }
}
