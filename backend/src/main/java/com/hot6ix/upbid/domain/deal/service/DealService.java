package com.hot6ix.upbid.domain.deal.service;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoom;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.domain.deal.dto.response.AuctionItemDealStatusResponseDto;
import com.hot6ix.upbid.domain.deal.dto.response.AuctionRoomDealStatusResponseDto;
import com.hot6ix.upbid.domain.deal.dto.response.DealSummaryResponseDto;
import com.hot6ix.upbid.domain.deal.entity.DealItemStatus;
import com.hot6ix.upbid.domain.deal.entity.DealRole;
import com.hot6ix.upbid.domain.deal.repository.DealRepository;
import com.hot6ix.upbid.domain.deal.repository.DealSummaryProjection;
import com.hot6ix.upbid.domain.deal.repository.RoomDealStatusProjection;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.exception.SellerProfileErrorType;
import com.hot6ix.upbid.domain.user.repository.SellerProfileRepository;
import com.hot6ix.upbid.global.exception.ApplicationException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DealService {

    private final DealRepository dealRepository;
    private final AuctionRoomRepository auctionRoomRepository;
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

        List<AuctionItemDealStatusResponseDto> items =
                dealRepository.findRoomDealStatuses(auctionRoomId).stream()
                        .map(this::toItemDealStatus)
                        .toList();

        return new AuctionRoomDealStatusResponseDto(
                auctionRoom.getAuctionRoomId(), auctionRoom.getName(), items);
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

    /** 상대의 유무를 {@code dealCandidateId}로 본다. 상태와 상대가 같은 한 행에서 나와 어긋나지 않는다. */
    private AuctionItemDealStatusResponseDto toItemDealStatus(RoomDealStatusProjection item) {
        return new AuctionItemDealStatusResponseDto(
                item.getAuctionItemId(),
                item.getProductName(),
                toItemStatus(item.getItemStatus(),
                        item.getDealCompleted() != 0,
                        item.getDealCandidateId() != null),
                item.getAmount(),
                item.getDealCandidateId(),
                item.getPartnerNickname(),
                item.getCandidateCount(),
                item.getFailedCandidateCount());
    }

    private DealSummaryResponseDto toResponse(DealSummaryProjection deal) {
        return new DealSummaryResponseDto(
                deal.getAuctionItemId(),
                deal.getAuctionRoomId(),
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
