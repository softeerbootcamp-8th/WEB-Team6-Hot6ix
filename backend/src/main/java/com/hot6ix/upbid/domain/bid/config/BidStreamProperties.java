package com.hot6ix.upbid.domain.bid.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Redis Stream 입찰 영속화 Consumer의 운영 설정. */
@ConfigurationProperties(prefix = "upbid.bid-stream")
public record BidStreamProperties(
        String key,
        String group,
        String consumer,
        Duration pollDelay,
        Duration blockTimeout,
        Duration lockAtMostFor
) {
}
