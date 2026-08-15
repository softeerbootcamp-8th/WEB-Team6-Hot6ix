package com.hot6ix.upbid.domain.bid.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.BidContextProjection;
import com.hot6ix.upbid.domain.bid.dto.response.BidCreateResponseDto;
import com.hot6ix.upbid.domain.bid.entity.Bid;
import com.hot6ix.upbid.domain.bid.exception.BidErrorType;
import com.hot6ix.upbid.domain.bid.repository.BidRepository;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.event.DomainEvent;
import com.hot6ix.upbid.global.event.payload.BidPlaced;
import com.hot6ix.upbid.global.event.payload.SoftCloseExtended;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.exception.CommonErrorType;
import com.hot6ix.upbid.support.ScriptedClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    private static final Long ROOM_ID = 1L;
    private static final Long ITEM_ID = 2L;
    private static final Long BIDDER_ID = 10L;
    private static final Long OTHER_BIDDER_ID = 11L;
    private static final Long SELLER_ID = 20L;

    private static final long STARTING_PRICE = 10_000L;
    private static final long BID_INCREMENT = 1_000L;

    /** 경계 테스트용 고정 마감 시각. 실제 시계와 무관해야 결과가 흔들리지 않는다. */
    private static final LocalDateTime FIXED_END_AT = LocalDateTime.of(2026, 8, 5, 12, 0);

    private static final int SOFT_CLOSE_TRIGGER_SECONDS = 30;
    private static final int SOFT_CLOSE_EXTEND_SECONDS = 30;
    private static final String PRODUCT_NAME = "한정판피규어";

    @Mock
    private BidRepository bidRepository;

    @Mock
    private AuctionItemRepository auctionItemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    private BidService bidService;

    private User bidder;

    @BeforeEach
    void setUp() {
        bidder = user(BIDDER_ID, "한기");
        bidService = newBidService(Clock.systemDefaultZone());
    }

    private BidService newBidService(Clock clock) {
        return new BidService(bidRepository, auctionItemRepository, userRepository,
                domainEventPublisher, clock, new BidMetrics(new SimpleMeterRegistry()));
    }

    /**
     * 시각을 읽는 순서대로 정해 둔다. 값을 둘 주면 <b>도착 시각과 락을 잡은 뒤의 시각</b>이
     * 갈리므로, 락을 기다리는 동안 시간이 흐른 상황을 재현할 수 있다.
     */
    private void givenClock(Instant... instants) {
        bidService = newBidService(ScriptedClock.of(instants));
    }

    private static Instant at(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant();
    }

    private User user(Long userId, String nickname) {
        User user = User.builder()
                .email("user" + userId + "@hot6ix.com")
                .password("password")
                .nickname(nickname)
                .phoneNumber("010-1234-5678")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }

    /**
     * 진행중이고 마감이 한참 남은 물품. 개별 테스트에서 상태·마감·최고입찰자만 바꿔 쓴다.
     *
     * <p>경매방에 Soft Close 설정을 두지 않아 연장이 일어나지 않는다. 연장을 보려면
     * {@link #auctionItem(AuctionItemStatus, LocalDateTime, User, Integer)}를 쓴다.
     */
    private AuctionItem auctionItem(AuctionItemStatus status, LocalDateTime endAt, User leader) {
        return auctionItem(status, endAt, leader, null);
    }

    private AuctionItem auctionItem(AuctionItemStatus status, LocalDateTime endAt, User leader,
                                    Integer softCloseTriggerSeconds) {
        AuctionRoom auctionRoom = AuctionRoom.builder()
                .name("승민상점 경매방")
                .bidIncrement(1_000L)
                .softCloseTriggerSeconds(softCloseTriggerSeconds)
                .softCloseExtendSeconds(softCloseTriggerSeconds == null ? null : SOFT_CLOSE_EXTEND_SECONDS)
                .build();
        ReflectionTestUtils.setField(auctionRoom, "auctionRoomId", ROOM_ID);

        Product product = Product.builder().name(PRODUCT_NAME).build();

        AuctionItem auctionItem = AuctionItem.builder()
                .auctionRoom(auctionRoom)
                .product(product)
                .leaderUser(leader)
                .startingPrice(STARTING_PRICE)
                .bidIncrement(BID_INCREMENT)
                .status(status)
                .endAt(endAt)
                .build();
        ReflectionTestUtils.setField(auctionItem, "auctionItemId", ITEM_ID);
        return auctionItem;
    }

    private AuctionItem inProgressItem() {
        return auctionItem(AuctionItemStatus.IN_PROGRESS, LocalDateTime.now().plusHours(1), null);
    }

    private AuctionItem inProgressItemLedBy(User leader, long currentPrice) {
        AuctionItem auctionItem =
                auctionItem(AuctionItemStatus.IN_PROGRESS, LocalDateTime.now().plusHours(1), leader);
        ReflectionTestUtils.setField(auctionItem, "currentPrice", currentPrice);
        return auctionItem;
    }

    private void givenBidder() {
        when(userRepository.findByUserIdAndDeletedAtIsNull(BIDDER_ID)).thenReturn(Optional.of(bidder));
    }

    /**
     * 판매자 검사는 락 앞에서 하므로 락 앞 조회 하나면 된다.
     *
     * <p>연장 설정을 비워 둔다. 이걸 쓰는 테스트는 판매자나 참여 기록에서 걸려 락까지 못 가므로
     * 연장 판정에 닿지 않는다.
     */
    private void givenSeller(Long sellerUserId) {
        when(auctionItemRepository.findBidContext(ITEM_ID)).thenReturn(
                Optional.of(new BidContextProjection(sellerUserId, PRODUCT_NAME, null, null)));
    }

    /**
     * 정상 경로 — 판매자가 남이고, 참여 기록이 있고, 물품을 락으로 읽는다.
     *
     * <p><b>락 앞 조회를 물품에서 뽑아 만든다.</b> 서비스가 상품명과 연장 설정을 엔티티가 아니라
     * 이 조회 결과에서 꺼내므로, 손으로 적은 값을 넣으면 물품과 어긋난 채로 통과해 버린다.
     */
    private void givenItem(AuctionItem auctionItem) {
        AuctionRoom auctionRoom = auctionItem.getAuctionRoom();
        when(auctionItemRepository.findBidContext(ITEM_ID)).thenReturn(
                Optional.of(new BidContextProjection(
                        SELLER_ID,
                        auctionItem.getProduct().getName(),
                        auctionRoom.getSoftCloseTriggerSeconds(),
                        auctionRoom.getSoftCloseExtendSeconds())));
        when(auctionItemRepository.existsParticipant(ITEM_ID, BIDDER_ID)).thenReturn(true);
        when(auctionItemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(auctionItem));
    }

    /**
     * 저장된 입찰에는 ID와 접수 시각이 채워져 나온다. 응답과 이벤트가 그 값을 쓰므로
     * 실제 저장처럼 흉내낸다.
     */
    private void givenSaveSucceeds() {
        when(bidRepository.saveAndFlush(any(Bid.class))).thenAnswer(invocation -> {
            Bid bid = invocation.getArgument(0);
            ReflectionTestUtils.setField(bid, "bidId", 100L);
            ReflectionTestUtils.setField(bid, "acceptedAt", LocalDateTime.now());
            return bid;
        });
    }

    @Test
    @DisplayName("입찰이 없으면 시작가와 같은 금액으로 첫 입찰을 할 수 있다")
    void acceptsFirstBidAtStartingPrice() {

        AuctionItem auctionItem = inProgressItem();
        givenBidder();
        givenItem(auctionItem);
        givenSaveSucceeds();

        BidCreateResponseDto response = bidService.place(ITEM_ID, BIDDER_ID, STARTING_PRICE);

        assertThat(response.amount()).isEqualTo(STARTING_PRICE);
        assertThat(auctionItem.getCurrentPrice()).isEqualTo(STARTING_PRICE);
        assertThat(auctionItem.getLeaderUser()).isEqualTo(bidder);
    }

    @Test
    @DisplayName("여러 단위를 한 번에 올리는 입찰도 받는다")
    void acceptsJumpBid() {

        AuctionItem auctionItem = inProgressItem();
        givenBidder();
        givenItem(auctionItem);
        givenSaveSucceeds();

        bidService.place(ITEM_ID, BIDDER_ID, 15_000L);

        assertThat(auctionItem.getCurrentPrice()).isEqualTo(15_000L);
    }

    @Test
    @DisplayName("입찰이 있으면 현재가 + 단위 이상이어야 통과한다")
    void acceptsExactlyOneIncrementAboveCurrentPrice() {

        AuctionItem auctionItem = inProgressItemLedBy(user(OTHER_BIDDER_ID, "원기"), 11_000L);
        givenBidder();
        givenItem(auctionItem);
        givenSaveSucceeds();

        bidService.place(ITEM_ID, BIDDER_ID, 12_000L);

        assertThat(auctionItem.getCurrentPrice()).isEqualTo(12_000L);
        assertThat(auctionItem.getLeaderUser()).isEqualTo(bidder);
    }

    @Test
    @DisplayName("최소 입찰 금액보다 낮으면 거절한다")
    void rejectsAmountBelowMinimum() {

        givenBidder();
        givenItem(inProgressItemLedBy(user(OTHER_BIDDER_ID, "원기"), 11_000L));

        assertThatThrownBy(() -> bidService.place(ITEM_ID, BIDDER_ID, 11_000L))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(BidErrorType.BID_AMOUNT_TOO_LOW);

        verify(bidRepository, never()).saveAndFlush(any(Bid.class));
    }

    @Test
    @DisplayName("입찰 단위에 맞지 않는 금액은 거절한다")
    void rejectsAmountOffTheIncrementGrid() {

        givenBidder();
        givenItem(inProgressItem());

        assertThatThrownBy(() -> bidService.place(ITEM_ID, BIDDER_ID, 11_500L))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(BidErrorType.INVALID_BID_UNIT);
    }

    @Test
    @DisplayName("진행중이 아닌 물품에는 입찰할 수 없다")
    void rejectsItemNotInProgress() {

        givenBidder();
        givenItem(auctionItem(AuctionItemStatus.READY, LocalDateTime.now().plusHours(1), null));

        assertThatThrownBy(() -> bidService.place(ITEM_ID, BIDDER_ID, STARTING_PRICE))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(BidErrorType.ITEM_NOT_IN_PROGRESS);
    }

    @Test
    @DisplayName("마감 시각이 지난 물품에는 입찰할 수 없다")
    void rejectsClosedItem() {

        givenBidder();
        givenItem(auctionItem(AuctionItemStatus.IN_PROGRESS, LocalDateTime.now().minusSeconds(1), null));

        assertThatThrownBy(() -> bidService.place(ITEM_ID, BIDDER_ID, STARTING_PRICE))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(BidErrorType.ITEM_CLOSED);
    }

    @Test
    @DisplayName("마감 시각이 없으면 마감 판정을 할 수 없으므로 거절한다")
    void rejectsItemWithoutEndAt() {

        givenBidder();
        givenItem(auctionItem(AuctionItemStatus.IN_PROGRESS, null, null));

        assertThatThrownBy(() -> bidService.place(ITEM_ID, BIDDER_ID, STARTING_PRICE))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(BidErrorType.ITEM_CLOSED);
    }

    @Test
    @DisplayName("이미 최고 입찰자면 다시 입찰할 수 없다")
    void rejectsWhenAlreadyTopBidder() {

        givenBidder();
        givenItem(inProgressItemLedBy(bidder, 11_000L));

        assertThatThrownBy(() -> bidService.place(ITEM_ID, BIDDER_ID, 12_000L))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(BidErrorType.ALREADY_TOP_BIDDER);
    }

    @Test
    @DisplayName("판매자는 자기 물품에 입찰할 수 없고 물품 락도 잡지 않는다")
    void rejectsSellerBiddingOnOwnItem() {

        givenBidder();
        givenSeller(BIDDER_ID);

        assertThatThrownBy(() -> bidService.place(ITEM_ID, BIDDER_ID, STARTING_PRICE))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(BidErrorType.SELLER_CANNOT_BID);

        verify(auctionItemRepository, never()).findByIdForUpdate(any());
        verify(bidRepository, never()).saveAndFlush(any(Bid.class));
    }

    @Test
    @DisplayName("판매자 본인은 참여 기록을 보기 전에 판매자 사유로 거절한다")
    void rejectsSellerBeforeParticipantCheck() {

        givenBidder();
        givenSeller(BIDDER_ID);

        assertThatThrownBy(() -> bidService.place(ITEM_ID, BIDDER_ID, STARTING_PRICE))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(BidErrorType.SELLER_CANNOT_BID);

        // 판매자는 자기 방에 약관 동의를 하지 않아 참여 행이 없다. 순서가 반대면
        // 통과할 수 없는 "약관에 동의하라"는 안내를 받는다.
        verify(auctionItemRepository, never()).existsParticipant(any(), any());
    }

    @Test
    @DisplayName("참여 기록이 없으면 입찰할 수 없고 물품 락도 잡지 않는다")
    void rejectsBidderWithoutParticipation() {

        givenBidder();
        givenSeller(SELLER_ID);
        when(auctionItemRepository.existsParticipant(ITEM_ID, BIDDER_ID)).thenReturn(false);

        assertThatThrownBy(() -> bidService.place(ITEM_ID, BIDDER_ID, STARTING_PRICE))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(BidErrorType.TERMS_NOT_AGREED);

        verify(auctionItemRepository, never()).findByIdForUpdate(any());
        verify(bidRepository, never()).saveAndFlush(any(Bid.class));
    }

    @Test
    @DisplayName("없는 물품에는 입찰할 수 없고 물품 락도 잡지 않는다")
    void rejectsMissingItem() {

        givenBidder();
        when(auctionItemRepository.findBidContext(ITEM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bidService.place(ITEM_ID, BIDDER_ID, STARTING_PRICE))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(AuctionErrorType.AUCTION_ITEM_NOT_FOUND);

        verify(auctionItemRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("없는 회원은 입찰할 수 없고 물품 락도 잡지 않는다")
    void rejectsMissingBidderBeforeLocking() {

        when(userRepository.findByUserIdAndDeletedAtIsNull(BIDDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bidService.place(ITEM_ID, BIDDER_ID, STARTING_PRICE))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(CommonErrorType.RESOURCE_NOT_FOUND);

        verify(auctionItemRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("같은 금액 unique 위반은 동시 입찰 충돌로 바꿔 응답한다")
    void translatesUniqueViolationToConflict() {

        givenBidder();
        givenItem(inProgressItem());
        when(bidRepository.saveAndFlush(any(Bid.class)))
                .thenThrow(new DataIntegrityViolationException("uk_bids_item_amount"));

        assertThatThrownBy(() -> bidService.place(ITEM_ID, BIDDER_ID, STARTING_PRICE))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(BidErrorType.CONCURRENT_BID_CONFLICT);
    }

    @Test
    @DisplayName("입찰이 접수되면 BidPlaced 이벤트를 발행한다")
    void publishesBidPlaced() {

        givenBidder();
        givenItem(inProgressItem());
        givenSaveSucceeds();

        bidService.place(ITEM_ID, BIDDER_ID, 15_000L);

        ArgumentCaptor<BidPlaced> captor = ArgumentCaptor.forClass(BidPlaced.class);
        verify(domainEventPublisher).publish(captor.capture());

        BidPlaced published = captor.getValue();
        assertThat(published.roomId()).isEqualTo(ROOM_ID);
        assertThat(published.itemId()).isEqualTo(ITEM_ID);
        assertThat(published.itemName()).isEqualTo("한정판피규어");
        assertThat(published.bidderNickname()).isEqualTo("한기");
        assertThat(published.bidPrice()).isEqualTo(15_000L);
    }

    @Test
    @DisplayName("거절된 입찰은 이벤트를 발행하지 않는다")
    void doesNotPublishOnRejection() {

        givenBidder();
        givenItem(inProgressItem());

        assertThatThrownBy(() -> bidService.place(ITEM_ID, BIDDER_ID, 9_000L))
                .isInstanceOf(ApplicationException.class);

        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("마감 임박 입찰은 같은 트랜잭션에서 마감을 밀고 SoftCloseExtended를 발행한다")
    void extendsOnClosingSoonBid() {

        AuctionItem auctionItem = closingSoonItem();
        LocalDateTime endAtBefore = auctionItem.getEndAt();
        givenBidder();
        givenItem(auctionItem);
        givenSaveSucceeds();

        bidService.place(ITEM_ID, BIDDER_ID, 15_000L);

        assertThat(auctionItem.getEndAt())
                .as("연장을 커밋 뒤로 미루면 그 사이에 마감이 끼어들어 방금 받은 입찰이 무시된다")
                .isEqualTo(endAtBefore.plusSeconds(SOFT_CLOSE_EXTEND_SECONDS));

        SoftCloseExtended published = (SoftCloseExtended) publishedEvents().get(1);
        assertThat(published.itemId()).isEqualTo(ITEM_ID);
        assertThat(published.extendSeconds()).isEqualTo(SOFT_CLOSE_EXTEND_SECONDS);
        assertThat(published.endAt())
                .as("화면과 스케줄러가 이 값으로 마감 시각을 다시 맞춘다")
                .isEqualTo(auctionItem.getEndAt());
    }

    @Test
    @DisplayName("연장 판정과 이벤트는 락 앞에서 읽은 값을 쓴다")
    void usesValuesReadBeforeLock() {

        // 물품의 경매방과 상품에는 값을 안 남긴다. 여기서 값이 나오면 락을 쥔 채로 지연 로딩이
        // 돌았다는 뜻이라, 그 SELECT 만큼 같은 물품에 몰린 다른 입찰이 뒤로 밀린다.
        AuctionItem auctionItem = auctionItem(AuctionItemStatus.IN_PROGRESS,
                LocalDateTime.now().plusSeconds(SOFT_CLOSE_TRIGGER_SECONDS - 5L), null, null);
        LocalDateTime endAtBefore = auctionItem.getEndAt();

        givenBidder();
        when(auctionItemRepository.findBidContext(ITEM_ID)).thenReturn(
                Optional.of(new BidContextProjection(SELLER_ID, "락 앞에서 읽은 이름",
                        SOFT_CLOSE_TRIGGER_SECONDS, SOFT_CLOSE_EXTEND_SECONDS)));
        when(auctionItemRepository.existsParticipant(ITEM_ID, BIDDER_ID)).thenReturn(true);
        when(auctionItemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(auctionItem));
        givenSaveSucceeds();

        bidService.place(ITEM_ID, BIDDER_ID, 15_000L);

        assertThat(auctionItem.getEndAt())
                .as("물품의 방에는 설정이 없다. 연장이 일어났다면 락 앞에서 읽은 설정을 쓴 것이다")
                .isEqualTo(endAtBefore.plusSeconds(SOFT_CLOSE_EXTEND_SECONDS));

        assertThat(((BidPlaced) publishedEvents().get(0)).itemName()).isEqualTo("락 앞에서 읽은 이름");
        assertThat(((SoftCloseExtended) publishedEvents().get(1)).itemName())
                .isEqualTo("락 앞에서 읽은 이름");
    }

    @Test
    @DisplayName("연장 이벤트는 입찰 이벤트보다 나중에 나간다")
    void publishesExtensionAfterBid() {

        givenBidder();
        givenItem(closingSoonItem());
        givenSaveSucceeds();

        bidService.place(ITEM_ID, BIDDER_ID, 15_000L);

        assertThat(publishedEvents())
                .as("연장이 먼저 보이면 무엇 때문에 밀렸는지 이벤트 피드에서 알 수 없다")
                .map(Object::getClass)
                .containsExactly(BidPlaced.class, SoftCloseExtended.class);
    }

    @Test
    @DisplayName("마감이 아직 먼 입찰은 연장하지 않는다")
    void doesNotExtendWhenNotClosingSoon() {

        AuctionItem auctionItem = auctionItem(AuctionItemStatus.IN_PROGRESS,
                LocalDateTime.now().plusMinutes(10), null, SOFT_CLOSE_TRIGGER_SECONDS);
        LocalDateTime endAtBefore = auctionItem.getEndAt();
        givenBidder();
        givenItem(auctionItem);
        givenSaveSucceeds();

        bidService.place(ITEM_ID, BIDDER_ID, 15_000L);

        assertThat(auctionItem.getEndAt()).isEqualTo(endAtBefore);
        assertThat(publishedEvents()).map(Object::getClass).containsExactly(BidPlaced.class);
    }

    @Test
    @DisplayName("마감 1밀리초 전에 도착한 입찰은 받는다")
    void acceptsOneMilliBeforeDeadline() {

        givenClock(at(FIXED_END_AT).minusMillis(1));
        givenBidder();
        givenItem(itemEndingAt(FIXED_END_AT));
        givenSaveSucceeds();

        assertThatCode(() -> bidService.place(ITEM_ID, BIDDER_ID, 15_000L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("마감 정각에 도착한 입찰은 거절한다")
    void rejectsExactlyAtDeadline() {

        givenClock(at(FIXED_END_AT));
        givenBidder();
        givenItem(itemEndingAt(FIXED_END_AT));

        assertThatThrownBy(() -> bidService.place(ITEM_ID, BIDDER_ID, 15_000L))
                .as("정각을 받을지 말지는 취향이 아니라 명세다. end_at은 '이 시각부터 닫힘'을 뜻한다")
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(BidErrorType.ITEM_CLOSED);
    }

    @Test
    @DisplayName("마감 1밀리초 뒤에 도착한 입찰은 거절한다")
    void rejectsOneMilliAfterDeadline() {

        givenClock(at(FIXED_END_AT).plusMillis(1));
        givenBidder();
        givenItem(itemEndingAt(FIXED_END_AT));

        assertThatThrownBy(() -> bidService.place(ITEM_ID, BIDDER_ID, 15_000L))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(BidErrorType.ITEM_CLOSED);
    }

    @Test
    @DisplayName("락을 기다리다 마감을 넘겨도 도착이 마감 전이었으면 받는다")
    void acceptsWhenArrivedBeforeDeadlineDespiteLockWait() {

        // 마감 100ms 전에 도착했지만 락을 300ms 기다려 판정은 마감 200ms 뒤에 이뤄졌다.
        givenClock(at(FIXED_END_AT).minusMillis(100), at(FIXED_END_AT).plusMillis(200));
        givenBidder();
        givenItem(itemEndingAt(FIXED_END_AT));
        givenSaveSucceeds();

        assertThatCode(() -> bidService.place(ITEM_ID, BIDDER_ID, 15_000L))
                .as("앞사람이 같은 물품에 입찰 중이라 줄을 섰을 뿐인데 그 대기가 불이익이 되면 안 된다")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("도착 때는 연장 구간 밖이었어도 락을 기다리는 사이 들어왔으면 연장한다")
    void extendsByPostLockTimeNotArrivalTime() {

        // 연장 구간은 마감 30초 전부터다. 도착은 35초 전(밖), 판정은 25초 전(안).
        givenClock(at(FIXED_END_AT).minusSeconds(35), at(FIXED_END_AT).minusSeconds(25));
        AuctionItem auctionItem = auctionItem(AuctionItemStatus.IN_PROGRESS, FIXED_END_AT, null,
                SOFT_CLOSE_TRIGGER_SECONDS);
        givenBidder();
        givenItem(auctionItem);
        givenSaveSucceeds();

        bidService.place(ITEM_ID, BIDDER_ID, 15_000L);

        assertThat(auctionItem.getEndAt())
                .as("연장까지 도착 시각으로 판정하면 실제로는 임박한 입찰을 놓친다")
                .isEqualTo(FIXED_END_AT.plusSeconds(SOFT_CLOSE_EXTEND_SECONDS));
    }

    private AuctionItem itemEndingAt(LocalDateTime endAt) {
        return auctionItem(AuctionItemStatus.IN_PROGRESS, endAt, null);
    }

    private List<DomainEvent> publishedEvents() {
        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(domainEventPublisher, atLeastOnce()).publish(captor.capture());
        return captor.getAllValues();
    }

    /** Soft Close 트리거 구간 안에 있는 물품. 지금 입찰하면 연장 대상이다. */
    private AuctionItem closingSoonItem() {
        return auctionItem(AuctionItemStatus.IN_PROGRESS,
                LocalDateTime.now().plusSeconds(SOFT_CLOSE_TRIGGER_SECONDS - 5L),
                null, SOFT_CLOSE_TRIGGER_SECONDS);
    }
}
