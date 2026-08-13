package com.hot6ix.upbid.domain.bid.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.bid.entity.Bid;
import com.hot6ix.upbid.domain.bid.repository.BidRepository;
import com.hot6ix.upbid.domain.bid.stream.BidStreamEvent;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.event.DomainEvent;
import com.hot6ix.upbid.global.event.payload.BidPlaced;
import com.hot6ix.upbid.global.event.payload.SoftCloseExtended;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BidStreamPersistenceServiceTest {

    private static final long ITEM_ID = 101L;
    private static final long ROOM_ID = 202L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-13T13:00:00Z"), ZONE);

    @Mock
    private BidRepository bidRepository;
    @Mock
    private AuctionItemRepository auctionItemRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DomainEventPublisher domainEventPublisher;

    private BidStreamPersistenceService persistenceService;
    private AuctionItem auctionItem;

    @BeforeEach
    void setUp() {
        persistenceService = new BidStreamPersistenceService(
                bidRepository, auctionItemRepository, userRepository, domainEventPublisher, CLOCK);
        auctionItem = auctionItem();
    }

    @Test
    @DisplayName("Redis 승인 시각과 상태를 저장하고 기존 실시간 이벤트를 발행한다")
    void persistsAcceptedBidAndPublishesEvents() {

        User bidder = user(11L, "한기");
        BidStreamEvent.BidAccepted event = event(
                "request-1", bidder.getUserId(), 20_000L, 60, 60,
                Instant.parse("2026-08-13T13:04:00Z"),
                Instant.parse("2026-08-13T13:06:00Z"));
        givenNewEvent(event, bidder);

        persistenceService.persist(event);

        ArgumentCaptor<Bid> bidCaptor = ArgumentCaptor.forClass(Bid.class);
        verify(bidRepository).saveAndFlush(bidCaptor.capture());
        Bid saved = bidCaptor.getValue();
        assertThat(saved.getRequestId()).isEqualTo("request-1");
        assertThat(saved.getAcceptedAt()).isEqualTo(LocalDateTime.ofInstant(
                Instant.parse("2026-08-13T13:04:00Z"), ZONE));
        assertThat(auctionItem.getCurrentPrice()).isEqualTo(20_000L);
        assertThat(auctionItem.getLeaderUser()).isSameAs(bidder);
        assertThat(auctionItem.getEndAt()).isEqualTo(LocalDateTime.ofInstant(
                Instant.parse("2026-08-13T13:06:00Z"), ZONE));
        assertThat(auctionItem.getTotalExtensionSeconds()).isEqualTo(60);

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(domainEventPublisher, org.mockito.Mockito.times(2)).publish(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .extracting(Object::getClass)
                .containsExactly(BidPlaced.class, SoftCloseExtended.class);
    }

    @Test
    @DisplayName("이미 저장한 requestId는 Bid와 이벤트를 다시 만들지 않는다")
    void ignoresAlreadyPersistedRequest() {

        Bid existingBid = org.mockito.Mockito.mock(Bid.class);
        BidStreamEvent.BidAccepted event = event(
                "request-1", 11L, 20_000L, 0, 0,
                Instant.parse("2026-08-13T13:04:00Z"),
                Instant.parse("2026-08-13T13:05:00Z"));
        when(bidRepository.findByRequestId("request-1")).thenReturn(Optional.of(existingBid));

        persistenceService.persist(event);

        verify(bidRepository, never()).saveAndFlush(any(Bid.class));
        verifyNoInteractions(auctionItemRepository, userRepository, domainEventPublisher);
    }

    @Test
    @DisplayName("높은 입찰이 먼저 커밋되면 뒤늦은 낮은 입찰이 물품 상태를 되돌리지 않는다")
    void doesNotRegressItemWhenLowerBidPersistsLater() {

        User highBidder = user(12L, "고액 입찰자");
        User lowBidder = user(11L, "저액 입찰자");
        BidStreamEvent.BidAccepted high = event(
                "request-high", 12L, 20_000L, 60, 60,
                Instant.parse("2026-08-13T13:04:10Z"),
                Instant.parse("2026-08-13T13:06:00Z"));
        BidStreamEvent.BidAccepted low = event(
                "request-low", 11L, 10_000L, 30, 30,
                Instant.parse("2026-08-13T13:04:00Z"),
                Instant.parse("2026-08-13T13:05:30Z"));
        when(bidRepository.findByRequestId(any(String.class))).thenReturn(Optional.empty());
        givenSaveSucceeds();
        when(auctionItemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(auctionItem));
        when(userRepository.findById(12L)).thenReturn(Optional.of(highBidder));
        when(userRepository.findById(11L)).thenReturn(Optional.of(lowBidder));

        persistenceService.persist(high);
        persistenceService.persist(low);

        assertThat(auctionItem.getCurrentPrice()).isEqualTo(20_000L);
        assertThat(auctionItem.getLeaderUser()).isSameAs(highBidder);
        assertThat(auctionItem.getEndAt()).isEqualTo(LocalDateTime.ofInstant(
                Instant.parse("2026-08-13T13:06:00Z"), ZONE));
        assertThat(auctionItem.getTotalExtensionSeconds()).isEqualTo(60);
    }

    private void givenNewEvent(BidStreamEvent.BidAccepted event, User bidder) {
        when(bidRepository.findByRequestId(event.requestId())).thenReturn(Optional.empty());
        givenSaveSucceeds();
        when(auctionItemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(auctionItem));
        when(userRepository.findById(bidder.getUserId())).thenReturn(Optional.of(bidder));
    }

    private void givenSaveSucceeds() {
        when(bidRepository.saveAndFlush(any(Bid.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static BidStreamEvent.BidAccepted event(
            String requestId, long bidderUserId, long amount, int extendedSeconds,
            int totalExtensionSeconds, Instant acceptedAt, Instant endAt) {
        return new BidStreamEvent.BidAccepted(
                requestId, ITEM_ID, ROOM_ID, bidderUserId, amount,
                acceptedAt.toEpochMilli(), endAt.toEpochMilli(), extendedSeconds,
                totalExtensionSeconds);
    }

    private static User user(long userId, String nickname) {
        User user = User.builder()
                .email("user" + userId + "@hot6ix.com")
                .nickname(nickname)
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }

    private static AuctionItem auctionItem() {
        AuctionRoom room = AuctionRoom.builder()
                .name("한기의 경매방")
                .bidIncrement(1_000L)
                .softCloseTriggerSeconds(60)
                .softCloseExtendSeconds(60)
                .build();
        ReflectionTestUtils.setField(room, "auctionRoomId", ROOM_ID);
        Product product = Product.builder().name("한정판 피규어").build();
        AuctionItem item = AuctionItem.builder()
                .auctionRoom(room)
                .product(product)
                .startingPrice(10_000L)
                .bidIncrement(1_000L)
                .status(AuctionItemStatus.IN_PROGRESS)
                .endAt(LocalDateTime.ofInstant(Instant.parse("2026-08-13T13:05:00Z"), ZONE))
                .build();
        ReflectionTestUtils.setField(item, "auctionItemId", ITEM_ID);
        return item;
    }
}
