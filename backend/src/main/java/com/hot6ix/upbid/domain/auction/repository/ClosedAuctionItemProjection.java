package com.hot6ix.upbid.domain.auction.repository;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;

/** 마감된 물품 한 줄. 거래 현황이 물품 이름과 낙찰·유찰 여부만 필요해서 그 둘만 담는다. */
public record ClosedAuctionItemProjection(
        Long auctionItemId,
        String productName,
        AuctionItemStatus status
) {
}
