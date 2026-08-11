package com.hot6ix.upbid.domain.bid.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.AuctionParticipantRepository;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisStore;
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
import java.time.Clock;
import java.time.LocalDateTime;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code BidService}가 접수된 입찰을 저장하는 경로만 본다.
 *
 * <p><b>금액·단위·마감·최고입찰자·판매자·참여 판정은 여기 없다.</b> 이슈 #246의 비교군 C에서
 * 그 판정이 {@code src/main/resources/lua/bid.lua}로 옮겨갔고, 판정이 Redis 안에서 돌기 때문에
 * Mockito로 흉내 낼 대상이 없다. 그 검증은 실제 Redis에 스크립트를 올려 돌리는
 * {@code perf/bid-lua-check.sh}에 있다.
 *
 * <p>옮기면서 검증이 <b>사라진</b> 것이 둘이다. 옛 테스트의 "락을 기다리다 마감을 넘겨도
 * 도착이 마감 전이었으면 받는다"와 "도착 때는 연장 구간 밖이었어도 락을 기다리는 사이
 * 들어왔으면 연장한다"는 물품 행 락이 없어져서 재현할 수 없다.
 *
 * <p>"진행중이 아닌 물품에는 입찰할 수 없다"는 자리가 옮겨갔다. 스크립트는 {@code endAt}만
 * 보므로 {@code status}를 보는 곳이 {@code seedFromDatabase} 하나 남았고, 그 검증은
 * "아직 시작하지 않은 물품은 채우는 단계에서 걸린다"에 있다.
 */
