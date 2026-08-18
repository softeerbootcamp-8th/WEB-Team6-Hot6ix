package com.hot6ix.upbid.domain.auction.store;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 물품 시작이 커밋된 뒤 MySQL 스냅샷으로 Redis 입찰 상태를 준비한다. */
@Service
@RequiredArgsConstructor
public class AuctionRedisInitializer {

    private final AuctionRedisSeedSnapshotLoader snapshotLoader;
    private final AuctionRedisStore auctionRedisStore;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void initialize(long itemId) {

        var roomId = snapshotLoader.findInProgressRoomId(itemId);
        if (roomId.isEmpty()) {
            return;
        }
        if (auctionRedisStore.isSeedReady(itemId, roomId.get())) {
            return;
        }
        snapshotLoader.loadInProgressSeed(itemId).ifPresent(auctionRedisStore::seed);
    }
}
