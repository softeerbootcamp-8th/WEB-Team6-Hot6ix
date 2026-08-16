package com.hot6ix.upbid.domain.bid.repository;

public interface TopBidderProjection {

    Long getAuctionItemId();

    // 보는 사람 본인인지 가리는 데만 쓴다. 응답에 그대로 싣지 않는다
    Long getBidderUserId();

    // 물품 안에서의 순위
    Integer getRankNo();

    String getNickname();

    // 그 사람이 이 물품에 넣은 가장 최고가액
    Long getAmount();
}
