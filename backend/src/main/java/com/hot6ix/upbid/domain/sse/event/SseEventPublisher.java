package com.hot6ix.upbid.domain.sse.event;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * SSE 이벤트를 Redis 채널로 내보낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseEventPublisher {

    public static final String EVENT_CHANNEL = "upbid:sse:events";

    private static final String SEQUENCE_KEY_PREFIX = "upbid:sse:room:";
    private static final String SEQUENCE_KEY_SUFFIX = ":seq";

    // 방이 비정상 종료되어 clearSequence()가 불리지 못한 경우 키가 남지 않도록 TTL 설정
    private static final Duration SEQUENCE_TTL = Duration.ofDays(2);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 이벤트에 방별 순차 ID를 붙여 채널로 내보낸다.
     */
    public void publish(String eventName, Long roomId, Object data) {
        try {
            SseEventMessage message = new SseEventMessage(
                    roomId, nextId(roomId), eventName, data, Instant.now(clock));

            stringRedisTemplate.convertAndSend(EVENT_CHANNEL, objectMapper.writeValueAsString(message));
        } catch (RuntimeException e) {
            log.error("sse 이벤트 발행 실패: roomId={}, eventName={}", roomId, eventName, e);
        }
    }

    /**
     * 방이 닫힐 때 순차 ID 카운터를 지운다.
     */
    public void clearSequence(Long roomId) {
        try {
            stringRedisTemplate.delete(sequenceKey(roomId));
        } catch (RuntimeException e) {
            log.warn("sse 순차 ID 카운터 삭제 실패: roomId={}", roomId, e);
        }
    }

    private long nextId(Long roomId) {
        String key = sequenceKey(roomId);
        Long id = stringRedisTemplate.opsForValue().increment(key);

        if (id == null) {
            throw new IllegalStateException("redis INCR가 값을 돌려주지 않았다: key=" + key);
        }

        // 첫 count면 TTL 설정
        if (id == 1L) {
            stringRedisTemplate.expire(key, SEQUENCE_TTL);
        }

        return id;
    }

    private String sequenceKey(Long roomId) {
        return SEQUENCE_KEY_PREFIX + roomId + SEQUENCE_KEY_SUFFIX;
    }
}
