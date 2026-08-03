package com.hot6ix.upbid.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.global.event.DomainEvent;
import com.hot6ix.upbid.global.event.payload.ItemEnded;
import com.hot6ix.upbid.global.event.payload.ItemPassed;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuctionItemCloseServiceTest {

    private static final Long ROOM_ID = 10L;
    private static final Long ITEM_ID = 30L;
    private static final Long BIDDER_ID = 40L;

    @Mock
    private AuctionItemRepository auctionItemRepository;

    @Mock
    private DomainEventPublisher domainEventPublisher;

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

    private DomainEvent publishedEvent() {
        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(domainEventPublisher).publish(captor.capture());
        return captor.getValue();
    }

    private AuctionItem givenLockedItem(AuctionItemStatus status) {
        AuctionItem auctionItem = newItem(status);
        when(auctionItemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(auctionItem));
        return auctionItem;
    }

    private AuctionItem newItem(AuctionItemStatus status) {
        SellerProfile sellerProfile = newSellerProfile();
        AuctionItem auctionItem = AuctionItem.builder()
                .auctionRoom(newRoom(sellerProfile))
                .product(newProduct(sellerProfile))
                .startingPrice(10_000L)
                .bidIncrement(1_000L)
                .status(status)
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
        return SellerProfile.builder()
                .user(user("승민"))
                .storeName("승민 스토어")
                .build();
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
