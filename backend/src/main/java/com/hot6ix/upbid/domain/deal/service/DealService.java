package com.hot6ix.upbid.domain.deal.service;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.deal.dto.response.DealSummaryResponseDto;
import com.hot6ix.upbid.domain.deal.entity.DealItemStatus;
import com.hot6ix.upbid.domain.deal.entity.DealRole;
import com.hot6ix.upbid.domain.deal.repository.DealRepository;
import com.hot6ix.upbid.domain.deal.repository.DealSummaryProjection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DealService {

    private final DealRepository dealRepository;

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

    private DealSummaryResponseDto toResponse(DealSummaryProjection deal) {
        return new DealSummaryResponseDto(
                deal.getAuctionItemId(),
                deal.getAuctionRoomId(),
                deal.getProductId(),
                deal.getProductName(),
                deal.getAuctionRoomName(),
                isSeller(deal) ? DealRole.SELLER : DealRole.BUYER,
                toItemStatus(deal),
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
     * <p>후보가 전원 실패한 물품은 여기서 {@code IN_PROGRESS}로 남는다. 화면에 그 상태가 없어
     * 프론트 연동 때 함께 정한다.
     */
    private DealItemStatus toItemStatus(DealSummaryProjection deal) {

        if (AuctionItemStatus.valueOf(deal.getItemStatus()) == AuctionItemStatus.FAILED) {
            return DealItemStatus.UNSOLD;
        }
        return deal.getDealCompleted() != 0 ? DealItemStatus.COMPLETED : DealItemStatus.IN_PROGRESS;
    }
}
