package com.hot6ix.upbid.domain.auction.store;

/** Redis Lua가 결정한 자연 마감 또는 판매자 마감 앞당기기 결과. */
public sealed interface RedisCloseDecision {

    record Closing(long closedAtMillis) implements RedisCloseDecision {
    }

    record Advanced(long endAtMillis, int remainingSeconds,
                    long advancedAtMillis) implements RedisCloseDecision {
    }

    record Rejected(Reason reason, Long endAtMillis) implements RedisCloseDecision {
    }

    enum Reason {
        KEY_MISSING,
        ITEM_NOT_IN_PROGRESS,
        NOT_OWNER,
        NOT_DUE,
        ALREADY_CLOSING_SOON
    }
}
