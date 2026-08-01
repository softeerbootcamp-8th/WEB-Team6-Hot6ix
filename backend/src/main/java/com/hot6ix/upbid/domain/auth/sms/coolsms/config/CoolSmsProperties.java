package com.hot6ix.upbid.domain.auth.sms.coolsms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "coolsms")
public record CoolSmsProperties(
        String apiKey,
        String apiSecret,
        String from
) {
}
