package com.hot6ix.upbid.domain.auction.store;

/** 입찰 판정에 사용하는 Redis 키 이름을 한곳에서 만든다. */
public final class AuctionRedisKeys {

    private AuctionRedisKeys() {
    }

    public static String item(long itemId) {
        return "auction:item:" + itemId;
    }

    public static String leaderboard(long itemId) {
        return item(itemId) + ":leaderboard";
    }

    public static String participants(long roomId) {
        return "auction:room:" + roomId + ":participants";
    }

    public static String participantNicknames(long roomId) {
        return "auction:room:" + roomId + ":participant-nicknames";
    }

    public static String bidRequest(String requestId) {
        return "auction:bid:request:" + requestId;
    }

    public static String stream() {
        return "auction:bid:stream";
    }
}
