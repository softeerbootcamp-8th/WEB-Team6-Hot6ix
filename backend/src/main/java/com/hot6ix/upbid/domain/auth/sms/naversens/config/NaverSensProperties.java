package com.hot6ix.upbid.domain.auth.sms.naversens.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ncp.sms")
public record NaverSensProperties(
        String accessKey,
        String secretKey,
        String serviceId,
        String sendFrom,
        Duration connectTimeout,
        Duration readTimeout
) {
}
