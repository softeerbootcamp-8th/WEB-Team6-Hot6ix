package com.hot6ix.upbid.domain.deal.entity;

/**
 * 후보 목록을 보는 사람의 역할. 판매자와 입찰자가 같은 endpoint를 쓰고 서버가 판정한다.
 * 화면 제어로 권한을 보장하지 않으므로, 연락처를 내릴지도 이 값으로 정한다.
 */
public enum DealViewerRole {
    SELLER,
    BIDDER
}
