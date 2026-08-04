package com.hot6ix.upbid.domain.deal.entity;

/**
 * 한 거래 또는 한 경매방에서 나의 역할. 후보 목록·거래 내역·내 경매방 목록이 같은 값을 쓴다 —
 * 화면도 거래와 방에 같은 배지를 붙이므로 어휘를 하나로 둔다. 판매자와 구매자가 같은
 * endpoint를 쓰고 서버가 판정하며, 화면 제어가 아니라 이 값으로 무엇을 내릴지 정한다.
 */
public enum DealRole {
    SELLER,
    BUYER
}
