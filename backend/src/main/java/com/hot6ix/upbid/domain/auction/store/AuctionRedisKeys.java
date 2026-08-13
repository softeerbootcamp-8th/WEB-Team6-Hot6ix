package com.hot6ix.upbid.domain.auction.store;

/** 입찰 판정에 사용하는 Redis 키 이름을 한곳에서 만든다. */
public final class AuctionRedisKeys {

    private AuctionRedisKeys() {
    }

    public static String item(long itemId) {
        return "auction:item:" + itemId;
    }

    public static String participants(long roomId) {
        return "auction:room:" + roomId + ":participants";
    }

    public static String accepted(long itemId) {
        return "auction:item:" + itemId + ":accepted";
    }

    public static String stream() {
        return "auction:bid:stream";
    }
}
