package com.hot6ix.upbid.domain.deal.service;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.exception.AuctionItemErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.bid.dto.BidderRankProjection;
import com.hot6ix.upbid.domain.bid.repository.BidRepository;
import com.hot6ix.upbid.domain.deal.entity.DealCandidate;
import com.hot6ix.upbid.domain.deal.repository.DealCandidateRepository;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.event.payload.ItemEnded;
import com.hot6ix.upbid.global.event.payload.WinnerDecided;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import com.hot6ix.upbid.global.exception.ApplicationException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DealCandidateService {

    /**
     * 남길 낙찰 후보 수. 1순위가 낙찰자이고 나머지는 거래가 깨졌을 때 넘길 차순위다.
     * 경매방별로 달리 둘 필요가 생기면 그때 설정으로 옮긴다.
     */
    private static final int MAX_CANDIDATE_COUNT = 5;

    private final AuctionItemRepository auctionItemRepository;
    private final DealCandidateRepository dealCandidateRepository;
    private final BidRepository bidRepository;
    private final UserRepository userRepository;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 마감된 물품의 상위 입찰자를 순위대로 낙찰 후보로 남기고, 1순위를 낙찰자로 발행한다.
     * 같은 마감 이벤트를 두 번 받아도 후보는 한 번만 생긴다.
     *
     * <p>{@code REQUIRES_NEW}인 이유는 이 메서드가 {@code AFTER_COMMIT} 리스너에서 호출되기
     * 때문이다. 그 지점은 마감 트랜잭션이 완료 처리 중이어서, 기본 {@code REQUIRED}로는
     * 커밋되지 않는 트랜잭션에 참여할 수 있다.
     *
     * <p>낙찰자를 {@code ItemEnded}의 낙찰자 정보로 정하지 않고 {@code bids}에서 다시 뽑는다.
     * DB가 경매 상태의 원본이고, 2순위 이하는 어차피 재조회가 필요하다.
     *
     * @param event 마감된 물품의 이벤트
     * @throws ApplicationException 물품이 없을 때(AUCTION_ITEM_NOT_FOUND)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void award(ItemEnded event) {

        AuctionItem auctionItem = auctionItemRepository.findByIdForUpdate(event.itemId())
                .orElseThrow(() -> new ApplicationException(AuctionItemErrorType.AUCTION_ITEM_NOT_FOUND));

        if (dealCandidateRepository.existsByAuctionItem_AuctionItemId(event.itemId())) {
            return;
        }

        List<BidderRankProjection> topBidders =
                bidRepository.findTopBidders(event.itemId(), MAX_CANDIDATE_COUNT);

        // 입찰이 없으면 유찰이다. ItemPassed는 마감 쪽이 발행하므로 여기서는 아무것도 남기지 않는다.
        if (topBidders.isEmpty()) {
            return;
        }

        dealCandidateRepository.saveAll(toCandidates(auctionItem, topBidders));

        BidderRankProjection winner = topBidders.getFirst();
        domainEventPublisher.publish(WinnerDecided.of(event.roomId(), event.itemId(),
                winner.getBidderUserId(), winner.getAmount(), LocalDateTime.now()));
    }

    /**
     * 조회 순서를 그대로 1부터 시작하는 순위로 매긴다. 입찰자는 {@code getReferenceById}로
     * 프록시만 잡아 FK만 채우고 실제 조회는 하지 않는다.
     */
    private List<DealCandidate> toCandidates(AuctionItem auctionItem, List<BidderRankProjection> topBidders) {

        List<DealCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < topBidders.size(); index++) {
            BidderRankProjection bidder = topBidders.get(index);
            candidates.add(DealCandidate.builder()
                    .auctionItem(auctionItem)
                    .bidder(userRepository.getReferenceById(bidder.getBidderUserId()))
                    .candidateRank(index + 1)
                    .bidAmount(bidder.getAmount())
                    .build());
        }
        return candidates;
    }
}
