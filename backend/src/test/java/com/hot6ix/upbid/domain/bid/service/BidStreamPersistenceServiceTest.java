package com.hot6ix.upbid.domain.bid.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.hot6ix.upbid.domain.bid.stream.BidStreamFailureCode;
import com.hot6ix.upbid.domain.bid.stream.BidStreamPermanentException;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.event.DomainEvent;
import com.hot6ix.upbid.global.event.payload.BidPlaced;
import com.hot6ix.upbid.global.event.payload.ItemCloseAdvanced;
import com.hot6ix.upbid.global.event.payload.ItemEnded;
import com.hot6ix.upbid.global.event.payload.ItemPassed;
import com.hot6ix.upbid.global.event.payload.SoftCloseExtended;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
    @DisplayName("이미 저장한 requestId의 물품과 입찰자와 금액이 같으면 다시 저장하지 않는다")
    void ignoresAlreadyPersistedRequest() {

        User bidder = user(11L, "한기");
        Bid existingBid = Bid.builder()
                .requestId("request-1")
                .auctionItem(auctionItem)
                .bidder(bidder)
                .amount(20_000L)
                .acceptedAt(LocalDateTime.ofInstant(
                        Instant.parse("2026-08-13T13:04:00Z"), ZONE))
                .build();
        BidStreamEvent.BidAccepted event = event(
                "request-1", 11L, 20_000L, 0, 0,
                Instant.parse("2026-08-13T13:04:00Z"),
                Instant.parse("2026-08-13T13:05:00Z"));
        when(bidRepository.findByRequestId("request-1")).thenReturn(Optional.of(existingBid));

        persistenceService.persist(event);

        verify(bidRepository, never()).saveAndFlush(any(Bid.class));
        verifyNoInteractions(auctionItemRepository, userRepository, domainEventPublisher);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("conflictingRedeliveries")
    @DisplayName("이미 저장한 requestId의 fingerprint가 다르면 Stream 불일치로 실패한다")
    void rejectsConflictingAlreadyPersistedRequest(
            String description,
            BidStreamEvent.BidAccepted conflictingEvent) {

        User bidder = user(11L, "한기");
        Bid existingBid = Bid.builder()
                .requestId("request-1")
                .auctionItem(auctionItem)
                .bidder(bidder)
                .amount(20_000L)
                .acceptedAt(LocalDateTime.ofInstant(
                        Instant.parse("2026-08-13T13:04:00Z"), ZONE))
                .build();
        when(bidRepository.findByRequestId("request-1")).thenReturn(Optional.of(existingBid));

        assertThatThrownBy(() -> persistenceService.persist(conflictingEvent))
                .isInstanceOfSatisfying(BidStreamPermanentException.class,
                        error -> assertThat(error.getCode())
                                .isEqualTo(BidStreamFailureCode.IDEMPOTENCY_CONFLICT))
                .hasMessageContaining("request-1");
        verify(bidRepository, never()).saveAndFlush(any(Bid.class));
        verifyNoInteractions(auctionItemRepository, userRepository, domainEventPublisher);
    }

    @Test
    @DisplayName("승인 이벤트의 입찰자가 MySQL에 없으면 재시도로 해결되지 않는 실패다")
    void rejectsMissingBidderAsPermanentFailure() {
        BidStreamEvent.BidAccepted event = event(
                "request-missing-bidder", 99L, 20_000L, 0, 0,
                Instant.parse("2026-08-13T13:04:00Z"),
                Instant.parse("2026-08-13T13:05:00Z"));
        when(bidRepository.findByRequestId("request-missing-bidder"))
                .thenReturn(Optional.empty());
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> persistenceService.persist(event))
                .isInstanceOfSatisfying(BidStreamPermanentException.class,
                        error -> assertThat(error.getCode())
                                .isEqualTo(BidStreamFailureCode.REFERENCED_RESOURCE_MISSING));

        verifyNoInteractions(auctionItemRepository, domainEventPublisher);
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

    @Test
    @DisplayName("ITEM_CLOSING 스냅샷의 최고 입찰자를 반영하고 낙찰로 최종화한다")
    void finalizesSoldItemFromClosingSnapshot() {
        User leader = user(11L, "낙찰자");
        when(auctionItemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(auctionItem));
        when(userRepository.findById(11L)).thenReturn(Optional.of(leader));

        persistenceService.persist(new BidStreamEvent.ItemClosing(
                ITEM_ID, ROOM_ID, 20_000L, 11L,
                Instant.parse("2026-08-13T13:06:00Z").toEpochMilli(), 60,
                "NATURAL", Instant.parse("2026-08-13T13:06:01Z").toEpochMilli()));

        assertThat(auctionItem.getStatus()).isEqualTo(AuctionItemStatus.SOLD);
        assertThat(auctionItem.getCurrentPrice()).isEqualTo(20_000L);
        assertThat(auctionItem.getLeaderUser()).isSameAs(leader);
        verify(domainEventPublisher).publish(any(ItemEnded.class));
    }

    @Test
    @DisplayName("최고 입찰자가 없는 ITEM_CLOSING은 유찰로 최종화한다")
    void finalizesFailedItemWithoutLeader() {
        when(auctionItemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(auctionItem));

        persistenceService.persist(new BidStreamEvent.ItemClosing(
                ITEM_ID, ROOM_ID, 10_000L, null,
                Instant.parse("2026-08-13T13:06:00Z").toEpochMilli(), 0,
                "NATURAL", Instant.parse("2026-08-13T13:06:01Z").toEpochMilli()));

        assertThat(auctionItem.getStatus()).isEqualTo(AuctionItemStatus.FAILED);
        verify(domainEventPublisher).publish(any(ItemPassed.class));
        verify(userRepository, never()).findById(any(Long.class));
    }

    @Test
    @DisplayName("ITEM_CLOSE_ADVANCED를 DB에 반영한 뒤 기존 앞당김 이벤트를 발행한다")
    void persistsCloseAdvanced() {
        when(auctionItemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(auctionItem));

        persistenceService.persist(new BidStreamEvent.ItemCloseAdvanced(
                ITEM_ID, ROOM_ID,
                Instant.parse("2026-08-13T13:01:00Z").toEpochMilli(), 60,
                Instant.parse("2026-08-13T13:00:00Z").toEpochMilli()));

        assertThat(auctionItem.getEndAt()).isEqualTo(LocalDateTime.ofInstant(
                Instant.parse("2026-08-13T13:01:00Z"), ZONE));
        assertThat(auctionItem.getNotifiedAt()).isEqualTo(LocalDateTime.ofInstant(
                Instant.parse("2026-08-13T13:00:00Z"), ZONE));
        verify(domainEventPublisher).publish(any(ItemCloseAdvanced.class));
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

    private static Stream<Arguments> conflictingRedeliveries() {
        long acceptedAt = Instant.parse("2026-08-13T13:04:00Z").toEpochMilli();
        long endAt = Instant.parse("2026-08-13T13:05:00Z").toEpochMilli();
        return Stream.of(
                Arguments.of("물품이 다름", new BidStreamEvent.BidAccepted(
                        "request-1", ITEM_ID + 1, ROOM_ID, 11L, 20_000L,
                        acceptedAt, endAt, 0, 0)),
                Arguments.of("입찰자가 다름", new BidStreamEvent.BidAccepted(
                        "request-1", ITEM_ID, ROOM_ID, 12L, 20_000L,
                        acceptedAt, endAt, 0, 0)),
                Arguments.of("금액이 다름", new BidStreamEvent.BidAccepted(
                        "request-1", ITEM_ID, ROOM_ID, 11L, 21_000L,
                        acceptedAt, endAt, 0, 0)));
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
