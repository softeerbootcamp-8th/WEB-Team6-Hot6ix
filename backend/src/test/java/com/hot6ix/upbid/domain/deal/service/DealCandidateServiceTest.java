package com.hot6ix.upbid.domain.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.deal.entity.DealCandidate;
import com.hot6ix.upbid.domain.deal.entity.DealCandidateStatus;
import com.hot6ix.upbid.domain.deal.exception.DealErrorType;
import com.hot6ix.upbid.domain.deal.repository.DealCandidateRepository;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.global.event.DomainEvent;
import com.hot6ix.upbid.global.event.EventType;
import com.hot6ix.upbid.global.event.payload.DealRightAssigned;
import com.hot6ix.upbid.global.event.payload.ItemEnded;
import com.hot6ix.upbid.global.event.payload.WinnerDecided;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import com.hot6ix.upbid.global.exception.ApplicationException;
import java.time.LocalDateTime;
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
    private DomainEventPublisher domainEventPublisher;

    @InjectMocks
    private DealCandidateService dealCandidateService;

    @Captor
    private ArgumentCaptor<DomainEvent> eventCaptor;

    /** 판매자 검증이 연관 관계를 따라가므로 모두 채운다. ID는 빌더로 못 넣어 리플렉션을 쓴다. */
    private AuctionItem soldItem(AuctionItemStatus status) {

        SellerProfile sellerProfile = SellerProfile.builder()
                .user(user(SELLER_ID))
                .storeName("승민상점")
                .build();
        AuctionRoom auctionRoom = AuctionRoom.builder()
                .bidIncrement(1_000L)
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

    private ItemEnded itemEnded() {
        return ItemEnded.of(ROOM_ID, ITEM_ID, "포토카드", 15_000L, "원기", OCCURRED_AT);
    }

    private void givenLockedItem() {
        givenLockedItem(AuctionItemStatus.SOLD);
    }

    private void givenLockedItem(AuctionItemStatus status) {
        when(auctionItemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(soldItem(status)));
    }

    private void givenCurrentWinner(DealCandidate candidate) {
        when(dealCandidateRepository.findCurrentWinner(ITEM_ID))
                .thenReturn(Optional.ofNullable(candidate));
    }

    private void givenTarget(DealCandidate candidate) {
        when(dealCandidateRepository.findCandidate(ITEM_ID, candidate.getDealCandidateId()))
                .thenReturn(Optional.of(candidate));
    }

    private void givenNoEarlierWaiting() {
        when(dealCandidateRepository.existsWaitingCandidateBefore(anyLong(), any())).thenReturn(false);
    }

    @Test
    @DisplayName("입찰 이력을 후보로 옮기고 1순위에게 DealRightAssigned를 발행한다")
    void awardPublishesDealRightForFirstRank() {

        givenLockedItem();
        when(dealCandidateRepository.insertCandidatesFromBids(ITEM_ID)).thenReturn(3);
        givenCurrentWinner(candidate(101L, 1, 15_000L, DealCandidateStatus.WAITING));

        dealCandidateService.award(itemEnded());

        verify(domainEventPublisher).publish(eventCaptor.capture());
        DealRightAssigned published = (DealRightAssigned) eventCaptor.getValue();
        assertThat(published.type()).isEqualTo(EventType.DEAL_RIGHT_ASSIGNED);
        assertThat(published.roomId()).isEqualTo(ROOM_ID);
        assertThat(published.itemId()).isEqualTo(ITEM_ID);
        assertThat(published.dealCandidateId()).isEqualTo(101L);
        assertThat(published.candidateRank()).isEqualTo(1);
        assertThat(published.bidAmount()).isEqualTo(15_000L);
    }

    @Test
    @DisplayName("후보가 이미 있으면 다시 만들지 않고 이벤트도 발행하지 않는다")
    void awardIsIdempotent() {

        givenLockedItem();
        when(dealCandidateRepository.existsCandidate(ITEM_ID)).thenReturn(true);

        dealCandidateService.award(itemEnded());

        verify(dealCandidateRepository, never()).insertCandidatesFromBids(anyLong());
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("삽입된 후보가 없으면 입찰이 없었다는 뜻이라 이벤트를 발행하지 않는다")
    void awardDoesNothingWhenNoBid() {

        givenLockedItem();
        when(dealCandidateRepository.insertCandidatesFromBids(ITEM_ID)).thenReturn(0);

        dealCandidateService.award(itemEnded());

        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("물품이 없으면 AUCTION_ITEM_NOT_FOUND 예외가 발생한다")
    void awardThrowsWhenItemNotFound() {

        when(auctionItemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dealCandidateService.award(itemEnded()))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorType", AuctionErrorType.AUCTION_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("후보를 만들기 전에 물품 행에 락을 건다")
    void awardLocksItemBeforeCheckingCandidates() {

        givenLockedItem();
        when(dealCandidateRepository.existsCandidate(ITEM_ID)).thenReturn(true);

        dealCandidateService.award(itemEnded());

        // 락 없이 존재 검사를 하면 동시 요청이 둘 다 "없음"을 보고 각각 후보를 만든다.
        InOrder inOrder = inOrder(auctionItemRepository, dealCandidateRepository);
        inOrder.verify(auctionItemRepository).findByIdForUpdate(ITEM_ID);
        inOrder.verify(dealCandidateRepository).existsCandidate(ITEM_ID);
    }

    @Test
    @DisplayName("거래 실패를 기록하면 차순위 후보에게 DealRightAssigned를 발행한다")
    void failTransfersToNextCandidate() {

        givenLockedItem();
        DealCandidate first = candidate(101L, 1, 15_000L, DealCandidateStatus.WAITING);
        DealCandidate second = candidate(102L, 2, 13_000L, DealCandidateStatus.WAITING);
        givenTarget(first);
        givenNoEarlierWaiting();
        when(dealCandidateRepository.findNextWaitingCandidate(ITEM_ID, 1))
                .thenReturn(Optional.of(second));

        dealCandidateService.fail(ITEM_ID, 101L, SELLER_ID);

        assertThat(first.getStatus()).isEqualTo(DealCandidateStatus.FAILED);
        assertThat(first.getFailedAt()).isNotNull();

        verify(domainEventPublisher).publish(eventCaptor.capture());
        DealRightAssigned published = (DealRightAssigned) eventCaptor.getValue();
        assertThat(published.dealCandidateId()).isEqualTo(102L);
        assertThat(published.candidateRank()).isEqualTo(2);
        assertThat(published.bidderUserId()).isEqualTo(second.getBidder().getUserId());
        assertThat(published.bidAmount()).isEqualTo(13_000L);
    }

    /** 후보를 보충하지 않으므로, 마지막 순위가 실패하면 아무 일도 일어나지 않는다. */
    @Test
    @DisplayName("다음 차례가 없으면 이벤트를 발행하지 않는다")
    void failPublishesNothingWhenNoNextCandidate() {

        givenLockedItem();
        DealCandidate last = candidate(105L, 5, 11_000L, DealCandidateStatus.WAITING);
        givenTarget(last);
        givenNoEarlierWaiting();
        when(dealCandidateRepository.findNextWaitingCandidate(ITEM_ID, 5))
                .thenReturn(Optional.empty());

        dealCandidateService.fail(ITEM_ID, 105L, SELLER_ID);

        assertThat(last.getStatus()).isEqualTo(DealCandidateStatus.FAILED);
        verify(domainEventPublisher, never()).publish(any());
    }

    /** 최종 낙찰자가 정해지는 시점이라 WinnerDecided는 여기서만, 물품당 한 번 나간다. */
    @Test
    @DisplayName("거래 성사를 확정하면 COMPLETED가 되고 WinnerDecided를 발행한다")
    void completePublishesWinnerDecided() {

        givenLockedItem();
        DealCandidate first = candidate(101L, 1, 15_000L, DealCandidateStatus.WAITING);
        givenTarget(first);
        givenNoEarlierWaiting();

        dealCandidateService.complete(ITEM_ID, 101L, SELLER_ID);

        assertThat(first.getStatus()).isEqualTo(DealCandidateStatus.COMPLETED);
        assertThat(first.getCompletedAt()).isNotNull();

        verify(domainEventPublisher).publish(eventCaptor.capture());
        WinnerDecided published = (WinnerDecided) eventCaptor.getValue();
        assertThat(published.type()).isEqualTo(EventType.WINNER_DECIDED);
        assertThat(published.roomId()).isEqualTo(ROOM_ID);
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
        when(dealCandidateRepository.findCandidate(ITEM_ID, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dealCandidateService.fail(ITEM_ID, 999L, SELLER_ID))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorType", DealErrorType.DEAL_CANDIDATE_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 처리된 후보를 다시 실패시키면 DEAL_CANDIDATE_ALREADY_RESOLVED 예외가 발생한다")
    void failRejectsAlreadyResolvedCandidate() {

        givenLockedItem();
        givenTarget(candidate(101L, 1, 15_000L, DealCandidateStatus.FAILED));

        assertThatThrownBy(() -> dealCandidateService.fail(ITEM_ID, 101L, SELLER_ID))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorType", DealErrorType.DEAL_CANDIDATE_ALREADY_RESOLVED);
    }

    @Test
    @DisplayName("상위 순위가 대기 중이면 DEAL_CANDIDATE_NOT_ACTIVE 예외가 발생한다")
    void failRejectsCandidateThatIsNotCurrentWinner() {

        givenLockedItem();
        givenTarget(candidate(102L, 2, 13_000L, DealCandidateStatus.WAITING));
        when(dealCandidateRepository.existsWaitingCandidateBefore(ITEM_ID, 2)).thenReturn(true);

        assertThatThrownBy(() -> dealCandidateService.fail(ITEM_ID, 102L, SELLER_ID))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorType", DealErrorType.DEAL_CANDIDATE_NOT_ACTIVE);
    }

    @Test
    @DisplayName("이미 성사된 거래가 있으면 DEAL_ALREADY_COMPLETED 예외가 발생한다")
    void failRejectsWhenDealAlreadyCompleted() {

        givenLockedItem();
        when(dealCandidateRepository.existsCompletedCandidate(ITEM_ID)).thenReturn(true);

        assertThatThrownBy(() -> dealCandidateService.fail(ITEM_ID, 102L, SELLER_ID))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorType", DealErrorType.DEAL_ALREADY_COMPLETED);
    }
}
