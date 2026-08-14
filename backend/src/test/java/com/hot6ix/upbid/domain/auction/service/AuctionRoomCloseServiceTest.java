package com.hot6ix.upbid.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    private DomainEventPublisher domainEventPublisher;

    @InjectMocks
    private AuctionRoomCloseService auctionRoomCloseService;

    @Nested
    @DisplayName("판매자가 직접 종료할 때")
    class Close {

        @Test
        @DisplayName("방을 종료하면 상태가 CLOSED가 되고 종료 시각이 남는다")
        void close() {

            givenActiveSellerProfile();
            givenOwnedRoom();
            AuctionRoom auctionRoom = givenLockedRoom(AuctionRoomStatus.OPEN);
            givenInProgressCount(0);

            AuctionRoomPublicResponseDto response = auctionRoomCloseService.close(USER_ID, ROOM_ID);

            assertThat(auctionRoom.getStatus()).isEqualTo(AuctionRoomStatus.CLOSED);
            assertThat(auctionRoom.getClosedAt()).isNotNull();
            assertThat(response.status()).isEqualTo(AuctionRoomStatus.CLOSED);
            assertThat(response.closedAt())
                    .as("종료 화면이 '종료 {날짜}'를 그리는 값이라 응답에 실려야 한다")
                    .isEqualTo(auctionRoom.getClosedAt());
        }

        @Test
        @DisplayName("진행 중인 물품이 남아 있으면 종료를 거절한다")
        void rejectsRoomWithInProgressItem() {

            givenActiveSellerProfile();
            givenOwnedRoom();
            AuctionRoom auctionRoom = givenLockedRoom(AuctionRoomStatus.OPEN);
            givenInProgressCount(1);

            assertThatThrownBy(() -> auctionRoomCloseService.close(USER_ID, ROOM_ID))
                    .isInstanceOf(ApplicationException.class)
                    .hasFieldOrPropertyWithValue("errorType",
                            AuctionErrorType.AUCTION_ROOM_HAS_IN_PROGRESS_ITEM);

            assertThat(auctionRoom.getStatus())
                    .as("입찰이 붙어 있는 경매가 종료 요청 하나로 사라지면 안 된다")
                    .isEqualTo(AuctionRoomStatus.OPEN);
            verify(domainEventPublisher, never()).publish(any());
        }

        @Test
        @DisplayName("물품을 하나도 시작하지 않은 방(BEFORE)도 종료할 수 있다")
        void closesRoomBeforeAnyItemStarted() {

            givenActiveSellerProfile();
            givenOwnedRoom();
            AuctionRoom auctionRoom = givenLockedRoom(AuctionRoomStatus.BEFORE);
            givenInProgressCount(0);

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
            givenInProgressCount(0);

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

            assertThatThrownBy(() -> auctionRoomCloseService.close(USER_ID, ROOM_ID))
                    .isInstanceOf(ApplicationException.class)
                    .hasFieldOrPropertyWithValue("errorType", AuctionErrorType.AUCTION_ROOM_CLOSED);

            assertThat(auctionRoom.getClosedAt())
                    .as("두 번째 종료가 첫 종료 시각을 덮어쓰면 안 된다")
                    .isNull();
            verify(domainEventPublisher, never()).publish(any());
        }

        @Test
        @DisplayName("남의 방이거나 없는 방이면 방을 잠그기 전에 거절한다")
        void rejectsRoomNotOwned() {

            givenActiveSellerProfile();
            when(auctionRoomRepository.existsByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                    ROOM_ID, SELLER_PROFILE_ID)).thenReturn(false);

            assertThatThrownBy(() -> auctionRoomCloseService.close(USER_ID, ROOM_ID))
                    .isInstanceOf(ApplicationException.class)
                    .hasFieldOrPropertyWithValue("errorType", AuctionErrorType.AUCTION_ROOM_NOT_FOUND);

            verify(auctionRoomRepository, never()).findByIdForUpdate(ROOM_ID);
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

    private void givenInProgressCount(long count) {
        when(auctionItemRepository.countByAuctionRoom_AuctionRoomIdAndStatus(
                ROOM_ID, AuctionItemStatus.IN_PROGRESS)).thenReturn(count);
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
