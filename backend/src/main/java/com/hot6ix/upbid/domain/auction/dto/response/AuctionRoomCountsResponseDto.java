package com.hot6ix.upbid.domain.auction.dto.response;

/** 목록 화면 필터 바의 탭 숫자. 화면의 "전체"는 셋을 더한 값이다. */
public record AuctionRoomCountsResponseDto(
        Long before,
        Long open,
        Long closed
) {
}
