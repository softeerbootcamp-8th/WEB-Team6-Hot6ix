package com.hot6ix.upbid.global.event;

public enum EventType {

    // 방 단위
    ROOM_ENTERED,
    ROOM_CLOSED,

    // 물품 단위
    ITEM_STARTED,
    ITEM_ENDED,
    ITEM_CLOSING_SOON,
    BID_PLACED,
    SOFT_CLOSE_EXTENDED,

    // 낙찰
    DEAL_RIGHT_ASSIGNED,
    WINNER_DECIDED,
    ITEM_PASSED
}
