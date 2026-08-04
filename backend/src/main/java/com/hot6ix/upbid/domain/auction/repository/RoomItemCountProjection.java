package com.hot6ix.upbid.domain.auction.repository;

/** 경매방 하나의 물품 수. 방 목록이 방마다 물품 개수를 보여주는 데 쓴다. */
public record RoomItemCountProjection(
        Long auctionRoomId,
        Long itemCount
) {
}
