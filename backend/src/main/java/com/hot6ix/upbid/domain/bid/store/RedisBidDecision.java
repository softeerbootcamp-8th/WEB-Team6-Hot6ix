package com.hot6ix.upbid.domain.bid.store;

/** Redis Lua가 내린 입찰 승인 또는 거절 결과. */
public sealed interface RedisBidDecision {

    record Accepted(
            String requestId,
            long acceptedAtMillis,
            long endAtMillis,
            int extendedSeconds,
            boolean duplicate
    ) implements RedisBidDecision {
    }

    record Rejected(Reason reason) implements RedisBidDecision {
    }

    enum Reason {
        KEY_MISSING,
        ITEM_NOT_IN_PROGRESS,
        ITEM_CLOSED,
        ALREADY_TOP_BIDDER,
        BID_AMOUNT_TOO_LOW,
        INVALID_BID_UNIT,
        SELLER_CANNOT_BID,
        TERMS_NOT_AGREED
    }
}
