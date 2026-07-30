package com.hot6ix.upbid.domain.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.exception.AuctionItemErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.bid.dto.BidderRankProjection;
import com.hot6ix.upbid.domain.bid.repository.BidRepository;
import com.hot6ix.upbid.domain.deal.entity.DealCandidate;
import com.hot6ix.upbid.domain.deal.repository.DealCandidateRepository;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.event.DomainEvent;
import com.hot6ix.upbid.global.event.EventType;
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

@ExtendWith(MockitoExtension.class)
class DealCandidateServiceTest {

    private static final Long ROOM_ID = 1L;
    private static final Long ITEM_ID = 2L;
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

    /**
     * native 쿼리 projection이라 구현체가 없어 테스트용으로 만든다. record 컴포넌트 이름이
     * 그대로 접근자가 되므로 인터페이스를 만족한다.
     */
    private record Rank(Long getBidderUserId, Long getAmount) implements BidderRankProjection {
    }

    private ItemEnded itemEnded() {
        return ItemEnded.of(ROOM_ID, ITEM_ID, "포토카드", 15_000L, "원기", OCCURRED_AT);
    }

    private AuctionItem soldItem() {
        return AuctionItem.builder()
                .startingPrice(10_000L)
                .bidIncrement(1_000L)
                .status(AuctionItemStatus.SOLD)
                .build();
    }

    private void givenLockedItem() {
        when(auctionItemRepository.findByIdForUpdate(ITEM_ID)).thenReturn(Optional.of(soldItem()));
    }

    private void givenTopBidders(BidderRankProjection... bidders) {
        when(bidRepository.findTopBidders(anyLong(), anyInt())).thenReturn(List.of(bidders));
        when(userRepository.getReferenceById(anyLong())).thenReturn(User.builder()
                .email("bidder@hot6ix.com")
                .password("password")
                .nickname("입찰자")
                .phoneNumber("010-1234-5678")
                .build());
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

    @Test
    @DisplayName("1순위 입찰자와 그 금액으로 WinnerDecided를 발행한다")
    void awardPublishesWinnerDecidedForFirstRank() {

        givenLockedItem();
        givenTopBidders(new Rank(11L, 15_000L), new Rank(22L, 13_000L));

        dealCandidateService.award(itemEnded());

        verify(domainEventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(WinnerDecided.class);

        WinnerDecided published = (WinnerDecided) eventCaptor.getValue();
        assertThat(published.type()).isEqualTo(EventType.WINNER_DECIDED);
        assertThat(published.roomId()).isEqualTo(ROOM_ID);
        assertThat(published.itemId()).isEqualTo(ITEM_ID);
        assertThat(published.winnerUserId()).isEqualTo(11L);
        assertThat(published.winningPrice()).isEqualTo(15_000L);
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
}
