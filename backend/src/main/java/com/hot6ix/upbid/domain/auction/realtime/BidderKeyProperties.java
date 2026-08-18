package com.hot6ix.upbid.domain.auction.realtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "upbid.auction.live-state")
public record BidderKeyProperties(String bidderKeySecret) {
}
