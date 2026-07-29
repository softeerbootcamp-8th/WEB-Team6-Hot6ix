package com.hot6ix.upbid.domain.auction.service;

import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemDetailResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemSummaryResponseDto;
import com.hot6ix.upbid.domain.auction.exception.AuctionItemErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.global.exception.ApplicationException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionItemService {

    private final AuctionItemRepository auctionItemRepository;

    /**
     * 경매방의 물품 목록을 상태 우선 순서로 조회한다.
     * 경매방 존재 여부는 확인하지 않으므로, 없는 경매방과 물품이 0개인 경매방이
     * 모두 빈 목록으로 나간다. 이 구분은 경매방 Repository 도입 후 추가한다.
     *
     * @param auctionRoomId 조회할 경매방의 ID
     * @return 물품 요약 목록. 물품이 없으면 빈 목록
     */
    public List<AuctionItemSummaryResponseDto> getSummaries(Long auctionRoomId) {
        return auctionItemRepository.findSummaries(auctionRoomId);
    }

    /**
     * 물품 상세를 조회한다. 상태로 거르지 않으므로 낙찰·유찰된 물품도 조회된다.
     *
     * @param auctionItemId 조회할 물품의 ID
     * @return 물품 상세
     * @throws ApplicationException 물품이 없을 때(AUCTION_ITEM_NOT_FOUND)
     */
    public AuctionItemDetailResponseDto getDetail(Long auctionItemId) {
        return auctionItemRepository.findDetail(auctionItemId)
                .orElseThrow(() -> new ApplicationException(AuctionItemErrorType.AUCTION_ITEM_NOT_FOUND));
    }
}
