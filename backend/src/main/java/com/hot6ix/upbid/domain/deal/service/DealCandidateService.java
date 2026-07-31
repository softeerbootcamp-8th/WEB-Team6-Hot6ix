package com.hot6ix.upbid.domain.deal.service;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.bid.dto.BidderRankProjection;
import com.hot6ix.upbid.domain.bid.repository.BidRepository;
import com.hot6ix.upbid.domain.deal.entity.DealCandidate;
import com.hot6ix.upbid.domain.deal.entity.DealCandidateStatus;
import com.hot6ix.upbid.domain.deal.exception.DealErrorType;
import com.hot6ix.upbid.domain.deal.repository.DealCandidateRepository;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.event.payload.DealRightAssigned;
import com.hot6ix.upbid.global.event.payload.ItemEnded;
import com.hot6ix.upbid.global.event.payload.WinnerDecided;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import com.hot6ix.upbid.global.exception.ApplicationException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DealCandidateService {

    /** 상한이 아니라 한 번에 만들 후보 수다. 다 실패하면 다음 묶음을 보충한다. */
    private static final int CANDIDATE_BATCH_SIZE = 5;

    private final AuctionItemRepository auctionItemRepository;
    private final DealCandidateRepository dealCandidateRepository;
    private final BidRepository bidRepository;
    private final UserRepository userRepository;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 상위 입찰자를 순위대로 후보로 남기고 1순위에게 낙찰 권한을 준다. 같은 이벤트를 두 번
     * 받아도 후보는 한 번만 생긴다. 낙찰자는 {@code ItemEnded}가 아니라 {@code bids}에서
     * 다시 뽑는다 — DB가 원본이고 2순위 이하는 어차피 재조회가 필요하다.
     * {@code REQUIRES_NEW}인 이유는 {@code AFTER_COMMIT} 리스너에서 호출되기 때문이다.
     * 기본 전파로는 완료 처리 중인 마감 트랜잭션에 참여해 커밋되지 않을 수 있다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void award(ItemEnded event) {

        AuctionItem auctionItem = auctionItemRepository.findByIdForUpdate(event.itemId())
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND));

        if (dealCandidateRepository.existsByAuctionItem_AuctionItemId(event.itemId())) {
            return;
        }

        List<BidderRankProjection> topBidders =
                bidRepository.findTopBidders(event.itemId(), CANDIDATE_BATCH_SIZE);

        // 입찰이 없으면 유찰이다. ItemPassed는 마감 쪽이 발행하므로 여기서는 아무것도 남기지 않는다.
        if (topBidders.isEmpty()) {
            return;
        }

        List<DealCandidate> saved =
                dealCandidateRepository.saveAll(toCandidates(auctionItem, topBidders, 1));

        publishDealRight(event.roomId(), event.itemId(), saved.getFirst());
    }

    /**
     * 거래 실패를 기록하고 낙찰 권한을 다음 후보에게 넘긴다. 남은 후보가 없으면 보충해서라도
     * 넘기며 입찰자가 소진될 때까지 이어진다. 그때까지도 물품은 {@code SOLD}로 남는다.
     *
     * @throws ApplicationException 물품·판매자·후보 상태 검증 실패 시({@link DealErrorType})
     */
    @Transactional
    public void fail(Long auctionItemId, Long candidateId, Long sellerUserId) {

        AuctionItem auctionItem = lockSoldItemOwnedBy(auctionItemId, sellerUserId);
        List<DealCandidate> candidates = dealCandidateRepository.findAllByAuctionItemId(auctionItemId);
        DealCandidate target = findCurrentWinner(candidates, candidateId);

        target.fail(LocalDateTime.now());

        nextWaiting(candidates, target)
                .or(() -> topUpCandidates(auctionItem, candidates))
                .ifPresent(next -> publishDealRight(
                        auctionItem.getAuctionRoom().getAuctionRoomId(), auctionItemId, next));
    }

    /**
     * 후보가 소진됐을 때 다음 순위를 한 묶음 더 이어 붙인다. 이미 후보인 회원은 제외해
     * 조회하고, 순위는 기존 최대 다음부터 매긴다. {@code fail}이 잡은 락 안에서 실행된다.
     *
     * @return 보충한 묶음의 첫 후보. 남은 입찰자가 없으면 빈 값
     */
    private Optional<DealCandidate> topUpCandidates(AuctionItem auctionItem, List<DealCandidate> exhausted) {

        List<Long> excludedBidderUserIds = exhausted.stream()
                .map(candidate -> candidate.getBidder().getUserId())
                .toList();

        List<BidderRankProjection> nextBidders = bidRepository.findTopBiddersExcluding(
                auctionItem.getAuctionItemId(), excludedBidderUserIds, CANDIDATE_BATCH_SIZE);

        if (nextBidders.isEmpty()) {
            return Optional.empty();
        }

        int nextRank = exhausted.stream()
                .mapToInt(DealCandidate::getCandidateRank)
                .max()
                .orElse(0) + 1;

        return Optional.of(dealCandidateRepository
                .saveAll(toCandidates(auctionItem, nextBidders, nextRank))
                .getFirst());
    }

    /**
     * 거래 성사를 확정하고 {@code WinnerDecided}를 발행한다. 최종 낙찰자가 정해지는 시점이라
     * 물품당 한 번만 나간다. 남은 하위 후보는 {@code COMPLETED} 판정에 막히므로 그대로 둔다.
     *
     * @throws ApplicationException {@link #fail}과 같다
     */
    @Transactional
    public void complete(Long auctionItemId, Long candidateId, Long sellerUserId) {

        AuctionItem auctionItem = lockSoldItemOwnedBy(auctionItemId, sellerUserId);
        List<DealCandidate> candidates = dealCandidateRepository.findAllByAuctionItemId(auctionItemId);
        DealCandidate winner = findCurrentWinner(candidates, candidateId);

        winner.complete(LocalDateTime.now());

        domainEventPublisher.publish(WinnerDecided.of(
                auctionItem.getAuctionRoom().getAuctionRoomId(), auctionItemId,
                winner.getBidder().getUserId(), winner.getBidAmount(), LocalDateTime.now()));
    }

    /** 마감 직후 1순위에게, 이후 실패할 때마다 차순위에게 발행되므로 여러 번 나간다. */
    private void publishDealRight(Long roomId, Long itemId, DealCandidate candidate) {
        domainEventPublisher.publish(DealRightAssigned.of(roomId, itemId,
                candidate.getDealCandidateId(), candidate.getCandidateRank(),
                candidate.getBidder().getUserId(), candidate.getBidAmount(), LocalDateTime.now()));
    }

    /** 락을 먼저 잡아야 뒤따르는 검증과 상태 변경이 동시 요청에 대해 직렬화된다. */
    private AuctionItem lockSoldItemOwnedBy(Long auctionItemId, Long sellerUserId) {

        AuctionItem auctionItem = auctionItemRepository.findByIdForUpdate(auctionItemId)
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND));

        Long ownerUserId = auctionItem.getAuctionRoom().getSellerProfile().getUser().getUserId();
        if (!ownerUserId.equals(sellerUserId)) {
            throw new ApplicationException(DealErrorType.NOT_DEAL_OWNER);
        }
        if (auctionItem.getStatus() != AuctionItemStatus.SOLD) {
            throw new ApplicationException(DealErrorType.ITEM_NOT_SOLD);
        }
        return auctionItem;
    }

    /** 순위가 더 높은 {@code WAITING} 후보가 남아 있으면 아직 그 후보의 차례다. */
    private DealCandidate findCurrentWinner(List<DealCandidate> candidates, Long candidateId) {

        if (candidates.stream().anyMatch(candidate -> candidate.getStatus() == DealCandidateStatus.COMPLETED)) {
            throw new ApplicationException(DealErrorType.DEAL_ALREADY_COMPLETED);
        }

        DealCandidate target = candidates.stream()
                .filter(candidate -> candidate.getDealCandidateId().equals(candidateId))
                .findFirst()
                .orElseThrow(() -> new ApplicationException(DealErrorType.DEAL_CANDIDATE_NOT_FOUND));

        if (target.getStatus() != DealCandidateStatus.WAITING) {
            throw new ApplicationException(DealErrorType.DEAL_CANDIDATE_ALREADY_RESOLVED);
        }
        if (hasWaitingAbove(candidates, target)) {
            throw new ApplicationException(DealErrorType.DEAL_CANDIDATE_NOT_ACTIVE);
        }
        return target;
    }

    private boolean hasWaitingAbove(List<DealCandidate> candidates, DealCandidate target) {
        return candidates.stream()
                .anyMatch(candidate -> candidate.getStatus() == DealCandidateStatus.WAITING
                        && candidate.getCandidateRank() < target.getCandidateRank());
    }

    private Optional<DealCandidate> nextWaiting(List<DealCandidate> candidates, DealCandidate failed) {
        return candidates.stream()
                .filter(candidate -> candidate.getStatus() == DealCandidateStatus.WAITING
                        && candidate.getCandidateRank() > failed.getCandidateRank())
                .findFirst();
    }

    /** 입찰자는 프록시만 잡아 FK만 채운다. 실제 조회는 하지 않는다. */
    private List<DealCandidate> toCandidates(AuctionItem auctionItem,
                                             List<BidderRankProjection> topBidders, int startRank) {

        List<DealCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < topBidders.size(); index++) {
            BidderRankProjection bidder = topBidders.get(index);
            candidates.add(DealCandidate.builder()
                    .auctionItem(auctionItem)
                    .bidder(userRepository.getReferenceById(bidder.getBidderUserId()))
                    .candidateRank(startRank + index)
                    .bidAmount(bidder.getAmount())
                    .build());
        }
        return candidates;
    }
}
