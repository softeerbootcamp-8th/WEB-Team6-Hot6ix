package com.hot6ix.upbid.domain.bid.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.bid.dto.response.BidCreateResponseDto;
import com.hot6ix.upbid.domain.bid.entity.Bid;
import com.hot6ix.upbid.domain.bid.exception.BidErrorType;
import com.hot6ix.upbid.domain.bid.repository.BidRepository;
import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.event.payload.BidPlaced;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.exception.CommonErrorType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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

    @Mock
    private BidRepository bidRepository;

    @Mock
    private AuctionItemRepository auctionItemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @InjectMocks
    private BidService bidService;

    private User bidder;

    @BeforeEach
    void setUp() {
        bidder = user(BIDDER_ID, "한기");
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
     */
    private AuctionItem auctionItem(AuctionItemStatus status, LocalDateTime endAt, User leader) {
        AuctionRoom auctionRoom = AuctionRoom.builder().name("승민상점 경매방").bidIncrement(1_000L).build();
        ReflectionTestUtils.setField(auctionRoom, "auctionRoomId", ROOM_ID);

        Product product = Product.builder().name("한정판피규어").build();

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

    /** 판매자는 입찰자와 다른 사람이라 판매자 검사를 통과한다. */
    private void givenItem(AuctionItem auctionItem) {
        givenItemSoldBy(auctionItem, SELLER_ID);
    }

    /**
     * 판매자 조회는 락을 잡기 전에 일어나 물품 조회와 별개 쿼리지만, 검사 자체는 락 안에서
     * 하므로 두 스텁이 항상 함께 필요하다.
     */
    private void givenItemSoldBy(AuctionItem auctionItem, Long sellerUserId) {
        when(auctionItemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(auctionItem));
        when(auctionItemRepository.findSellerUserId(ITEM_ID)).thenReturn(Optional.of(sellerUserId));
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
    @DisplayName("판매자는 자기 물품에 입찰할 수 없다")
    void rejectsSellerBiddingOnOwnItem() {

        givenBidder();
        givenItemSoldBy(inProgressItem(), BIDDER_ID);

        assertThatThrownBy(() -> bidService.place(ITEM_ID, BIDDER_ID, STARTING_PRICE))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(BidErrorType.SELLER_CANNOT_BID);

        verify(bidRepository, never()).saveAndFlush(any(Bid.class));
    }
    
    @Test
    @DisplayName("판매자가 아직 시작 안 한 자기 물품에 입찰하면 상태가 아니라 판매자 사유로 거절한다")
    void rejectsSellerBeforeItemStatus() {

        givenBidder();
        givenItemSoldBy(
                auctionItem(AuctionItemStatus.READY, LocalDateTime.now().plusHours(1), null),
                BIDDER_ID);

        assertThatThrownBy(() -> bidService.place(ITEM_ID, BIDDER_ID, STARTING_PRICE))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(BidErrorType.SELLER_CANNOT_BID);
    }

    @Test
    @DisplayName("없는 물품에는 입찰할 수 없고 물품 락도 잡지 않는다")
    void rejectsMissingItem() {

        givenBidder();
        when(auctionItemRepository.findSellerUserId(ITEM_ID)).thenReturn(Optional.empty());

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
}
