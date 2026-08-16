package com.hot6ix.upbid.domain.auction.store;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.AuctionParticipantRepository;
import com.hot6ix.upbid.global.exception.ApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 물품 시작이 커밋된 뒤 MySQL 스냅샷으로 Redis 입찰 상태를 준비한다. */
@Service
@RequiredArgsConstructor
public class AuctionRedisInitializer {

    private final AuctionItemRepository auctionItemRepository;
    private final AuctionParticipantRepository auctionParticipantRepository;
    private final AuctionRedisStore auctionRedisStore;

    @Transactional(readOnly = true)
    public void initialize(long itemId) {

        AuctionItem item = auctionItemRepository.findById(itemId)
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND));

        if (item.getStatus() != AuctionItemStatus.IN_PROGRESS) {
            return;
        }

        Long roomId = item.getAuctionRoom().getAuctionRoomId();
        if (auctionRedisStore.isSeedReady(itemId, roomId)) {
            return;
        }

        Long sellerUserId = auctionItemRepository.findSellerUserId(itemId)
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND));

        auctionRedisStore.seed(
                item,
                roomId,
                sellerUserId,
                auctionParticipantRepository.findAgreedUserIds(roomId));
    }
}
