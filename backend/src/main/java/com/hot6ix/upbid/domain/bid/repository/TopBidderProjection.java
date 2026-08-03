package com.hot6ix.upbid.domain.bid.repository;

public interface TopBidderProjection {

    Long getAuctionItemId();

    // 물품 안에서의 순위
    Integer getRankNo();

    String getNickname();

    // 그 사람이 이 물품에 넣은 가장 최고가액
    Long getAmount();
}
