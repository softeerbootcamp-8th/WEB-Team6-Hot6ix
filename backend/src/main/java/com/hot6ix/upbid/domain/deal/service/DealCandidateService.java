package com.hot6ix.upbid.domain.deal.service;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.deal.entity.DealCandidate;
import com.hot6ix.upbid.domain.deal.entity.DealCandidateStatus;
import com.hot6ix.upbid.domain.deal.exception.DealErrorType;
import com.hot6ix.upbid.domain.deal.repository.DealCandidateRepository;
import com.hot6ix.upbid.global.event.payload.DealRightAssigned;
import com.hot6ix.upbid.global.event.payload.ItemEnded;
import com.hot6ix.upbid.global.event.payload.WinnerDecided;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import com.hot6ix.upbid.global.exception.ApplicationException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DealCandidateService {

    private final AuctionItemRepository auctionItemRepository;
    private final DealCandidateRepository dealCandidateRepository;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 물품의 입찰 이력을 후보로 옮기고 1순위에게 낙찰 권한을 준다. 같은 이벤트를 두 번 받아도
     * 후보는 한 번만 생긴다. 낙찰자는 {@code ItemEnded}가 아니라 {@code bids}에서 다시 뽑는다 —
     * DB가 원본이다.
     * {@code REQUIRES_NEW}인 이유는 {@code AFTER_COMMIT} 리스너에서 호출되기 때문이다.
     * 기본 전파로는 완료 처리 중인 마감 트랜잭션에 참여해 커밋되지 않을 수 있다.
     *
     * <p><b>{@code READ_COMMITTED}인 이유는 {@code INSERT ... SELECT} 때문이다.</b>
     * REPEATABLE READ에서는 InnoDB가 읽는 쪽 테이블에 공유 넥스트키 락을 걸어,
     * {@code bids}와 {@code users}의 행이 <b>입찰자 수에 비례해</b> 커밋까지 잠긴다. 그러면
     * 그 경매와 무관한 회원의 프로필 수정까지 대기한다. READ COMMITTED에서는 SELECT 부분이
     * 일관된 읽기로 처리돼 원천 테이블에 락을 걸지 않는다.
     *
     * <p>대신 statement 실행 중 커밋된 입찰은 후보에 들어오지 않는다. 이 메서드는 마감이
     * 커밋된 뒤에 돌아 물품이 이미 낙찰 상태이므로 새 입찰이 들어올 수 없다.
     * 멱등성은 격리 수준과 무관하다 — 물품 행의 배타 락이 존재 검사와 삽입을 직렬화한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void award(ItemEnded event) {

        auctionItemRepository.findByIdForUpdate(event.itemId())
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND));

        if (dealCandidateRepository.existsCandidate(event.itemId())) {
            return;
        }

        // 삽입 행이 0이면 입찰이 없었다는 뜻이다. 유찰 이벤트는 마감 쪽이 발행한다.
        if (dealCandidateRepository.insertCandidatesFromBids(event.itemId()) == 0) {
            return;
        }

        dealCandidateRepository.findCurrentWinner(event.itemId())
                .ifPresent(winner -> publishDealRight(event.roomId(), event.itemId(), winner));
    }

    /**
     * 거래 실패를 기록하고 낙찰 권한을 다음 후보에게 넘긴다. 남은 후보가 없으면 이벤트 없이
     * 끝나고 물품은 {@code SOLD}로 남는다 — 경매 결과와 거래 결과는 별개다.
     *
     * @throws ApplicationException 물품·판매자·후보 상태 검증 실패 시({@link DealErrorType})
     */
    @Transactional
    public void fail(Long auctionItemId, Long candidateId, Long sellerUserId) {

        AuctionItem auctionItem = lockSoldItemOwnedBy(auctionItemId, sellerUserId);
        DealCandidate target = findCurrentWinner(auctionItemId, candidateId);

        target.fail(LocalDateTime.now());

        dealCandidateRepository.findNextWaitingCandidate(auctionItemId, target.getCandidateRank())
                .ifPresent(next -> publishDealRight(
                        auctionItem.getAuctionRoom().getAuctionRoomId(), auctionItemId, next));
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
        DealCandidate winner = findCurrentWinner(auctionItemId, candidateId);

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

    /**
     * 요청받은 후보가 지금 낙찰 권한을 가진 후보인지 확인한다. 후보 전체를 읽지 않고 판정에
     * 필요한 것만 조회한다 — 후보 수가 입찰자 수만큼 늘어날 수 있기 때문이다.
     */
    private DealCandidate findCurrentWinner(Long auctionItemId, Long candidateId) {

        if (dealCandidateRepository.existsCompletedCandidate(auctionItemId)) {
            throw new ApplicationException(DealErrorType.DEAL_ALREADY_COMPLETED);
        }

        DealCandidate target = dealCandidateRepository
                .findCandidate(auctionItemId, candidateId)
                .orElseThrow(() -> new ApplicationException(DealErrorType.DEAL_CANDIDATE_NOT_FOUND));

        if (target.getStatus() != DealCandidateStatus.WAITING) {
            throw new ApplicationException(DealErrorType.DEAL_CANDIDATE_ALREADY_RESOLVED);
        }
        if (dealCandidateRepository.existsWaitingCandidateBefore(
                auctionItemId, target.getCandidateRank())) {
            throw new ApplicationException(DealErrorType.DEAL_CANDIDATE_NOT_ACTIVE);
        }
        return target;
    }
}
