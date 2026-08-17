package com.hot6ix.upbid.domain.bid.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.hot6ix.upbid.domain.auction.store.AuctionRedisStore;
import com.hot6ix.upbid.domain.bid.config.BidStreamConfig;
import com.hot6ix.upbid.domain.bid.service.BidStreamPersistenceService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

class BidStreamConsumerConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ConsumerConfiguration.class)
            .withPropertyValues(
                    "upbid.bid-stream.key=auction:bid:stream",
                    "upbid.bid-stream.group=bid-mysql-writers",
                    "upbid.bid-stream.consumer=test-bid-writer",
                    "upbid.bid-stream.poll-delay=100ms",
                    "upbid.bid-stream.block-timeout=50ms",
                    "upbid.bid-stream.lock-at-most-for=60s",
                    "upbid.bid-stream.retry-delay=5s",
                    "upbid.bid-stream.max-fast-attempts=5",
                    "upbid.bid-stream.halted-retry-delay=60s",
                    "upbid.bid-stream.transaction-timeout-seconds=20",
                    "upbid.bid-stream.max-records-per-poll=50",
                    "upbid.bid-stream.max-drain-duration=1s")
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .withBean(BidStreamPersistenceService.class,
                    () -> mock(BidStreamPersistenceService.class))
            .withBean(BidStreamMetrics.class,
                    () -> new BidStreamMetrics(new SimpleMeterRegistry()))
            .withBean(BidStreamFailureClassifier.class, BidStreamFailureClassifier::new)
            .withBean(BidStreamQuarantineStore.class,
                    () -> mock(BidStreamQuarantineStore.class))
            .withBean(Clock.class, Clock::systemUTC);

    @Test
    @DisplayName("Consumer 비활성화 설정이면 Scheduler Consumer 빈을 만들지 않는다")
    void disablesConsumerBean() {
        contextRunner
                .withPropertyValues("upbid.bid-stream.consumer-enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(BidStreamConsumer.class);
                    assertThat(context).hasSingleBean(AuctionRedisStore.class);
                    assertThat(context).hasBean("bidStreamGroupInitializer");
                });
    }

    @Test
    @DisplayName("Consumer 활성화 설정이면 Scheduler Consumer 빈을 만든다")
    void enablesConsumerBean() {
        contextRunner
                .withPropertyValues("upbid.bid-stream.consumer-enabled=true")
                .run(context -> assertThat(context).hasSingleBean(BidStreamConsumer.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import({AuctionRedisStore.class, BidStreamConfig.class, BidStreamConsumer.class})
    static class ConsumerConfiguration {
    }
}
