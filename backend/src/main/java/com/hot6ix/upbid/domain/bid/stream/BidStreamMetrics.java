package com.hot6ix.upbid.domain.bid.stream;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/** 입찰 Stream과 Redis Lua 처리 상태를 기록하는 저카디널리티 메트릭. */
@Component
public class BidStreamMetrics {

    private final MeterRegistry registry;
    private final Counter acknowledgeFailures;
    private final Counter seedFailures;
    private final Timer persistence;
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong lagMillis = new AtomicLong();

    public BidStreamMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.acknowledgeFailures = registry.counter("upbid.bid.stream.ack.failures");
        this.seedFailures = registry.counter("upbid.auction.redis.seed.failures");
        this.persistence = registry.timer("upbid.bid.stream.persist");
        Gauge.builder("upbid.bid.stream.pending", pending, AtomicLong::get)
                .register(registry);
        Gauge.builder("upbid.bid.stream.lag", lagMillis, AtomicLong::get)
                .baseUnit("milliseconds")
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

    public void recordLagMillis(long value) {
        lagMillis.set(Math.max(value, 0));
    }

    public void recordAcknowledgeFailure() {
        acknowledgeFailures.increment();
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
                "type", type == null || type.isBlank() ? "UNKNOWN" : type,
                "result", result);
    }
}
