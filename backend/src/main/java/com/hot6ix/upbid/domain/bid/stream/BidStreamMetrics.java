package com.hot6ix.upbid.domain.bid.stream;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/** 입찰 Stream과 Redis Lua 처리 상태를 기록하는 저카디널리티 메트릭. */
@Component
public class BidStreamMetrics {

    private final MeterRegistry registry;
    private final Counter acknowledgeFailures;
    private final Counter quarantineWriteFailures;
    private final Counter seedFailures;
    private final Timer persistence;
    private final DistributionSummary drainRecords;
    private final Timer drainDuration;
    private final DistributionSummary deliveryLag;
    private final AtomicLong backlog = new AtomicLong();
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong lagMillis = new AtomicLong();
    private final AtomicLong oldestPendingAgeSeconds = new AtomicLong();
    private final AtomicLong halted = new AtomicLong();

    public BidStreamMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.acknowledgeFailures = registry.counter("upbid.bid.stream.ack.failures");
        this.quarantineWriteFailures = registry.counter(
                "upbid.bid.stream.quarantine.write.failures");
        this.seedFailures = registry.counter("upbid.auction.redis.seed.failures");
        this.persistence = registry.timer("upbid.bid.stream.persist");
        this.drainRecords = DistributionSummary.builder("upbid.bid.stream.drain.records")
                .baseUnit("records")
                .publishPercentileHistogram()
                .register(registry);
        this.drainDuration = Timer.builder("upbid.bid.stream.drain.duration")
                .publishPercentileHistogram()
                .register(registry);
        this.deliveryLag = DistributionSummary.builder("upbid.bid.stream.delivery.lag")
                .baseUnit("milliseconds")
                .publishPercentileHistogram()
                .register(registry);
        Gauge.builder("upbid.bid.stream.backlog", backlog, AtomicLong::get)
                .register(registry);
        Gauge.builder("upbid.bid.stream.pending", pending, AtomicLong::get)
                .register(registry);
        Gauge.builder("upbid.bid.stream.lag", lagMillis, AtomicLong::get)
                .baseUnit("milliseconds")
                .register(registry);
        Gauge.builder("upbid.bid.stream.oldest.pending.age",
                        oldestPendingAgeSeconds, AtomicLong::get)
                .baseUnit("seconds")
                .register(registry);
        Gauge.builder("upbid.bid.stream.halted", halted, AtomicLong::get)
                .register(registry);
    }

    public void recordPersistence(Runnable operation) {
        persistence.record(operation);
    }

    public void recordSuccess(String type) {
        processed(type, "success").increment();
    }

    public void recordFailure(String type) {
        processed(type, "failure").increment();
    }

    public void recordPending(long count) {
        pending.set(count);
    }

    public void recordBacklog(long count) {
        backlog.set(Math.max(count, 0));
    }

    public void recordDrain(int records, Duration duration, DrainLimit limit) {
        drainRecords.record(Math.max(records, 0));
        drainDuration.record(duration.isNegative() ? Duration.ZERO : duration);

        switch (limit) {
            case NONE -> {
                // Stream이 비어서 자연스럽게 끝난 tick은 상한 도달이 아니다.
            }
            case RECORDS -> drainLimited("records").increment();
            case DURATION -> drainLimited("duration").increment();
        }
    }

    public void recordLagMillis(long value) {
        long nonNegative = Math.max(value, 0);
        lagMillis.set(nonNegative);
        deliveryLag.record(nonNegative);
    }

    public void recordIdleLag() {
        lagMillis.set(0);
    }

    public void recordAcknowledgeFailure() {
        acknowledgeFailures.increment();
    }

    public void recordRetry(String failureKind) {
        registry.counter("upbid.bid.stream.retries",
                "failure.kind", normalize(failureKind)).increment();
    }

    public void recordQuarantined(String eventType, String failureCode) {
        registry.counter("upbid.bid.stream.quarantined",
                "event.type", normalize(eventType),
                "failure.code", normalize(failureCode)).increment();
    }

    public void recordQuarantineWriteFailure() {
        quarantineWriteFailures.increment();
    }

    public void recordOldestPendingAgeSeconds(long value) {
        oldestPendingAgeSeconds.set(Math.max(value, 0));
    }

    public void recordHalted(boolean value) {
        halted.set(value ? 1 : 0);
    }

    public void recordLuaDecision(String result) {
        registry.counter("upbid.bid.lua.decisions", "result", result).increment();
    }

    public void recordLuaFailure(String stage) {
        registry.counter("upbid.bid.lua.failures", "stage", stage).increment();
    }

    public void recordSeedFailure() {
        seedFailures.increment();
    }

    private Counter processed(String type, String result) {
        return registry.counter("upbid.bid.stream.processed",
                "type", normalize(type),
                "result", result);
    }

    private Counter drainLimited(String reason) {
        return registry.counter("upbid.bid.stream.drain.limited", "reason", reason);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }

    public enum DrainLimit {
        NONE,
        RECORDS,
        DURATION
    }
}
