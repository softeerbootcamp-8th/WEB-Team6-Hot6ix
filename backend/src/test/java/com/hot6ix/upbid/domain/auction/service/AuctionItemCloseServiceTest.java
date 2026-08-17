package com.hot6ix.upbid.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.dto.request.AuctionItemCloseEarlyRequestDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemCloseEarlyResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisInitializer;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisStore;
import com.hot6ix.upbid.domain.auction.store.RedisCloseDecision;
import com.hot6ix.upbid.domain.auction.realtime.AuctionRealtimeSsePublisher;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.global.event.DomainEvent;
import com.hot6ix.upbid.global.event.payload.ItemEnded;
import com.hot6ix.upbid.global.event.payload.ItemPassed;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import com.hot6ix.upbid.global.exception.ApplicationException;
import java.time.LocalDateTime;
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
    private static final Long USER_ID = 50L;
    private static final Long SELLER_PROFILE_ID = 60L;

    @Mock
    private AuctionItemRepository auctionItemRepository;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    /** 조기 마감이 앞당긴 endAt을 Redis에도 쓴다(이슈 #246의 비교군 C). 여기서는 호출만 받는다. */
    @Mock
    private AuctionRedisStore auctionRedisStore;

    @Mock
    private AuctionRedisInitializer auctionRedisInitializer;

    @Mock
    private AuctionRealtimeSsePublisher auctionRealtimeSsePublisher;

    @InjectMocks
    private AuctionItemCloseService auctionItemCloseService;

    @Test
    @DisplayName("마감 시각이 아직 남았으면 닫지 않고 그 시각을 돌려준다")
    void deferWhenNotDueYet() {

        LocalDateTime endAt = millisPrecision(LocalDateTime.now().plusSeconds(30));
        when(auctionRedisStore.requestNaturalClose(eq(ITEM_ID), anyLong()))
                .thenReturn(new RedisCloseDecision.Rejected(
                        RedisCloseDecision.Reason.NOT_DUE, epochMillis(endAt)));

        Optional<LocalDateTime> rescheduleAt = auctionItemCloseService.closeIfDue(ITEM_ID);

        assertThat(rescheduleAt)
                .as("락을 기다리는 사이 Soft Close 연장이 커밋된 경우다. 여기서 닫으면 연장이 무시된다")
                .contains(endAt);
        verify(auctionItemRepository, never()).findByIdForUpdate(anyLong());
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("마감 시각이 지났으면 닫고 다시 예약할 시각을 남기지 않는다")
    void closeWhenDue() {

        when(auctionRedisStore.requestNaturalClose(eq(ITEM_ID), anyLong()))
                .thenReturn(new RedisCloseDecision.Closing(
                        ROOM_ID, ITEM_ID, "한정판 피규어", 10_000L,
                        null, null, System.currentTimeMillis(), System.currentTimeMillis()));

        Optional<LocalDateTime> rescheduleAt = auctionItemCloseService.closeIfDue(ITEM_ID);

        assertThat(rescheduleAt).isEmpty();
        verify(auctionItemRepository, never()).findByIdForUpdate(anyLong());
        verify(domainEventPublisher, never()).publish(any());
        verify(auctionRealtimeSsePublisher).publishClosing(any(RedisCloseDecision.Closing.class));
    }

    @Test
    @DisplayName("Redis가 확정한 앞당김 시각을 즉시 응답하고 DB 반영은 Consumer에 맡긴다")
    void closeEarlyAdvancesEndAt() {

        LocalDateTime advancedAt = millisPrecision(LocalDateTime.now());
        LocalDateTime endAt = advancedAt.plusSeconds(60);
        when(auctionRedisStore.requestSellerAdvance(ITEM_ID, USER_ID, null))
                .thenReturn(new RedisCloseDecision.Advanced(
                        ROOM_ID, ITEM_ID, "한정판 피규어",
                        epochMillis(endAt), 60, epochMillis(advancedAt)));

        AuctionItemCloseEarlyResponseDto response =
                auctionItemCloseService.closeEarly(USER_ID, ITEM_ID, null);

        assertThat(response.remainingSeconds()).isEqualTo(60);
        assertThat(response.endAt()).isEqualTo(endAt);
        verify(auctionItemRepository, never()).findByIdForUpdate(anyLong());
        verify(domainEventPublisher, never()).publish(any());
        verify(auctionRealtimeSsePublisher).publishAdvanced(any(RedisCloseDecision.Advanced.class));
    }

    @Test
    @DisplayName("남의 방 물품은 마감을 앞당길 수 없고 물품이 없을 때와 같은 응답을 준다")
    void closeEarlyRejectsOtherSellersItem() {

        when(auctionRedisStore.requestSellerAdvance(ITEM_ID, USER_ID, null))
                .thenReturn(new RedisCloseDecision.Rejected(
                        RedisCloseDecision.Reason.NOT_OWNER, null));

        assertThatThrownBy(() -> auctionItemCloseService.closeEarly(USER_ID, ITEM_ID, null))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(AuctionErrorType.AUCTION_ITEM_NOT_FOUND);
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("진행 중이 아닌 물품은 마감을 앞당길 수 없다")
    void closeEarlyRejectsNotInProgressItem() {

        when(auctionRedisStore.requestSellerAdvance(ITEM_ID, USER_ID, null))
                .thenReturn(new RedisCloseDecision.Rejected(
                        RedisCloseDecision.Reason.ITEM_NOT_IN_PROGRESS, null));

        assertThatThrownBy(() -> auctionItemCloseService.closeEarly(USER_ID, ITEM_ID, null))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(AuctionErrorType.AUCTION_ITEM_NOT_IN_PROGRESS);
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("이미 마감이 임박했으면 거절하고 마감 시각을 그대로 둔다")
    void closeEarlyRejectsAlreadyClosingSoonItem() {

        LocalDateTime endAt = LocalDateTime.now().plusSeconds(10);
        when(auctionRedisStore.requestSellerAdvance(ITEM_ID, USER_ID, null))
                .thenReturn(new RedisCloseDecision.Rejected(
                        RedisCloseDecision.Reason.ALREADY_CLOSING_SOON, epochMillis(endAt)));

        assertThatThrownBy(() -> auctionItemCloseService.closeEarly(USER_ID, ITEM_ID, null))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(AuctionErrorType.AUCTION_ITEM_ALREADY_CLOSING_SOON);
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("Redis Hash가 없으면 DB 스냅샷으로 초기화한 뒤 앞당기기를 다시 시도한다")
    void closeEarlySeedsMissingRedisState() {
        LocalDateTime endAt = millisPrecision(LocalDateTime.now().plusSeconds(60));
        when(auctionRedisStore.requestSellerAdvance(ITEM_ID, USER_ID, null))
                .thenReturn(
                        new RedisCloseDecision.Rejected(RedisCloseDecision.Reason.KEY_MISSING, null),
                        new RedisCloseDecision.Advanced(
                                ROOM_ID, ITEM_ID, "한정판 피규어",
                                epochMillis(endAt), 60, epochMillis(endAt.minusSeconds(60))));

        AuctionItemCloseEarlyResponseDto response =
                auctionItemCloseService.closeEarly(USER_ID, ITEM_ID, null);

        verify(auctionRedisInitializer).initialize(ITEM_ID);
        assertThat(response.endAt()).isEqualTo(endAt);
    }

    @Test
    @DisplayName("요청한 남은 초를 그대로 Lua에 넘기고 그 값이 응답에 실린다")
    void closeEarlyPassesRequestedRemainingSeconds() {

        LocalDateTime advancedAt = millisPrecision(LocalDateTime.now());
        LocalDateTime endAt = advancedAt.plusSeconds(600);
        when(auctionRedisStore.requestSellerAdvance(ITEM_ID, USER_ID, 600))
                .thenReturn(new RedisCloseDecision.Advanced(
                        ROOM_ID, ITEM_ID, "한정판 피규어",
                        epochMillis(endAt), 600, epochMillis(advancedAt)));

        AuctionItemCloseEarlyResponseDto response = auctionItemCloseService.closeEarly(
                USER_ID, ITEM_ID, new AuctionItemCloseEarlyRequestDto(600));

        assertThat(response.remainingSeconds()).isEqualTo(600);
        assertThat(response.endAt()).isEqualTo(endAt);
    }

    @Test
    @DisplayName("트리거보다 짧게 남기려 하면 임박 거절과 다른 에러로 옮긴다")
    void closeEarlyRejectsRemainingTooShort() {

        when(auctionRedisStore.requestSellerAdvance(ITEM_ID, USER_ID, 30))
                .thenReturn(new RedisCloseDecision.Rejected(
                        RedisCloseDecision.Reason.REMAINING_TOO_SHORT, null));

        assertThatThrownBy(() -> auctionItemCloseService.closeEarly(
                USER_ID, ITEM_ID, new AuctionItemCloseEarlyRequestDto(30)))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(AuctionErrorType.AUCTION_ITEM_CLOSE_EARLY_REMAINING_TOO_SHORT);
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("지금 마감보다 뒤로 남기려 하면 거절한다")
    void closeEarlyRejectsRemainingTooLong() {

        when(auctionRedisStore.requestSellerAdvance(ITEM_ID, USER_ID, 99999))
                .thenReturn(new RedisCloseDecision.Rejected(
                        RedisCloseDecision.Reason.REMAINING_TOO_LONG, null));

        assertThatThrownBy(() -> auctionItemCloseService.closeEarly(
                USER_ID, ITEM_ID, new AuctionItemCloseEarlyRequestDto(99999)))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(AuctionErrorType.AUCTION_ITEM_CLOSE_EARLY_REMAINING_TOO_LONG);
        verify(domainEventPublisher, never()).publish(any());
    }

    private static long epochMillis(LocalDateTime value) {
        return value.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static LocalDateTime millisPrecision(LocalDateTime value) {
        return value.withNano(value.getNano() / 1_000_000 * 1_000_000);
    }

    private DomainEvent publishedEvent() {
        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(domainEventPublisher).publish(captor.capture());
        return captor.getValue();
    }

    private AuctionItem givenLockedItem(AuctionItemStatus status) {
        return givenLockedItem(status, null);
    }

    private AuctionItem givenLockedItem(AuctionItemStatus status, LocalDateTime endAt) {
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
