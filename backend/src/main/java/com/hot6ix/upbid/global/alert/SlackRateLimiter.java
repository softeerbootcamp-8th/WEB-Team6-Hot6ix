package com.hot6ix.upbid.global.alert;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 동일한 에러가 짧은 시간 안에 반복될 때 Slack 채널이 도배되지 않도록
 * per-error-key 쿨다운을 적용한다.
 *
 * <p>errorKey = "METHOD /path ExceptionClass" 조합으로 식별한다.
 * 같은 키의 알림이 cooldownSeconds 이내에 이미 나간 경우 {@code shouldSend}가 false를 반환한다.
 *
 * <p>맵은 무한정 자라지 않도록 마지막 전송이 10분 이상 지난 항목을 주기적으로 정리한다.
 * 정리는 shouldSend 호출 시점마다 수행하므로 별도 스케줄러가 필요없다.
 */
@Component
public class SlackRateLimiter {

    private static final long CLEANUP_THRESHOLD_MS = 10 * 60 * 1000L;

    private final ConcurrentHashMap<String, Long> lastSentAt = new ConcurrentHashMap<>();

    public boolean shouldSend(String errorKey, long cooldownSeconds) {
        long now = System.currentTimeMillis();
        long cooldownMs = cooldownSeconds * 1000L;

        cleanupStaleEntries(now);

        Long last = lastSentAt.get(errorKey);

        if (last != null && now - last < cooldownMs) {
            return false;
        }

        lastSentAt.put(errorKey, now);
        return true;
    }

    private void cleanupStaleEntries(long now) {
        lastSentAt.entrySet().removeIf(entry -> now - entry.getValue() > CLEANUP_THRESHOLD_MS);
    }
}
