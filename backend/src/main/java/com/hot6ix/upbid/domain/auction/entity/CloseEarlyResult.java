package com.hot6ix.upbid.domain.auction.entity;

/**
 * {@link AuctionItem#closeEarly} 의 판정 결과. 앞당기지 못한 이유가 셋이고 화면에서 안내가 서로
 * 달라야 해서, 엔티티는 어느 쪽인지만 가리고 <b>HTTP 응답으로 옮기는 것은 Service 가 한다.</b>
 */
public enum CloseEarlyResult {

    /** 앞당겼다. {@code end_at}이 바뀌었다. */
    ADVANCED,

    /**
     * 이미 연장 구간 안이라 앞당길 자리가 없다. 요청한 값과 무관하게 걸리며, <b>가장 짧게 남길 수
     * 있는 트리거 초로도 마감이 뒤로 밀리는 상태</b>다.
     */
    ALREADY_CLOSING_SOON,

    /** 요청한 남은 초가 경매방의 Soft Close 트리거보다 짧다. */
    REMAINING_TOO_SHORT,

    /** 요청한 남은 초가 지금 마감까지 남은 시간 이상이라, 받아주면 앞당기기가 마감을 미룬다. */
    REMAINING_TOO_LONG
}
