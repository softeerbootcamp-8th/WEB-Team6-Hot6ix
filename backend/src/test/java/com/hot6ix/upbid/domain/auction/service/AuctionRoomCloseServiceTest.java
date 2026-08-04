package com.hot6ix.upbid.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomPublicResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.exception.SellerProfileErrorType;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.global.event.DomainEvent;
import com.hot6ix.upbid.global.event.payload.RoomClosed;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import com.hot6ix.upbid.global.exception.ApplicationException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuctionRoomCloseServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long SELLER_PROFILE_ID = 2L;
    private static final Long ROOM_ID = 10L;

    @Mock
    private AuctionRoomRepository auctionRoomRepository;

    @Mock
    private AuctionItemRepository auctionItemRepository;

    @Mock
    private SellerProfileRepository sellerProfileRepository;

    @Mock
    private AuctionItemCloseService auctionItemCloseService;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @InjectMocks
    private AuctionRoomCloseService auctionRoomCloseService;

    @Test
    @DisplayName("방을 종료하면 상태가 CLOSED가 되고 종료 시각이 남는다")
    void close() {

        givenActiveSellerProfile();
        givenOwnedRoom();
        AuctionRoom auctionRoom = givenLockedRoom(AuctionRoomStatus.OPEN);
        givenInProgressItems();

        AuctionRoomPublicResponseDto response = auctionRoomCloseService.close(USER_ID, ROOM_ID);

        assertThat(auctionRoom.getStatus()).isEqualTo(AuctionRoomStatus.CLOSED);
        assertThat(auctionRoom.getClosedAt()).isNotNull();
        assertThat(response.status()).isEqualTo(AuctionRoomStatus.CLOSED);
        assertThat(response.closedAt())
                .as("종료 화면이 '종료 {날짜}'를 그리는 값이라 응답에 실려야 한다")
                .isEqualTo(auctionRoom.getClosedAt());
    }

    @Test
    @DisplayName("진행 중인 물품을 ID 오름차순으로 모두 마감한 뒤 방을 닫는다")
    void closesInProgressItemsInIdOrder() {

        givenActiveSellerProfile();
        givenOwnedRoom();
        givenLockedRoom(AuctionRoomStatus.OPEN);
        givenInProgressItems(31L, 32L, 33L);

        auctionRoomCloseService.close(USER_ID, ROOM_ID);

        InOrder inOrder = inOrder(auctionItemCloseService, auctionRoomRepository);
        inOrder.verify(auctionItemCloseService).close(31L);
        inOrder.verify(auctionItemCloseService).close(32L);
        inOrder.verify(auctionItemCloseService).close(33L);
        inOrder.verify(auctionRoomRepository).findByIdForUpdate(ROOM_ID);
    }

    @Test
    @DisplayName("아직 시작하지 않은 물품은 마감 대상이 아니다")
    void doesNotTouchReadyItems() {

        givenActiveSellerProfile();
        givenOwnedRoom();
        givenLockedRoom(AuctionRoomStatus.BEFORE);
        givenInProgressItems();

        auctionRoomCloseService.close(USER_ID, ROOM_ID);

        verify(auctionItemRepository).findIdsByRoomAndStatus(ROOM_ID, AuctionItemStatus.IN_PROGRESS);
        verify(auctionItemCloseService, never()).close(anyLong());
    }

    @Test
    @DisplayName("물품을 하나도 시작하지 않은 방(BEFORE)도 종료할 수 있다")
    void closesRoomBeforeAnyItemStarted() {

        givenActiveSellerProfile();
        givenOwnedRoom();
        AuctionRoom auctionRoom = givenLockedRoom(AuctionRoomStatus.BEFORE);
        givenInProgressItems();

        auctionRoomCloseService.close(USER_ID, ROOM_ID);

        assertThat(auctionRoom.getStatus())
                .as("쓰다 만 방을 정리할 수단이라 시작 전에도 닫을 수 있어야 한다")
                .isEqualTo(AuctionRoomStatus.CLOSED);
    }

    @Test
    @DisplayName("종료하면 RoomClosed가 발행된다")
    void publishesRoomClosed() {

        givenActiveSellerProfile();
        givenOwnedRoom();
        AuctionRoom auctionRoom = givenLockedRoom(AuctionRoomStatus.OPEN);
        givenInProgressItems();

        auctionRoomCloseService.close(USER_ID, ROOM_ID);

        assertThat(publishedEvent()).isInstanceOfSatisfying(RoomClosed.class, event -> {
            assertThat(event.roomId()).isEqualTo(ROOM_ID);
            assertThat(event.roomTitle()).isEqualTo("승민의 경매방");
            assertThat(event.occurredAt()).isEqualTo(auctionRoom.getClosedAt());
        });
    }

    @Test
    @DisplayName("이미 종료된 방은 다시 종료되지 않는다")
    void rejectsAlreadyClosedRoom() {

        givenActiveSellerProfile();
        givenOwnedRoom();
        AuctionRoom auctionRoom = givenLockedRoom(AuctionRoomStatus.CLOSED);
        givenInProgressItems();

        assertThatThrownBy(() -> auctionRoomCloseService.close(USER_ID, ROOM_ID))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorType", AuctionErrorType.AUCTION_ROOM_CLOSED);

        assertThat(auctionRoom.getClosedAt())
                .as("두 번째 종료가 첫 종료 시각을 덮어쓰면 안 된다")
                .isNull();
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("남의 방이거나 없는 방이면 물품을 건드리기 전에 거절한다")
    void rejectsRoomNotOwned() {

        givenActiveSellerProfile();
        when(auctionRoomRepository.existsByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                ROOM_ID, SELLER_PROFILE_ID)).thenReturn(false);

        assertThatThrownBy(() -> auctionRoomCloseService.close(USER_ID, ROOM_ID))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorType", AuctionErrorType.AUCTION_ROOM_NOT_FOUND);

        verify(auctionItemCloseService, never()).close(anyLong());
    }

    @Test
    @DisplayName("판매자 프로필이 없으면 종료할 수 없다")
    void rejectsMissingSellerProfile() {

        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> auctionRoomCloseService.close(USER_ID, ROOM_ID))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorType", SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND);
    }

    private DomainEvent publishedEvent() {
        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(domainEventPublisher).publish(captor.capture());
        return captor.getValue();
    }

    private void givenActiveSellerProfile() {
        when(sellerProfileRepository.findByUser_UserIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Optional.of(newSellerProfile()));
    }

    private void givenOwnedRoom() {
        when(auctionRoomRepository.existsByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                ROOM_ID, SELLER_PROFILE_ID)).thenReturn(true);
    }

    private AuctionRoom givenLockedRoom(AuctionRoomStatus status) {
        AuctionRoom auctionRoom = newRoom(status);
        when(auctionRoomRepository.findByIdForUpdate(ROOM_ID)).thenReturn(Optional.of(auctionRoom));
        return auctionRoom;
    }

    private void givenInProgressItems(Long... auctionItemIds) {
        when(auctionItemRepository.findIdsByRoomAndStatus(ROOM_ID, AuctionItemStatus.IN_PROGRESS))
                .thenReturn(List.of(auctionItemIds));
    }

    private AuctionRoom newRoom(AuctionRoomStatus status) {
        AuctionRoom auctionRoom = AuctionRoom.builder()
                .sellerProfile(newSellerProfile())
                .name("승민의 경매방")
                .status(status)
                .bidIncrement(1_000L)
                .build();
        ReflectionTestUtils.setField(auctionRoom, "auctionRoomId", ROOM_ID);
        return auctionRoom;
    }

    private SellerProfile newSellerProfile() {
        SellerProfile sellerProfile = SellerProfile.builder()
                .user(newUser())
                .storeName("승민 스토어")
                .build();
        ReflectionTestUtils.setField(sellerProfile, "sellerProfileId", SELLER_PROFILE_ID);
        return sellerProfile;
    }

    private User newUser() {
        User user = User.builder()
                .email("seungmin@hot6ix.com")
                .password("password")
                .nickname("승민")
                .phoneNumber("010-1234-5678")
                .build();
        ReflectionTestUtils.setField(user, "userId", USER_ID);
        return user;
    }
}
