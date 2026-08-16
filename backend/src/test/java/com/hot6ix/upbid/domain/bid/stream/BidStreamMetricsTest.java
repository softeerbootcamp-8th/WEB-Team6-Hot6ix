package com.hot6ix.upbid.domain.bid.stream;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BidStreamMetricsTest {

    private SimpleMeterRegistry registry;
    private BidStreamMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new BidStreamMetrics(registry);
    }

    @Test
    @DisplayName("Stream 처리 결과는 이벤트 타입과 성공 여부만 라벨로 기록한다")
    void recordsProcessedByTypeAndResult() {
        metrics.recordSuccess("BID_ACCEPTED");
        metrics.recordFailure("ITEM_CLOSING");

        assertThat(registry.get("upbid.bid.stream.processed")
                .tags("type", "BID_ACCEPTED", "result", "success").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("upbid.bid.stream.processed")
                .tags("type", "ITEM_CLOSING", "result", "failure").counter().count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("pending 수와 Stream lag를 Gauge 최신값으로 기록한다")
    void recordsPendingAndLag() {
        metrics.recordPending(7);
        metrics.recordLagMillis(1_250);

        assertThat(registry.get("upbid.bid.stream.pending").gauge().value()).isEqualTo(7);
        assertThat(registry.get("upbid.bid.stream.lag").gauge().value()).isEqualTo(1_250);
    }

    @Test
    @DisplayName("Lua 판정과 Redis seed 실패를 저카디널리티 Counter로 기록한다")
    void recordsLuaDecisionAndSeedFailure() {
        metrics.recordLuaDecision("accepted");
        metrics.recordLuaDecision("rejected_bid_amount_too_low");
        metrics.recordSeedFailure();

        assertThat(registry.get("upbid.bid.lua.decisions")
                .tag("result", "accepted").counter().count()).isEqualTo(1);
        assertThat(registry.get("upbid.bid.lua.decisions")
                .tag("result", "rejected_bid_amount_too_low").counter().count()).isEqualTo(1);
        assertThat(registry.get("upbid.auction.redis.seed.failures").counter().count()).isEqualTo(1);
    }
}
