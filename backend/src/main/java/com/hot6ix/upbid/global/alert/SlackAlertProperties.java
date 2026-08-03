package com.hot6ix.upbid.global.alert;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.alert.slack")
public record SlackAlertProperties(
        String webhookUrl,
        long cooldownSeconds
) {
}
