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
        /** 트리거 초로도 마감이 뒤로 밀린다. 요청 값과 무관하게 앞당길 자리가 없다. */
        ALREADY_CLOSING_SOON,
        /** 요청한 남은 초가 경매방의 연장 트리거보다 짧다. */
        REMAINING_TOO_SHORT,
        /** 요청한 남은 초가 지금 마감까지 남은 시간 이상이다. */
        REMAINING_TOO_LONG
    }
}
