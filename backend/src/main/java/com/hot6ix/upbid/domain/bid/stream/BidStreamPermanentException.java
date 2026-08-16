package com.hot6ix.upbid.domain.bid.stream;

import lombok.Getter;

/** 같은 Stream 원문을 그대로 재시도해도 해결되지 않는 데이터·불변식 오류. */
@Getter
public class BidStreamPermanentException extends RuntimeException {

    private final BidStreamFailureCode code;

    public BidStreamPermanentException(BidStreamFailureCode code, String message) {
        super(message);
        this.code = code;
    }

    public BidStreamPermanentException(
            BidStreamFailureCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
