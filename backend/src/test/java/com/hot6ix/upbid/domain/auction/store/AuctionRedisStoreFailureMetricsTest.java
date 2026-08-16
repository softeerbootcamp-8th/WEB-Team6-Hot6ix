package com.hot6ix.upbid.domain.auction.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.bid.stream.BidStreamMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class AuctionRedisStoreFailureMetricsTest {

    @Mock
    private StringRedisTemplate redis;

    private SimpleMeterRegistry registry;
    private AuctionRedisStore store;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        store = new AuctionRedisStore(redis, new BidStreamMetrics(registry));
    }

    @Test
    @DisplayName("Lua 반환 계약을 해석하지 못하면 parsing 실패로 기록한다")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void recordsParsingFailureWhenLuaResultIsMalformed() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(List.of("ACCEPTED", "request-1"));

        assertThatThrownBy(() -> store.evaluateBid(101L, "request-1", 11L, 10_000L, 1_000L))
                .isInstanceOf(IllegalStateException.class);

        assertThat(registry.find("upbid.bid.lua.failures")
                .tag("stage", "parsing").counter())
                .isNotNull()
                .extracting(counter -> counter.count())
                .isEqualTo(1.0);
    }
}
