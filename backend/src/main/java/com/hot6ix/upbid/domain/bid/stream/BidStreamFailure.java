package com.hot6ix.upbid.domain.bid.stream;

/** Consumer가 실패 이벤트의 다음 처리 단계를 선택하는 분류 결과. */
public record BidStreamFailure(
        Kind kind,
        BidStreamFailureCode code
) {
    public enum Kind {
        TRANSIENT,
        PERMANENT,
        RETRY_EXHAUSTED
    }

    public boolean shouldQuarantine() {
        return kind != Kind.TRANSIENT;
    }
}
