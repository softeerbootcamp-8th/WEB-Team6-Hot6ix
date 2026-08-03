package com.hot6ix.upbid.global.alert;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Profile("prod")
@Service
@EnableConfigurationProperties(SlackAlertProperties.class)
public class SlackAlertServiceImpl implements SlackAlertService {

    private static final int STACK_TRACE_LINES = 8;
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SlackAlertProperties properties;
    private final SlackRateLimiter rateLimiter;
    private final RestClient restClient;

    public SlackAlertServiceImpl(SlackAlertProperties properties, SlackRateLimiter rateLimiter) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.restClient = RestClient.create();
    }

    @Override
    public void send(HttpServletRequest request, Exception e) {
        if (properties.webhookUrl() == null || properties.webhookUrl().isBlank()) {
            return;
        }

        String errorKey = request.getMethod() + " " + request.getRequestURI()
                + " " + e.getClass().getSimpleName();

        if (!rateLimiter.shouldSend(errorKey, properties.cooldownSeconds())) {
            log.debug("Slack 알림 쿨다운 중 - errorKey={}", errorKey);
            return;
        }

        String message = buildMessage(request, e);

        CompletableFuture.runAsync(() -> {
            try {
                restClient.post()
                        .uri(properties.webhookUrl())
                        .body(Map.of("text", message))
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception ex) {
                log.warn("Slack 알림 전송 실패 - {}", ex.getMessage());
            }
        });
    }

    private String buildMessage(HttpServletRequest request, Exception e) {
        String stackTrace = Arrays.stream(e.getStackTrace())
                .limit(STACK_TRACE_LINES)
                .map(StackTraceElement::toString)
                .reduce("", (a, b) -> a + "\n  at " + b);

        return String.format("""
                🚨 *[ERROR]* `%s %s`
                *시각*: %s
                *예외*: `%s`
                *메시지*: %s
                *스택트레이스*:
                ```%s```
                """,
                request.getMethod(),
                request.getRequestURI(),
                LocalDateTime.now().format(FORMATTER),
                e.getClass().getSimpleName(),
                e.getMessage(),
                stackTrace
        );
    }
}
