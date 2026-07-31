package com.hot6ix.upbid.domain.auction.service;

import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemDetailResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemSummaryResponseDto;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
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
    private final AuctionRoomRepository auctionRoomRepository;

    /**
     * 경매방의 물품 목록을 상태 우선 순서로 조회한다.
     *
     * @param auctionRoomId 조회할 경매방의 ID
     * @return 물품 요약 목록. 물품이 없으면 빈 목록
     * @throws ApplicationException 경매방이 없거나 soft delete 되었을 때(AUCTION_ROOM_NOT_FOUND)
     */
    public List<AuctionItemSummaryResponseDto> getSummaries(Long auctionRoomId) {
        if (!auctionRoomRepository.existsByAuctionRoomIdAndDeletedAtIsNull(auctionRoomId)) {
            throw new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND);
        }
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
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND));
    }
}
