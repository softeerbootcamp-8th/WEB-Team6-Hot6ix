package com.hot6ix.upbid.domain.deal.service;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
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
import com.hot6ix.upbid.global.event.payload.DealRightAssigned;
import com.hot6ix.upbid.global.event.payload.ItemEnded;
import com.hot6ix.upbid.global.event.payload.WinnerDecided;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.response.PageResponse;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DealCandidateService {

    /** 화면이 5명씩 끊어 보여준다. 클라이언트가 정하게 두면 한 번에 전부 긁어갈 수 있다. */
    private static final int CANDIDATE_PAGE_SIZE = 5;

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
     * 물품의 낙찰 후보를 한 페이지 조회한다. 판매자와 입찰자가 같은 endpoint를 쓰고 역할은
     * 여기서 판정한다. 둘 다 아니면 남의 거래이므로 막는다.
     *
     * <p>연락처는 판매자가 볼 때, 거래 상대인 후보만 내린다. 구매자에게는 한 건도 주지 않는다.
     *
     * @throws ApplicationException 물품이 없거나(4001) 이 물품의 판매자도 후보도 아닐 때(6001)
     */
    public DealCandidateListResponseDto getCandidates(
            Long auctionItemId, int page, Long loginUserId) {

        AuctionItem auctionItem = auctionItemRepository.findWithSeller(auctionItemId)
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND));

        boolean seller = auctionItem.getAuctionRoom().getSellerProfile().getUser()
                .getUserId().equals(loginUserId);
        DealCandidate mine = seller ? null
                : dealCandidateRepository.findByBidder(auctionItemId, loginUserId)
                        .orElseThrow(() -> new ApplicationException(DealErrorType.NOT_DEAL_VIEWER));

        Long currentWinnerId = findCurrentWinnerId(auctionItemId);

        Page<DealCandidateResponseDto> candidates = dealCandidateRepository
                .findCandidates(auctionItemId, PageRequest.of(page, CANDIDATE_PAGE_SIZE))
                .map(candidate -> toResponse(candidate, currentWinnerId, seller, loginUserId));

        return new DealCandidateListResponseDto(
                seller ? DealRole.SELLER : DealRole.BUYER,
                seller ? null : mine.getCandidateRank(),
                PageResponse.of(candidates));
    }

    /**
     * 지금 낙찰 권한을 가진 후보의 ID. 없으면 {@code null}이다.
     *
     * <p>거래가 성사된 뒤에도 하위 순위는 {@code WAITING}으로 남는다. 그 상태에서
     * {@code findCurrentWinner}는 최저 순위 대기자를 돌려주므로, 끝난 거래에 진행 중인 후보가
     * 있는 것처럼 보인다. 성사 여부를 먼저 확인해야 하는 이유다.
     */
    private Long findCurrentWinnerId(Long auctionItemId) {

        if (dealCandidateRepository.existsCompletedCandidate(auctionItemId)) {
            return null;
        }
        return dealCandidateRepository.findCurrentWinner(auctionItemId)
                .map(DealCandidate::getDealCandidateId)
                .orElse(null);
    }

    /** 연락처는 거래 상대에게만 의미가 있다 — 지금 거래 중이거나 이미 성사된 후보다. */
    private DealCandidateResponseDto toResponse(
            DealCandidate candidate, Long currentWinnerId, boolean seller, Long loginUserId) {

        DealStatus dealStatus = toDealStatus(candidate, currentWinnerId);
        boolean contactVisible = seller
                && (dealStatus == DealStatus.IN_PROGRESS || dealStatus == DealStatus.COMPLETED);

        return DealCandidateResponseDto.of(candidate, dealStatus, contactVisible,
                candidate.getBidder().getUserId().equals(loginUserId));
    }

    private DealStatus toDealStatus(DealCandidate candidate, Long currentWinnerId) {
        return switch (candidate.getStatus()) {
            case COMPLETED -> DealStatus.COMPLETED;
            case FAILED -> DealStatus.FAILED;
            case WAITING -> candidate.getDealCandidateId().equals(currentWinnerId)
                    ? DealStatus.IN_PROGRESS : DealStatus.WAITING;
        };
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
