package com.hot6ix.upbid.domain.bid.stream;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/** 입찰 Stream 소비 상태를 기록하는 저카디널리티 메트릭. */
@Component
public class BidStreamMetrics {

    private final Counter processed;
    private final Counter failures;
    private final Counter acknowledgeFailures;
    private final Timer persistence;

    public BidStreamMetrics(MeterRegistry registry) {
        this.processed = registry.counter("upbid.bid.stream.processed", "result", "success");
        this.failures = registry.counter("upbid.bid.stream.processed", "result", "failure");
        this.acknowledgeFailures = registry.counter("upbid.bid.stream.ack.failures");
        this.persistence = registry.timer("upbid.bid.stream.persist");
    }

    public void recordPersistence(Runnable operation) {
        persistence.record(operation);
    }

    public void recordSuccess() {
        processed.increment();
    }

    public void recordFailure() {
        failures.increment();
    }

    public void recordAcknowledgeFailure() {
        acknowledgeFailures.increment();
    }
}
