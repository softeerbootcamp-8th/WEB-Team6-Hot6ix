package com.hot6ix.upbid.domain.bid.stream;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BidStreamFailureClassifierTest {

    private final BidStreamFailureClassifier classifier = new BidStreamFailureClassifier();

    @Test
    @DisplayName("명시적인 영구 실패는 전달 횟수와 무관하게 즉시 격리한다")
    void classifiesExplicitPermanentFailure() {
        RuntimeException failure = new BidStreamPermanentException(
                BidStreamFailureCode.ROOM_MISMATCH, "경매방 불일치");

        BidStreamFailure result = classifier.classify(failure, 1, 5);

        assertThat(result.kind()).isEqualTo(BidStreamFailure.Kind.PERMANENT);
        assertThat(result.code()).isEqualTo(BidStreamFailureCode.ROOM_MISMATCH);
        assertThat(result.shouldQuarantine()).isTrue();
    }

    @Test
    @DisplayName("일반 실패가 최대 처리 횟수 전이면 빠른 재시도를 유지한다")
    void classifiesTransientBeforeAttemptLimit() {
        BidStreamFailure result = classifier.classify(
                new RuntimeException("DB unavailable"), 4, 5);

        assertThat(result.kind()).isEqualTo(BidStreamFailure.Kind.TRANSIENT);
        assertThat(result.shouldQuarantine()).isFalse();
    }

    @Test
    @DisplayName("일반 실패가 최대 처리 횟수에 도달하면 격리 대상으로 전환한다")
    void classifiesExhaustedRetry() {
        BidStreamFailure result = classifier.classify(
                new RuntimeException("DB unavailable"), 5, 5);

        assertThat(result.kind()).isEqualTo(BidStreamFailure.Kind.RETRY_EXHAUSTED);
        assertThat(result.code()).isEqualTo(BidStreamFailureCode.RETRY_EXHAUSTED);
        assertThat(result.shouldQuarantine()).isTrue();
    }

    @Test
    @DisplayName("래핑된 원인 안의 영구 실패도 데이터 오류로 분류한다")
    void findsPermanentFailureInCauseChain() {
        RuntimeException failure = new RuntimeException("wrapped",
                new BidStreamPermanentException(
                        BidStreamFailureCode.EVENT_FORMAT_INVALID, "필드 오류"));

        BidStreamFailure result = classifier.classify(failure, 1, 5);

        assertThat(result.kind()).isEqualTo(BidStreamFailure.Kind.PERMANENT);
        assertThat(result.code()).isEqualTo(BidStreamFailureCode.EVENT_FORMAT_INVALID);
    }
}
