package com.hot6ix.upbid.domain.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.exception.AuctionItemErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.bid.dto.BidderRankProjection;
import com.hot6ix.upbid.domain.bid.repository.BidRepository;
import com.hot6ix.upbid.domain.deal.entity.DealCandidate;
import com.hot6ix.upbid.domain.deal.entity.DealCandidateStatus;
import com.hot6ix.upbid.domain.deal.exception.DealErrorType;
import com.hot6ix.upbid.domain.deal.repository.DealCandidateRepository;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.event.DomainEvent;
import com.hot6ix.upbid.global.event.EventType;
import com.hot6ix.upbid.global.event.payload.DealRightAssigned;
import com.hot6ix.upbid.global.event.payload.ItemEnded;
import com.hot6ix.upbid.global.event.payload.WinnerDecided;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import com.hot6ix.upbid.global.exception.ApplicationException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DealCandidateServiceTest {

    private static final Long ROOM_ID = 1L;
    private static final Long ITEM_ID = 2L;
    private static final Long SELLER_ID = 7L;
    private static final Long OTHER_USER_ID = 8L;
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 7, 29, 21, 0);

    @Mock
    private AuctionItemRepository auctionItemRepository;

    @Mock
    private DealCandidateRepository dealCandidateRepository;

    @Mock
    private BidRepository bidRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @InjectMocks
    private DealCandidateService dealCandidateService;

    @Captor
    private ArgumentCaptor<List<DealCandidate>> candidatesCaptor;

    @Captor
    private ArgumentCaptor<DomainEvent> eventCaptor;

    /** projection 구현체가 없어 테스트용으로 만든다. record 컴포넌트 이름이 접근자가 된다. */
    private record Rank(Long getBidderUserId, Long getAmount) implements BidderRankProjection {
    }

    private ItemEnded itemEnded() {
        return ItemEnded.of(ROOM_ID, ITEM_ID, "포토카드", 15_000L, "원기", OCCURRED_AT);
    }

    /** 판매자 검증이 연관 관계를 따라가므로 모두 채운다. ID는 빌더로 못 넣어 리플렉션을 쓴다. */
    private AuctionItem soldItem(AuctionItemStatus status) {

        User seller = user(SELLER_ID);
        SellerProfile sellerProfile = SellerProfile.builder()
                .user(seller)
                .storeName("승민상점")
                .build();
        AuctionRoom auctionRoom = AuctionRoom.builder()
                .sellerProfile(sellerProfile)
                .name("승민상점 경매방")
                .build();
        ReflectionTestUtils.setField(auctionRoom, "auctionRoomId", ROOM_ID);

        AuctionItem auctionItem = AuctionItem.builder()
                .auctionRoom(auctionRoom)
                .startingPrice(10_000L)
                .bidIncrement(1_000L)
                .status(status)
                .build();
        ReflectionTestUtils.setField(auctionItem, "auctionItemId", ITEM_ID);
        return auctionItem;
    }

    private User user(Long userId) {
        User user = User.builder()
                .email("user" + userId + "@hot6ix.com")
                .password("password")
                .nickname("회원" + userId)
                .phoneNumber("010-1234-5678")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }

    private DealCandidate candidate(Long candidateId, int rank, long bidAmount, DealCandidateStatus status) {
        DealCandidate candidate = DealCandidate.builder()
                .bidder(user(100L + rank))
                .candidateRank(rank)
                .bidAmount(bidAmount)
                .status(status)
                .build();
        ReflectionTestUtils.setField(candidate, "dealCandidateId", candidateId);
        return candidate;
    }

    private void givenLockedItem() {
        givenLockedItem(AuctionItemStatus.SOLD);
    }

    private void givenLockedItem(AuctionItemStatus status) {
        when(auctionItemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(soldItem(status)));
    }

    private void givenCandidates(DealCandidate... candidates) {
        when(dealCandidateRepository.findAllByAuctionItemId(ITEM_ID)).thenReturn(List.of(candidates));
    }

    private void givenTopBidders(BidderRankProjection... bidders) {
        when(bidRepository.findTopBidders(anyLong(), anyInt())).thenReturn(List.of(bidders));
        givenBidderReferences();
        givenSaveAssignsIds();
    }

    /** 후보에서 회원 ID를 읽어 이벤트에 실으므로 요청한 id를 가진 회원을 돌려준다. */
    private void givenBidderReferences() {
        when(userRepository.getReferenceById(anyLong()))
                .thenAnswer(invocation -> user(invocation.getArgument(0)));
    }

    /** 후보 ID는 INSERT 시점에 채워진다. 저장 결과로 이벤트를 만들므로 그 동작을 흉내낸다. */
    private void givenSaveAssignsIds() {
        when(dealCandidateRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<DealCandidate> saved = invocation.getArgument(0);
            long generatedId = 100L;
            for (DealCandidate candidate : saved) {
                ReflectionTestUtils.setField(candidate, "dealCandidateId", generatedId++);
            }
            return saved;
        });
    }

    @Test
    @DisplayName("상위 입찰자를 조회 순서대로 1순위부터 후보로 저장한다")
    void awardSavesCandidatesInRankOrder() {

        givenLockedItem();
        givenTopBidders(new Rank(11L, 15_000L), new Rank(22L, 13_000L), new Rank(33L, 12_000L));

        dealCandidateService.award(itemEnded());

        verify(dealCandidateRepository).saveAll(candidatesCaptor.capture());
        assertThat(candidatesCaptor.getValue())
                .extracting(DealCandidate::getCandidateRank)
                .containsExactly(1, 2, 3);
        assertThat(candidatesCaptor.getValue())
                .extracting(DealCandidate::getBidAmount)
                .containsExactly(15_000L, 13_000L, 12_000L);
    }

    @Test
    @DisplayName("후보 조회는 상한 5명으로 요청한다")
    void awardRequestsAtMostFiveBidders() {

        givenLockedItem();
        givenTopBidders(new Rank(11L, 15_000L));

        dealCandidateService.award(itemEnded());

        verify(bidRepository).findTopBidders(ITEM_ID, 5);
    }

    /** 마감 시점은 거래할 차례가 온 것이지 최종 확정이 아니므로 WinnerDecided가 아니다. */
    @Test
    @DisplayName("1순위에게 낙찰 권한을 주는 DealRightAssigned를 발행한다")
    void awardPublishesDealRightForFirstRank() {

        givenLockedItem();
        givenTopBidders(new Rank(11L, 15_000L), new Rank(22L, 13_000L));

        dealCandidateService.award(itemEnded());

        verify(domainEventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(DealRightAssigned.class);

        DealRightAssigned published = (DealRightAssigned) eventCaptor.getValue();
        assertThat(published.type()).isEqualTo(EventType.DEAL_RIGHT_ASSIGNED);
        assertThat(published.roomId()).isEqualTo(ROOM_ID);
        assertThat(published.itemId()).isEqualTo(ITEM_ID);
        assertThat(published.candidateRank()).isEqualTo(1);
        assertThat(published.bidderUserId()).isEqualTo(11L);
        assertThat(published.bidAmount()).isEqualTo(15_000L);
        assertThat(published.dealCandidateId()).isNotNull();
    }

    @Test
    @DisplayName("후보가 이미 있으면 다시 만들지 않고 이벤트도 발행하지 않는다")
    void awardIsIdempotent() {

        givenLockedItem();
        when(dealCandidateRepository.existsByAuctionItem_AuctionItemId(ITEM_ID)).thenReturn(true);

        dealCandidateService.award(itemEnded());

        verify(bidRepository, never()).findTopBidders(anyLong(), anyInt());
        verify(dealCandidateRepository, never()).saveAll(any());
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("입찰이 없으면 후보를 만들지 않고 이벤트도 발행하지 않는다")
    void awardDoesNothingWhenNoBid() {

        givenLockedItem();
        when(bidRepository.findTopBidders(anyLong(), anyInt())).thenReturn(List.of());

        dealCandidateService.award(itemEnded());

        verify(dealCandidateRepository, never()).saveAll(any());
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("물품이 없으면 AUCTION_ITEM_NOT_FOUND 예외가 발생한다")
    void awardThrowsWhenItemNotFound() {

        when(auctionItemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dealCandidateService.award(itemEnded()))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorType", AuctionItemErrorType.AUCTION_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("후보를 만들기 전에 물품 행에 락을 건다")
    void awardLocksItemBeforeCheckingCandidates() {

        givenLockedItem();
        when(dealCandidateRepository.existsByAuctionItem_AuctionItemId(ITEM_ID)).thenReturn(true);

        dealCandidateService.award(itemEnded());

        // 락 없이 존재 검사를 하면 동시 요청이 둘 다 "없음"을 보고 각각 후보를 만든다.
        InOrder inOrder = inOrder(auctionItemRepository, dealCandidateRepository);
        inOrder.verify(auctionItemRepository).findByIdForUpdate(ITEM_ID);
        inOrder.verify(dealCandidateRepository).existsByAuctionItem_AuctionItemId(ITEM_ID);
    }

    @Test
    @DisplayName("거래 실패를 기록하면 차순위 후보에게 DealRightAssigned를 발행한다")
    void failTransfersToNextCandidate() {

        givenLockedItem();
        DealCandidate first = candidate(101L, 1, 15_000L, DealCandidateStatus.WAITING);
        DealCandidate second = candidate(102L, 2, 13_000L, DealCandidateStatus.WAITING);
        givenCandidates(first, second);

        dealCandidateService.fail(ITEM_ID, 101L, SELLER_ID);

        assertThat(first.getStatus()).isEqualTo(DealCandidateStatus.FAILED);
        assertThat(first.getFailedAt()).isNotNull();

        verify(domainEventPublisher).publish(eventCaptor.capture());
        DealRightAssigned published = (DealRightAssigned) eventCaptor.getValue();
        assertThat(published.roomId()).isEqualTo(ROOM_ID);
        assertThat(published.dealCandidateId()).isEqualTo(102L);
        assertThat(published.candidateRank()).isEqualTo(2);
        assertThat(published.bidderUserId()).isEqualTo(second.getBidder().getUserId());
        assertThat(published.bidAmount()).isEqualTo(13_000L);
    }

    @Test
    @DisplayName("1순위가 실패한 뒤에는 2순위가 현재 낙찰자가 된다")
    void failAcceptsSecondRankAfterFirstFailed() {

        givenLockedItem();
        DealCandidate second = candidate(102L, 2, 13_000L, DealCandidateStatus.WAITING);
        givenCandidates(candidate(101L, 1, 15_000L, DealCandidateStatus.FAILED), second);

        dealCandidateService.fail(ITEM_ID, 102L, SELLER_ID);

        assertThat(second.getStatus()).isEqualTo(DealCandidateStatus.FAILED);
    }

    /** 이미 후보였던 회원은 제외하고, 순위는 기존 최대 다음부터 이어져야 한다. */
    @Test
    @DisplayName("후보가 모두 소진되면 다음 순위를 보충해 낙찰 권한을 넘긴다")
    void failTopsUpWhenCandidatesExhausted() {

        givenLockedItem();
        DealCandidate last = candidate(105L, 5, 11_000L, DealCandidateStatus.WAITING);
        givenCandidates(candidate(101L, 1, 15_000L, DealCandidateStatus.FAILED),
                candidate(102L, 2, 14_000L, DealCandidateStatus.FAILED),
                candidate(103L, 3, 13_000L, DealCandidateStatus.FAILED),
                candidate(104L, 4, 12_000L, DealCandidateStatus.FAILED),
                last);
        when(bidRepository.findTopBiddersExcluding(anyLong(), anyCollection(), anyInt()))
                .thenReturn(List.of(new Rank(66L, 10_000L), new Rank(77L, 9_000L)));
        givenBidderReferences();
        givenSaveAssignsIds();

        dealCandidateService.fail(ITEM_ID, 105L, SELLER_ID);

        verify(dealCandidateRepository).saveAll(candidatesCaptor.capture());
        assertThat(candidatesCaptor.getValue())
                .extracting(DealCandidate::getCandidateRank)
                .containsExactly(6, 7);

        verify(domainEventPublisher).publish(eventCaptor.capture());
        DealRightAssigned published = (DealRightAssigned) eventCaptor.getValue();
        assertThat(published.candidateRank()).isEqualTo(6);
        assertThat(published.bidderUserId()).isEqualTo(66L);
        assertThat(published.bidAmount()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("보충 조회는 이미 후보인 입찰자를 제외한다")
    void failExcludesExistingCandidatesWhenToppingUp() {

        givenLockedItem();
        DealCandidate only = candidate(101L, 1, 15_000L, DealCandidateStatus.WAITING);
        givenCandidates(only);
        when(bidRepository.findTopBiddersExcluding(anyLong(), anyCollection(), anyInt()))
                .thenReturn(List.of());

        dealCandidateService.fail(ITEM_ID, 101L, SELLER_ID);

        verify(bidRepository).findTopBiddersExcluding(ITEM_ID, List.of(only.getBidder().getUserId()), 5);
    }

    @Test
    @DisplayName("보충할 입찰자가 남지 않으면 후보를 만들지 않고 이벤트도 발행하지 않는다")
    void failPublishesNothingWhenNoBidderLeft() {

        givenLockedItem();
        givenCandidates(candidate(101L, 1, 15_000L, DealCandidateStatus.WAITING));
        when(bidRepository.findTopBiddersExcluding(anyLong(), anyCollection(), anyInt()))
                .thenReturn(List.of());

        dealCandidateService.fail(ITEM_ID, 101L, SELLER_ID);

        verify(dealCandidateRepository, never()).saveAll(any());
        verify(domainEventPublisher, never()).publish(any());
    }

    /** 최종 낙찰자가 정해지는 시점이라 WinnerDecided는 여기서만, 물품당 한 번 나간다. */
    @Test
    @DisplayName("거래 성사를 확정하면 COMPLETED가 되고 WinnerDecided를 발행한다")
    void completePublishesWinnerDecided() {

        givenLockedItem();
        DealCandidate first = candidate(101L, 1, 15_000L, DealCandidateStatus.WAITING);
        givenCandidates(first, candidate(102L, 2, 13_000L, DealCandidateStatus.WAITING));

        dealCandidateService.complete(ITEM_ID, 101L, SELLER_ID);

        assertThat(first.getStatus()).isEqualTo(DealCandidateStatus.COMPLETED);
        assertThat(first.getCompletedAt()).isNotNull();

        verify(domainEventPublisher).publish(eventCaptor.capture());
        WinnerDecided published = (WinnerDecided) eventCaptor.getValue();
        assertThat(published.type()).isEqualTo(EventType.WINNER_DECIDED);
        assertThat(published.roomId()).isEqualTo(ROOM_ID);
        assertThat(published.itemId()).isEqualTo(ITEM_ID);
        assertThat(published.winnerUserId()).isEqualTo(first.getBidder().getUserId());
        assertThat(published.winningPrice()).isEqualTo(15_000L);
    }

    @Test
    @DisplayName("판매자가 아니면 NOT_DEAL_OWNER 예외가 발생한다")
    void failRejectsNonOwner() {

        givenLockedItem();

        assertThatThrownBy(() -> dealCandidateService.fail(ITEM_ID, 101L, OTHER_USER_ID))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorType", DealErrorType.NOT_DEAL_OWNER);
    }

    @Test
    @DisplayName("낙찰이 확정되지 않은 물품이면 ITEM_NOT_SOLD 예외가 발생한다")
    void failRejectsItemNotSold() {

        givenLockedItem(AuctionItemStatus.IN_PROGRESS);

        assertThatThrownBy(() -> dealCandidateService.fail(ITEM_ID, 101L, SELLER_ID))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorType", DealErrorType.ITEM_NOT_SOLD);
    }

    @Test
    @DisplayName("다른 물품의 후보 ID를 넘기면 DEAL_CANDIDATE_NOT_FOUND 예외가 발생한다")
    void failRejectsUnknownCandidate() {

        givenLockedItem();
        givenCandidates(candidate(101L, 1, 15_000L, DealCandidateStatus.WAITING));

        assertThatThrownBy(() -> dealCandidateService.fail(ITEM_ID, 999L, SELLER_ID))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorType", DealErrorType.DEAL_CANDIDATE_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 처리된 후보를 다시 실패시키면 DEAL_CANDIDATE_ALREADY_RESOLVED 예외가 발생한다")
    void failRejectsAlreadyResolvedCandidate() {

        givenLockedItem();
        givenCandidates(candidate(101L, 1, 15_000L, DealCandidateStatus.FAILED),
                candidate(102L, 2, 13_000L, DealCandidateStatus.WAITING));

        assertThatThrownBy(() -> dealCandidateService.fail(ITEM_ID, 101L, SELLER_ID))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorType", DealErrorType.DEAL_CANDIDATE_ALREADY_RESOLVED);
    }

    @Test
    @DisplayName("상위 순위가 대기 중이면 DEAL_CANDIDATE_NOT_ACTIVE 예외가 발생한다")
    void failRejectsCandidateThatIsNotCurrentWinner() {

        givenLockedItem();
        givenCandidates(candidate(101L, 1, 15_000L, DealCandidateStatus.WAITING),
                candidate(102L, 2, 13_000L, DealCandidateStatus.WAITING));

        assertThatThrownBy(() -> dealCandidateService.fail(ITEM_ID, 102L, SELLER_ID))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorType", DealErrorType.DEAL_CANDIDATE_NOT_ACTIVE);
    }

    @Test
    @DisplayName("이미 성사된 거래가 있으면 DEAL_ALREADY_COMPLETED 예외가 발생한다")
    void failRejectsWhenDealAlreadyCompleted() {

        givenLockedItem();
        givenCandidates(candidate(101L, 1, 15_000L, DealCandidateStatus.COMPLETED),
                candidate(102L, 2, 13_000L, DealCandidateStatus.WAITING));

        assertThatThrownBy(() -> dealCandidateService.fail(ITEM_ID, 102L, SELLER_ID))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorType", DealErrorType.DEAL_ALREADY_COMPLETED);
    }
}
