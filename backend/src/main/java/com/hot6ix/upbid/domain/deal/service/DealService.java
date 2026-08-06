package com.hot6ix.upbid.domain.deal.service;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.domain.auction.repository.ClosedAuctionItemProjection;
import com.hot6ix.upbid.domain.deal.dto.response.AuctionItemDealStatusResponseDto;
import com.hot6ix.upbid.domain.deal.dto.response.AuctionRoomDealStatusResponseDto;
import com.hot6ix.upbid.domain.deal.dto.response.DealSummaryResponseDto;
import com.hot6ix.upbid.domain.deal.entity.DealCandidate;
import com.hot6ix.upbid.domain.deal.entity.DealItemStatus;
import com.hot6ix.upbid.domain.deal.entity.DealProgress;
import com.hot6ix.upbid.domain.deal.entity.DealRole;
import com.hot6ix.upbid.domain.deal.repository.DealCandidateRepository;
import com.hot6ix.upbid.domain.deal.repository.DealRepository;
import com.hot6ix.upbid.domain.deal.repository.DealSummaryProjection;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.exception.SellerProfileErrorType;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.global.exception.ApplicationException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DealService {

    private final DealRepository dealRepository;
    private final DealCandidateRepository dealCandidateRepository;
    private final AuctionRoomRepository auctionRoomRepository;
    private final AuctionItemRepository auctionItemRepository;
    private final SellerProfileRepository sellerProfileRepository;

    /**
     * 내가 판 것과 산 것을 최근 마감 순으로 모아 준다. 화면이 전체를 받아 역할·상태로 거르고
     * 건수도 직접 세므로 서버는 필터 파라미터를 두지 않는다.
     *
     * @return 거래가 없으면 빈 목록. 조회는 실패하지 않는다
     */
    public List<DealSummaryResponseDto> getDeals(Long loginUserId) {
        return dealRepository.findDeals(loginUserId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 판매자가 자기 경매방의 물품별 거래 진행 상황을 조회한다. 마감된 물품만 담긴다.
     *
     * <p>없는 방과 남의 방을 구분하지 않고 모두 404다 — 소유자만 보는 조회에서 둘을 나누면
     * 남의 방 ID를 넣어보는 것만으로 그 방의 존재를 알 수 있다. 공유 링크 조회
     * ({@code AuctionRoomShareService.getShareInfo})가 같은 판단을 한다.
     *
     * @param auctionRoomId 조회할 경매방의 ID
     * @param loginUserId   요청자의 회원 ID
     * @throws ApplicationException 판매자 프로필이 없을 때(SELLER_PROFILE_NOT_FOUND),
     *                               경매방이 없거나 본인 소유가 아닐 때(AUCTION_ROOM_NOT_FOUND)
     */
    public AuctionRoomDealStatusResponseDto getRoomDeals(Long auctionRoomId, Long loginUserId) {

        AuctionRoom auctionRoom = findOwnedRoom(auctionRoomId, loginUserId);

        List<ClosedAuctionItemProjection> closedItems = auctionItemRepository.findClosedItems(auctionRoomId);
        Map<Long, List<DealCandidate>> candidatesByItem = findCandidates(closedItems);

        List<AuctionItemDealStatusResponseDto> items = closedItems.stream()
                .map(item -> toItemDealStatus(item, candidatesByItem.get(item.auctionItemId())))
                .toList();

        return new AuctionRoomDealStatusResponseDto(
                auctionRoom.getAuctionRoomId(), auctionRoom.getName(), items);
    }

    /**
     * 마감된 물품 전체의 후보를 한 번에 읽어 물품별로 묶는다. 물품마다 조회하면 물품 수만큼
     * 쿼리가 나간다.
     *
     * <p>물품이 없으면 조회하지 않는다 — 빈 목록으로 {@code in ()} 을 만들면 문법 오류다.
     *
     * <p><b>후보 행이 전부 서버로 넘어온다.</b> 물품 수는
     * {@code AuctionItemRepository.MAX_SUMMARY_SIZE} 로 막혀 있지만 물품당 후보 수는 입찰자
     * 수만큼이라, 넘어오는 행이 "물품 수 × 입찰자 수" 로 늘어난다. 상한으로는 막을 수 없다 —
     * 후보 수를 세려면 전부 봐야 하고, 지금 상대 한 명을 DB 에서 고르려면 물품별 정렬과
     * top-1 이 필요해 {@code LATERAL} 로 돌아가야 한다.
     *
     * <p>지금 규모(방당 물품 수십, 물품당 입찰자 수 명)에서는 이 편이 낫다고 판단했다. 규칙이
     * SQL 과 자바로 갈리지 않는 값이 더 크다. <b>물품당 입찰자가 수십을 넘기 시작하면 다시
     * 본다</b> — 후보 수만 {@code group by} 로 DB 에서 세고 상대 선정만 {@code LATERAL} 로
     * 내리는 중간 형태가 있다.
     */
    private Map<Long, List<DealCandidate>> findCandidates(List<ClosedAuctionItemProjection> closedItems) {

        if (closedItems.isEmpty()) {
            return Map.of();
        }

        List<Long> itemIds = closedItems.stream()
                .map(ClosedAuctionItemProjection::auctionItemId)
                .toList();

        return dealCandidateRepository.findByAuctionItemIds(itemIds).stream()
                .collect(Collectors.groupingBy(
                        candidate -> candidate.getAuctionItem().getAuctionItemId()));
    }

    private AuctionRoom findOwnedRoom(Long auctionRoomId, Long loginUserId) {

        SellerProfile sellerProfile = sellerProfileRepository
                .findByUser_UserIdAndDeletedAtIsNull(loginUserId)
                .orElseThrow(() -> new ApplicationException(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND));

        return auctionRoomRepository
                .findByAuctionRoomIdAndSellerProfile_SellerProfileIdAndDeletedAtIsNull(
                        auctionRoomId, sellerProfile.getSellerProfileId())
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));
    }

    /**
     * 상대·금액·후보 수·상태가 모두 {@link DealProgress} 하나에서 나온다. 같은 후보 목록을
     * 한 번 읽어 계산하므로 서로 어긋날 수 없다.
     *
     * @param candidates 후보가 없는 물품이면 {@code null} 이 넘어온다 (그룹에 행이 없다)
     */
    private AuctionItemDealStatusResponseDto toItemDealStatus(
            ClosedAuctionItemProjection item, List<DealCandidate> candidates) {

        DealProgress progress = candidates == null
                ? DealProgress.empty()
                : DealProgress.of(candidates);

        return new AuctionItemDealStatusResponseDto(
                item.auctionItemId(),
                item.productName(),
                toItemStatus(item.status().name(), progress.completed(), progress.hasPartner()),
                progress.partnerAmount(),
                progress.partnerCandidateId(),
                progress.partnerNickname(),
                progress.candidateCount(),
                progress.failedCount());
    }

    private DealSummaryResponseDto toResponse(DealSummaryProjection deal) {
        return new DealSummaryResponseDto(
                deal.getAuctionItemId(),
                deal.getAuctionRoomId(),
                deal.getShareCode(),
                deal.getProductId(),
                deal.getProductName(),
                deal.getAuctionRoomName(),
                isSeller(deal) ? DealRole.SELLER : DealRole.BUYER,
                toItemStatus(deal.getItemStatus(),
                        deal.getDealCompleted() != 0,
                        deal.getHasWaitingCandidate() != 0),
                deal.getAmount(),
                deal.getPartnerNickname(),
                deal.getSellerProfileId(),
                deal.getClosedAt());
    }

    private boolean isSeller(DealSummaryProjection deal) {
        return deal.getSellerRow() != 0;
    }

    /**
     * 유찰이 먼저다. 입찰이 없어 후보가 만들어지지 않은 물품은 거래가 시작된 적이 없다.
     *
     * <p>그다음이 성사다. 거래가 끝난 뒤에도 하위 순위는 {@code WAITING}으로 남으므로,
     * 대기 후보 여부보다 성사 여부를 먼저 봐야 한다.
     *
     * <p>성사도 대기도 없으면 후보가 전원 실패한 것이다({@code ALL_FAILED}). 거래할 상대가
     * 없다는 점에서 유찰과 같지만, 후보가 있었다는 점이 달라 판매자가 취할 조치도 다르다.
     */
    /**
     * 거래 내역과 경매방 거래 현황이 같은 상태 머신을 쓴다. 두 화면이 같은 물품에 다른 상태를
     * 보여주면 어느 쪽이 맞는지 알 수 없다.
     *
     * <p>세 인자는 각자의 쿼리가 계산한다. "거래할 상대가 있나"를 거래 내역은 대기 후보
     * 존재로, 거래 현황은 실제로 고른 상대의 유무로 답한다 — 후자가 탈퇴 회원까지 반영하므로
     * 더 정확하다.
     *
     * @param hasPartner 지금 거래할 상대가 있는지
     */
    private DealItemStatus toItemStatus(String itemStatus, boolean dealCompleted, boolean hasPartner) {

        if (AuctionItemStatus.valueOf(itemStatus) == AuctionItemStatus.FAILED) {
            return DealItemStatus.UNSOLD;
        }
        if (dealCompleted) {
            return DealItemStatus.COMPLETED;
        }
        return hasPartner ? DealItemStatus.IN_PROGRESS : DealItemStatus.ALL_FAILED;
    }
}
