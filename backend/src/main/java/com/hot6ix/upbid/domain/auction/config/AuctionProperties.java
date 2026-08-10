package com.hot6ix.upbid.domain.auction.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 경매 규칙 중 <b>값을 바꿔 가며 재야 하는</b> 것만 밖으로 뺀 설정.
 * 기본값은 {@code application.yaml}에 있고 서비스 규칙 그대로다.
 */
@ConfigurationProperties(prefix = "upbid.auction")
public record AuctionProperties(
        int maxInProgressPerRoom
) {
}
