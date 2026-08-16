package com.hot6ix.upbid.domain.bid.stream;

import org.springframework.stereotype.Component;

/** 명시적인 영구 실패와 전달 횟수를 바탕으로 재시도 단계를 정한다. */
@Component
public class BidStreamFailureClassifier {

    public BidStreamFailure classify(
            Throwable failure, long deliveryCount, int maxFastAttempts) {
        BidStreamPermanentException permanent = findPermanent(failure);
        if (permanent != null) {
            return new BidStreamFailure(
                    BidStreamFailure.Kind.PERMANENT, permanent.getCode());
        }
        if (deliveryCount >= maxFastAttempts) {
            return new BidStreamFailure(
                    BidStreamFailure.Kind.RETRY_EXHAUSTED,
                    BidStreamFailureCode.RETRY_EXHAUSTED);
        }
        return new BidStreamFailure(BidStreamFailure.Kind.TRANSIENT, null);
    }

    private static BidStreamPermanentException findPermanent(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof BidStreamPermanentException permanent) {
                return permanent;
            }
            current = current.getCause();
        }
        return null;
    }
}
