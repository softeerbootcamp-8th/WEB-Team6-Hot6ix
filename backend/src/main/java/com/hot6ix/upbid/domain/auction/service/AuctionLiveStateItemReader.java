package com.hot6ix.upbid.domain.auction.service;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Redis Live State를 읽기 전에 진행 중 물품 ID만 짧은 DB 트랜잭션으로 가져온다. */
@Service
@RequiredArgsConstructor
public class AuctionLiveStateItemReader {

    private final AuctionItemRepository auctionItemRepository;

    @Transactional(readOnly = true)
    public List<Long> findInProgressItemIds(long roomId) {
        return auctionItemRepository
                .findByAuctionRoom_AuctionRoomIdAndStatus(roomId, AuctionItemStatus.IN_PROGRESS)
                .stream()
                .map(AuctionItem::getAuctionItemId)
                .toList();
    }
}