@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    private static final Long ROOM_ID = 1L;
    private static final Long ITEM_ID = 2L;
    private static final Long BIDDER_ID = 10L;
    private static final Long SELLER_ID = 20L;

    private static final long STARTING_PRICE = 10_000L;
    private static final long BID_INCREMENT = 1_000L;

    private static final int SOFT_CLOSE_TRIGGER_SECONDS = 30;
    private static final int SOFT_CLOSE_EXTEND_SECONDS = 30;

    @Mock
    private BidRepository bidRepository;

    @Mock
    private AuctionItemRepository auctionItemRepository;

    @Mock
    private AuctionParticipantRepository auctionParticipantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @Mock
    private AuctionRedisStore auctionRedisStore;

    private BidService bidService;

    private User bidder;

    @BeforeEach
    void setUp() {
        bidder = user(BIDDER_ID, "한기");
        bidService = new BidService(bidRepository, auctionItemRepository,
                auctionParticipantRepository, userRepository, domainEventPublisher,
                Clock.systemDefaultZone(), auctionRedisStore, noopTransactions());
    }

    @Test
    @DisplayName("Lua가 거절하면 DB를 아예 건드리지 않는다")
    void rejectedBidNeverReachesDatabase() {

        givenRedisReturns(3L);

        assertThatThrownBy(() -> bidService.place(ITEM_ID, BIDDER_ID, 9_000L))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(BidErrorType.BID_AMOUNT_TOO_LOW);

        // 이 검증이 비교군 C가 재려는 것 자체다. 거절된 요청이 커넥션도 트랜잭션도 안 쓴다.
        verifyNoInteractions(bidRepository, auctionItemRepository, userRepository);
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("Lua가 돌려준 거절 코드를 그대로 에러로 옮긴다")
    void mapsEveryRejectionCode() {

        assertRejection(1L, BidErrorType.ITEM_CLOSED);
        assertRejection(2L, BidErrorType.ALREADY_TOP_BIDDER);
        assertRejection(3L, BidErrorType.BID_AMOUNT_TOO_LOW);
        assertRejection(4L, BidErrorType.INVALID_BID_UNIT);
        assertRejection(5L, BidErrorType.SELLER_CANNOT_BID);
        assertRejection(6L, BidErrorType.TERMS_NOT_AGREED);
    }

    @Test
    @DisplayName("Lua가 모르는 값을 돌려주면 접수로 넘기지 않고 터진다")
    void unknownScriptResultIsNotTreatedAsAccepted() {

        givenRedisReturns(99L);

        assertThatThrownBy(() -> bidService.place(ITEM_ID, BIDDER_ID, 15_000L))
                .as("아무 거절 사유나 붙이면 index.csv의 거절 분포가 조용히 틀어진다")
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(bidRepository);
    }

    @Test
    @DisplayName("키가 없으면 DB에서 채우고 다시 판정한다")
    void seedsFromDatabaseThenEvaluatesAgain() {

        AuctionItem auctionItem = inProgressItem();
        when(auctionRedisStore.evaluateBid(anyLong(), anyLong(), anyLong(), anyLong()))
                .thenReturn(AuctionRedisStore.KEY_MISSING, AuctionRedisStore.ACCEPTED);
        when(auctionItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(auctionItem));
        when(auctionItemRepository.findSellerUserId(ITEM_ID)).thenReturn(Optional.of(SELLER_ID));
        when(auctionParticipantRepository.findAgreedUserIds(ROOM_ID)).thenReturn(List.of(BIDDER_ID));
        givenBidder();
        givenSaveSucceeds();

        bidService.place(ITEM_ID, BIDDER_ID, 15_000L);

        verify(auctionRedisStore).seed(auctionItem, ROOM_ID, SELLER_ID, List.of(BIDDER_ID));
    }

    @Test
    @DisplayName("채우려는 물품이 DB에도 없으면 없는 물품이다")
    void missingItemInDatabaseIsNotFound() {

        givenRedisReturns(AuctionRedisStore.KEY_MISSING);
        when(auctionItemRepository.findById(ITEM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bidService.place(ITEM_ID, BIDDER_ID, 15_000L))
                .as("Redis에 키가 없다는 것만으로는 '아직 안 채워졌다'와 '그런 물품이 없다'를 못 가른다")
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(AuctionErrorType.AUCTION_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("아직 시작하지 않은 물품은 채우는 단계에서 걸린다")
    void notStartedItemIsRejectedWhileSeeding() {

        givenRedisReturns(AuctionRedisStore.KEY_MISSING);
        when(auctionItemRepository.findById(ITEM_ID))
                .thenReturn(Optional.of(auctionItem(null, null, null)));

        assertThatThrownBy(() -> bidService.place(ITEM_ID, BIDDER_ID, 15_000L))
                .as("endAt이 null이면 Redis에 넣을 값이 없다. 스크립트는 endAt만 보므로 여기서 걸러야 한다")
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(BidErrorType.ITEM_NOT_IN_PROGRESS);

        verify(auctionRedisStore, never()).seed(any(), anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("없는 회원의 입찰은 접수 뒤 저장 단계에서 걸린다")
    void missingBidderFailsWhilePersisting() {

        givenRedisReturns(AuctionRedisStore.ACCEPTED);
        when(userRepository.findByUserIdAndDeletedAtIsNull(BIDDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bidService.place(ITEM_ID, BIDDER_ID, 15_000L))
                .as("Redis에서 이미 접수된 뒤라 되돌릴 수 없다. 이 경로가 5xx로 남는 것이 지금 설계다")
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(CommonErrorType.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("같은 금액 unique 위반은 동시 입찰 충돌로 바꿔 응답한다")
    void duplicateAmountBecomesConflict() {

        givenAccepted(inProgressItem());
        when(bidRepository.saveAndFlush(any(Bid.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> bidService.place(ITEM_ID, BIDDER_ID, 15_000L))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(BidErrorType.CONCURRENT_BID_CONFLICT);
    }

    @Test
    @DisplayName("입찰이 접수되면 BidPlaced 이벤트를 발행한다")
    void publishesBidPlaced() {

        givenAccepted(inProgressItem());
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
    @DisplayName("마감 임박 입찰은 마감을 밀고 그 값을 Redis에도 쓴다")
    void extendsAndWritesEndAtBackToRedis() {

        AuctionItem auctionItem = closingSoonItem();
        LocalDateTime endAtBefore = auctionItem.getEndAt();
        givenAccepted(auctionItem);
        givenSaveSucceeds();

        bidService.place(ITEM_ID, BIDDER_ID, 15_000L);

        assertThat(auctionItem.getEndAt())
                .isEqualTo(endAtBefore.plusSeconds(SOFT_CLOSE_EXTEND_SECONDS));

        verify(auctionRedisStore).updateEndAt(ITEM_ID, auctionItem.getEndAt());

        SoftCloseExtended published = (SoftCloseExtended) publishedEvents().get(1);
        assertThat(published.endAt()).isEqualTo(auctionItem.getEndAt());
    }

    @Test
    @DisplayName("연장 이벤트는 입찰 이벤트보다 나중에 나간다")
    void publishesExtensionAfterBid() {

        givenAccepted(closingSoonItem());
        givenSaveSucceeds();

        bidService.place(ITEM_ID, BIDDER_ID, 15_000L);

        assertThat(publishedEvents())
                .as("연장이 먼저 보이면 무엇 때문에 밀렸는지 이벤트 피드에서 알 수 없다")
                .map(Object::getClass)
                .containsExactly(BidPlaced.class, SoftCloseExtended.class);
    }

    @Test
    @DisplayName("마감이 아직 먼 입찰은 연장하지 않고 Redis도 안 건드린다")
    void doesNotExtendWhenNotClosingSoon() {

        AuctionItem auctionItem = auctionItem(LocalDateTime.now().plusMinutes(10), null,
                SOFT_CLOSE_TRIGGER_SECONDS);
        LocalDateTime endAtBefore = auctionItem.getEndAt();
        givenAccepted(auctionItem);
        givenSaveSucceeds();

        bidService.place(ITEM_ID, BIDDER_ID, 15_000L);

        assertThat(auctionItem.getEndAt()).isEqualTo(endAtBefore);
        verify(auctionRedisStore, never()).updateEndAt(anyLong(), any());
        assertThat(publishedEvents()).map(Object::getClass).containsExactly(BidPlaced.class);
    }

    @Test
    @DisplayName("뒤늦게 커밋된 낮은 금액은 현재가를 내리지 않는다")
    void lateLowerBidDoesNotLowerCurrentPrice() {

        // Lua는 물품당 한 줄로 세우지만 그 뒤 DB 트랜잭션은 각자라 커밋 순서가 뒤집힐 수 있다.
        AuctionItem auctionItem = inProgressItemLedBy(user(99L, "먼저"), 20_000L);
        givenAccepted(auctionItem);
        givenSaveSucceeds();

        bidService.place(ITEM_ID, BIDDER_ID, 15_000L);

        assertThat(auctionItem.getCurrentPrice())
                .as("내려가면 키가 날아갔을 때 seedFromDatabase가 낮은 값을 심는다")
                .isEqualTo(20_000L);
        assertThat(auctionItem.getLeaderUser().getNickname()).isEqualTo("먼저");
    }

    @Test
    @DisplayName("첫 입찰은 금액이 시작가와 같아도 현재가와 최고 입찰자를 채운다")
    void firstBidAppliesEvenWhenEqualToStartingPrice() {

        AuctionItem auctionItem = inProgressItem();
        givenAccepted(auctionItem);
        givenSaveSucceeds();

        bidService.place(ITEM_ID, BIDDER_ID, STARTING_PRICE);

        assertThat(auctionItem.getCurrentPrice()).isEqualTo(STARTING_PRICE);
        assertThat(auctionItem.getLeaderUser()).isEqualTo(bidder);
    }

    private void assertRejection(long scriptResult, BidErrorType expected) {

        givenRedisReturns(scriptResult);

        assertThatThrownBy(() -> bidService.place(ITEM_ID, BIDDER_ID, 15_000L))
                .isInstanceOf(ApplicationException.class)
                .extracting("errorType")
                .isEqualTo(expected);
    }

    private void givenRedisReturns(long result) {
        when(auctionRedisStore.evaluateBid(anyLong(), anyLong(), anyLong(), anyLong()))
                .thenReturn(result);
    }

    /** Lua가 접수했고, 저장 단계가 읽을 회원과 물품이 DB에 있는 상태. */
    private void givenAccepted(AuctionItem auctionItem) {
        givenRedisReturns(AuctionRedisStore.ACCEPTED);
        givenBidder();
        when(auctionItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(auctionItem));
    }

    private void givenBidder() {
        when(userRepository.findByUserIdAndDeletedAtIsNull(BIDDER_ID)).thenReturn(Optional.of(bidder));
    }

    /**
     * 저장된 입찰에는 ID와 접수 시각이 채워져 나온다. 응답과 이벤트가 그 값을 쓰므로
     * 실제 저장처럼 흉내낸다.
     */
    private void givenSaveSucceeds() {
        when(bidRepository.saveAndFlush(any(Bid.class))).thenAnswer(call -> {
            Bid bid = call.getArgument(0);
            ReflectionTestUtils.setField(bid, "bidId", 100L);
            ReflectionTestUtils.setField(bid, "acceptedAt", LocalDateTime.now());
            return bid;
        });
    }

    private List<DomainEvent> publishedEvents() {
        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(domainEventPublisher, atLeastOnce()).publish(captor.capture());
        return captor.getAllValues();
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

    private AuctionItem auctionItem(LocalDateTime endAt, User leader,
                                    Integer softCloseTriggerSeconds) {
        AuctionRoom auctionRoom = AuctionRoom.builder()
                .name("승민상점 경매방")
                .bidIncrement(1_000L)
                .softCloseTriggerSeconds(softCloseTriggerSeconds)
                .softCloseExtendSeconds(softCloseTriggerSeconds == null ? null : SOFT_CLOSE_EXTEND_SECONDS)
                .build();
        ReflectionTestUtils.setField(auctionRoom, "auctionRoomId", ROOM_ID);

        Product product = Product.builder().name("한정판피규어").build();

        AuctionItem auctionItem = AuctionItem.builder()
                .auctionRoom(auctionRoom)
                .product(product)
                .leaderUser(leader)
                .startingPrice(STARTING_PRICE)
                .bidIncrement(BID_INCREMENT)
                .status(AuctionItemStatus.IN_PROGRESS)
                .endAt(endAt)
                .build();
        ReflectionTestUtils.setField(auctionItem, "auctionItemId", ITEM_ID);
        return auctionItem;
    }

    private AuctionItem inProgressItem() {
        return auctionItem(LocalDateTime.now().plusHours(1), null, null);
    }

    private AuctionItem inProgressItemLedBy(User leader, long currentPrice) {
        AuctionItem auctionItem = auctionItem(LocalDateTime.now().plusHours(1), leader, null);
        ReflectionTestUtils.setField(auctionItem, "currentPrice", currentPrice);
        return auctionItem;
    }

    /** Soft Close 트리거 구간 안에 있는 물품. 지금 입찰하면 연장 대상이다. */
    private AuctionItem closingSoonItem() {
        return auctionItem(LocalDateTime.now().plusSeconds(SOFT_CLOSE_TRIGGER_SECONDS - 5L),
                null, SOFT_CLOSE_TRIGGER_SECONDS);
    }

    /**
     * 콜백을 그 자리에서 실행하는 {@code TransactionTemplate}. {@code BidService}가
     * 트랜잭션 경계를 프록시가 아니라 이 객체로 만들기 때문에, 목으로 두면 저장 경로가
     * 아예 안 돈다.
     */
    private static TransactionTemplate noopTransactions() {
        return new TransactionTemplate(new PlatformTransactionManager() {

            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        });
    }
}
