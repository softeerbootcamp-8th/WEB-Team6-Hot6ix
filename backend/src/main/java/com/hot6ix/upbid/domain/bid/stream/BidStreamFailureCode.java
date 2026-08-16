package com.hot6ix.upbid.domain.bid.stream;

/** Stream 이벤트를 자동 재시도만으로 복구할 수 없는 저카디널리티 실패 코드. */
public enum BidStreamFailureCode {
    EVENT_FORMAT_INVALID,
    REFERENCED_RESOURCE_MISSING,
    ROOM_MISMATCH,
    IDEMPOTENCY_CONFLICT,
    RETRY_EXHAUSTED
}
