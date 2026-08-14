package com.hot6ix.upbid.domain.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.deal.dto.response.DealCandidateListResponseDto;
import com.hot6ix.upbid.domain.deal.dto.response.DealCandidateResponseDto;
import com.hot6ix.upbid.domain.deal.entity.DealCandidate;
import com.hot6ix.upbid.domain.deal.entity.DealCandidateStatus;
import com.hot6ix.upbid.domain.deal.entity.DealRole;
import com.hot6ix.upbid.domain.deal.entity.DealStatus;
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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    /**
     * mock 이 아니라 진짜를 쓴다. mock 이면 {@code recordCandidateInsert} 가 supplier 를 안
     * 부르고 null 을 돌려줘서, 계측을 붙였다는 이유만으로 낙찰 로직이 통째로 안 돈다.
     */
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Spy
    private DealMetrics dealMetrics = new DealMetrics(meterRegistry);

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
    @DisplayName("후보 삽입 쿼리만 따로 재는 타이머에 실행이 한 건 남는다")
    void awardRecordsCandidateInsertTimer() {

        givenLockedItem();
        when(dealCandidateRepository.insertCandidatesFromBids(ITEM_ID)).thenReturn(3);
        givenCurrentWinner(candidate(101L, 1, 15_000L, DealCandidateStatus.WAITING));

        dealCandidateService.award(itemEnded());

        // 트랜잭션 전체를 재는 upbid.deal.award 와 달리 이 쿼리 하나만 잡혀야 한다.
        assertThat(meterRegistry.timer("upbid.deal.candidate.insert").count()).isEqualTo(1);
    }

    @Test
    @DisplayName("후보가 이미 있으면 삽입 쿼리를 부르지 않으므로 타이머도 안 움직인다")
    void awardDoesNotRecordCandidateInsertWhenAlreadyAwarded() {

        givenLockedItem();
        when(dealCandidateRepository.existsCandidate(ITEM_ID)).thenReturn(true);

        dealCandidateService.award(itemEnded());

        assertThat(meterRegistry.timer("upbid.deal.candidate.insert").count()).isZero();
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

    private void givenItemWithSeller() {
        givenItemStatus(AuctionItemStatus.SOLD);
        when(auctionItemRepository.findSellerUserId(ITEM_ID)).thenReturn(Optional.of(SELLER_ID));
    }

    private void givenItemStatus(AuctionItemStatus status) {
        when(auctionItemRepository.findStatus(ITEM_ID)).thenReturn(Optional.of(status));
    }

    private void givenPage(int page, long total, DealCandidate... candidates) {
        when(dealCandidateRepository.findCandidates(eq(ITEM_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(candidates), PageRequest.of(page, 5), total));
    }

    private void givenNoCompletedCandidate() {
        when(dealCandidateRepository.existsCompletedCandidate(ITEM_ID)).thenReturn(false);
    }

    @Test
    @DisplayName("판매자에게는 거래 상대인 후보의 연락처만 보인다")
    void getCandidatesRevealsContactOnlyToSellerForCurrentAndCompleted() {

        givenItemWithSeller();
        givenNoCompletedCandidate();
        DealCandidate current = candidate(102L, 2, 13_000L, DealCandidateStatus.WAITING);
        givenCurrentWinner(current);
        givenPage(0, 3,
                candidate(101L, 1, 15_000L, DealCandidateStatus.FAILED),
                current,
                candidate(103L, 3, 12_000L, DealCandidateStatus.WAITING));

        DealCandidateListResponseDto response =
                dealCandidateService.getCandidates(ITEM_ID, 0, SELLER_ID);

        assertThat(response.viewerRole()).isEqualTo(DealRole.SELLER);
        assertThat(response.myRank()).isNull();
        assertThat(response.candidates().content())
                .extracting(DealCandidateResponseDto::dealStatus, DealCandidateResponseDto::phoneNumber)
                .containsExactly(
                        tuple(DealStatus.FAILED, null),
                        tuple(DealStatus.IN_PROGRESS, "010-1234-5678"),
                        tuple(DealStatus.WAITING, null));
        assertThat(response.candidates().totalElements()).isEqualTo(3);
    }

    /** 연락처가 새면 개인정보 유출이다. 구매자에게는 한 건도 주지 않는다. */
    @Test
    @DisplayName("구매자에게는 어떤 후보의 연락처도 주지 않는다")
    void getCandidatesHidesEveryContactFromBidder() {

        givenItemWithSeller();
        givenNoCompletedCandidate();
        DealCandidate current = candidate(101L, 1, 15_000L, DealCandidateStatus.WAITING);
        givenCurrentWinner(current);
        DealCandidate mine = candidate(107L, 7, 9_000L, DealCandidateStatus.WAITING);
        when(dealCandidateRepository.findByBidder(ITEM_ID, 107L)).thenReturn(Optional.of(mine));
        givenPage(0, 7, current, candidate(102L, 2, 13_000L, DealCandidateStatus.WAITING));

        DealCandidateListResponseDto response =
                dealCandidateService.getCandidates(ITEM_ID, 0, 107L);

        assertThat(response.viewerRole()).isEqualTo(DealRole.BUYER);
        assertThat(response.candidates().content())
                .extracting(DealCandidateResponseDto::phoneNumber)
                .containsOnlyNulls();
    }

    /** 7위가 1페이지를 봐도 자기 순위를 알아야 화면이 그 페이지로 이동할 수 있다. */
    @Test
    @DisplayName("구매자의 순위는 요청한 페이지 밖에 있어도 내려간다")
    void getCandidatesReturnsMyRankOutsideRequestedPage() {

        givenItemWithSeller();
        givenNoCompletedCandidate();
        givenCurrentWinner(null);
        when(dealCandidateRepository.findByBidder(ITEM_ID, 107L))
                .thenReturn(Optional.of(candidate(107L, 7, 9_000L, DealCandidateStatus.WAITING)));
        givenPage(0, 7, candidate(101L, 1, 15_000L, DealCandidateStatus.FAILED));

        DealCandidateListResponseDto response =
                dealCandidateService.getCandidates(ITEM_ID, 0, 107L);

        assertThat(response.myRank()).isEqualTo(7);
        assertThat(response.candidates().content())
                .extracting(DealCandidateResponseDto::isMe)
                .containsExactly(false);
    }

    @Test
    @DisplayName("목록에 있는 내 행은 isMe로 표시된다")
    void getCandidatesMarksMyRow() {

        givenItemWithSeller();
        givenNoCompletedCandidate();
        DealCandidate mine = candidate(102L, 2, 13_000L, DealCandidateStatus.WAITING);
        givenCurrentWinner(mine);
        when(dealCandidateRepository.findByBidder(ITEM_ID, 102L)).thenReturn(Optional.of(mine));
        givenPage(0, 2, candidate(101L, 1, 15_000L, DealCandidateStatus.FAILED), mine);

        DealCandidateListResponseDto response =
                dealCandidateService.getCandidates(ITEM_ID, 0, 102L);

        assertThat(response.candidates().content())
                .extracting(DealCandidateResponseDto::isMe)
                .containsExactly(false, true);
    }

    /**
     * 성사된 뒤에도 하위 순위는 WAITING으로 남는다. 그 상태에서 최저 순위 대기자를 진행 중으로
     * 칠하면, 끝난 거래에 아직 진행 중인 후보가 있는 것처럼 보인다.
     */
    @Test
    @DisplayName("거래가 성사되면 남은 대기자를 진행 중으로 표시하지 않는다")
    void getCandidatesMarksNobodyInProgressAfterCompletion() {

        givenItemWithSeller();
        when(dealCandidateRepository.existsCompletedCandidate(ITEM_ID)).thenReturn(true);
        givenPage(0, 2,
                candidate(101L, 1, 15_000L, DealCandidateStatus.COMPLETED),
                candidate(102L, 2, 13_000L, DealCandidateStatus.WAITING));

        DealCandidateListResponseDto response =
                dealCandidateService.getCandidates(ITEM_ID, 0, SELLER_ID);

        assertThat(response.candidates().content())
                .extracting(DealCandidateResponseDto::dealStatus)
                .containsExactly(DealStatus.COMPLETED, DealStatus.WAITING);
        // 성사된 거래 상대의 연락처는 계속 보여야 한다.
        assertThat(response.candidates().content().getFirst().phoneNumber())
                .isEqualTo("010-1234-5678");
        verify(dealCandidateRepository, never()).findCurrentWinner(anyLong());
    }

    @Test
    @DisplayName("판매자도 후보도 아니면 조회할 수 없다")
    void getCandidatesRejectsOutsider() {

        givenItemWithSeller();
        when(dealCandidateRepository.findByBidder(ITEM_ID, OTHER_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dealCandidateService.getCandidates(ITEM_ID, 0, OTHER_USER_ID))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorType", DealErrorType.NOT_DEAL_VIEWER);
    }

    /**
     * 마감 검사를 역할 판정보다 먼저 하지 않으면 판매자는 빈 목록(200), 입찰자는 6007을 받아
     * 같은 상황에 응답이 갈린다. 두 역할이 같은 에러를 받는지까지 확인한다.
     */
    @Test
    @DisplayName("아직 마감되지 않은 물품은 역할과 무관하게 조회를 거절한다")
    void getCandidatesRejectsItemNotClosed() {

        givenItemStatus(AuctionItemStatus.IN_PROGRESS);

        assertThatThrownBy(() -> dealCandidateService.getCandidates(ITEM_ID, 0, SELLER_ID))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorType", DealErrorType.ITEM_NOT_SOLD);

        assertThatThrownBy(() -> dealCandidateService.getCandidates(ITEM_ID, 0, OTHER_USER_ID))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorType", DealErrorType.ITEM_NOT_SOLD);

        // 마감 전에는 역할을 따질 것도 없다.
        verify(auctionItemRepository, never()).findSellerUserId(anyLong());
    }

    /** 유찰도 마감이다. "낙찰 후보 없음" 화면이 이 응답으로 그려진다. */
    @Test
    @DisplayName("유찰 물품은 빈 목록으로 조회된다")
    void getCandidatesAllowsUnsoldItem() {

        givenItemStatus(AuctionItemStatus.FAILED);
        when(auctionItemRepository.findSellerUserId(ITEM_ID)).thenReturn(Optional.of(SELLER_ID));
        givenNoCompletedCandidate();
        givenCurrentWinner(null);
        givenPage(0, 0);

        DealCandidateListResponseDto response =
                dealCandidateService.getCandidates(ITEM_ID, 0, SELLER_ID);

        assertThat(response.viewerRole()).isEqualTo(DealRole.SELLER);
        assertThat(response.candidates().content()).isEmpty();
        assertThat(response.candidates().totalElements()).isZero();
    }

    @Test
    @DisplayName("없는 물품의 후보는 조회할 수 없다")
    void getCandidatesRejectsMissingItem() {

        when(auctionItemRepository.findStatus(ITEM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dealCandidateService.getCandidates(ITEM_ID, 0, SELLER_ID))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorType", AuctionErrorType.AUCTION_ITEM_NOT_FOUND);
    }
}
