package com.hot6ix.upbid.domain.auction.store;

/** Redis가 판정 원본인 동안의 물품 상태. CLOSING은 MySQL 마감 저장 대기 상태다. */
public enum AuctionRedisLiveStatus {
    IN_PROGRESS,
    CLOSING
}
