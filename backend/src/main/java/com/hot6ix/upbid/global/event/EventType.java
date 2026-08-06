package com.hot6ix.upbid.global.event;

public enum EventType {

    // 방 단위
    ROOM_ENTERED,
    ROOM_CLOSED,
    ROOM_UPDATED,

    // 경매방 편성 — 화면에 물품 목록을 다시 읽으라는 신호다(피드에는 쌓이지 않는다)
    ITEM_ADDED,
    ITEM_REMOVED,

    // 물품 단위
    ITEM_STARTED,
    ITEM_ENDED,
    ITEM_CLOSING_SOON,
    BID_PLACED,
    SOFT_CLOSE_EXTENDED,
    ITEM_CLOSE_ADVANCED,

    // 낙찰
    DEAL_RIGHT_ASSIGNED,
    WINNER_DECIDED,
    ITEM_PASSED
}
