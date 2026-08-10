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
     * 마감 이벤트를 받아 낙찰 후보를 만든다. {@link #award(Long, Long)}으로 위임한다 — 실제
     * 로직이 쓰는 것은 {@code roomId}·{@code itemId}뿐이고, 이벤트 자체가 필요한 건 아니다.
     *
     * <p>여기서 위임하는 것은 {@link DealAwardRecoveryRunner}가 이 경로를 또 하나의 진입점으로
     * 쓰기 때문이다. 복구가 이벤트를 억지로 조립하면 {@code finalPrice}·{@code winnerNickname}을
     * 다시 읽어야 하고 {@code eventId}·{@code occurredAt}이 거짓이 된다. ID만 넘기면 그럴
     * 필요가 없다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void award(ItemEnded event) {
        award(event.roomId(), event.itemId());
    }

    /**
     * 물품의 입찰 이력을 후보로 옮기고 1순위에게 낙찰 권한을 준다. 같은 물품에 두 번 불러도
     * 후보는 한 번만 생긴다. 낙찰자는 이벤트가 아니라 {@code bids}에서 다시 뽑는다 —
     * DB가 원본이다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void award(Long roomId, Long itemId) {

        auctionItemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND));

        if (dealCandidateRepository.existsCandidate(itemId)) {
            return;
        }

        // 삽입 행이 0이면 입찰이 없었다는 뜻이다. 유찰 이벤트는 마감 쪽이 발행한다.
        if (dealCandidateRepository.insertCandidatesFromBids(itemId) == 0) {
            return;
        }

        dealCandidateRepository.findCurrentWinner(itemId)
                .ifPresent(winner -> publishDealRight(roomId, itemId, winner));
    }

    /**
     * 물품의 낙찰 후보를 한 페이지 조회한다. 판매자와 입찰자가 같은 endpoint를 쓰고 역할은
     * 여기서 판정한다. 둘 다 아니면 남의 거래이므로 막는다.
     *
     * <p>연락처는 판매자가 볼 때, 거래 상대인 후보만 내린다. 구매자에게는 한 건도 주지 않는다.
     *
     * <p>마감 여부를 역할 판정보다 <b>먼저</b> 본다. 순서가 반대면 아직 진행 중인 물품에 대해
     * 판매자는 빈 목록(200)을, 입찰자는 후보가 없어 6007을 받아 같은 상황에 응답이 갈린다.
     *
     * @throws ApplicationException 물품이 없거나(4001), 아직 마감되지 않았거나(6002),
     *                              이 물품의 판매자도 후보도 아닐 때(6007)
     */
    public DealCandidateListResponseDto getCandidates(
            Long auctionItemId, int page, Long loginUserId) {

        requireClosed(auctionItemId);

        Long sellerUserId = auctionItemRepository.findSellerUserId(auctionItemId)
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND));

        boolean seller = sellerUserId.equals(loginUserId);
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
     * 마감된 물품만 후보를 조회할 수 있다. 유찰도 마감이라 통과시킨다 — 후보가 없다는 사실
     * 자체가 화면이 보여줄 결과이고("낙찰 후보 없음"), 거래 내역에서 같은 경로로 들어온다.
     */
    private void requireClosed(Long auctionItemId) {

        AuctionItemStatus status = auctionItemRepository.findStatus(auctionItemId)
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND));

        if (status != AuctionItemStatus.SOLD && status != AuctionItemStatus.FAILED) {
            throw new ApplicationException(DealErrorType.ITEM_NOT_SOLD);
        }
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
