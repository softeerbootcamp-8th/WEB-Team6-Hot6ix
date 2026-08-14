package com.hot6ix.upbid.domain.sse.event;

import com.hot6ix.upbid.domain.sse.config.SseProperties;
import com.hot6ix.upbid.domain.sse.service.SseMetrics;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * SSE 이벤트를 Redis 로 내보낸다.
 * ID 발급, replay 버퍼 적재, 채널 발행이 <b>스크립트 한 번</b>에 끝난다({@code redis/sse-publish.lua}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(SseProperties.class)
public class SseEventPublisher {

    public static final String EVENT_CHANNEL = "upbid:sse:events:v2";

    /** 방이 비정상 종료되어 정리가 안 된 경우에도 키가 남지 않도록 발행마다 갱신한다. */
    private static final Duration KEY_TTL = Duration.ofDays(2);

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<Long> ssePublishScript;
    private final ObjectMapper objectMapper;
    private final SseProperties sseProperties;
    private final SseMetrics sseMetrics;
    private final Clock clock;

    /**
     * 이벤트에 방별 순차 ID 를 붙여 버퍼에 넣고 채널로 내보낸다.
     *
     * <p>발행이 실패하면 그 이벤트는 사라진다. DB 가 Source of Truth 라 화면은 재조회로 복구
     * 되지만, 조용히 사라지면 아무도 모르기 때문에 지표로 남긴다.
     */
    public void publish(String eventName, Long roomId, Object data) {
        try {
            SseEventMessage message = new SseEventMessage(roomId, eventName, data, Instant.now(clock));

            stringRedisTemplate.execute(
                    ssePublishScript,
                    List.of(
                            SseRedisKeys.sequence(roomId),  // KEYS[1]
                            SseRedisKeys.events(roomId)),   // KEYS[2]
                    EVENT_CHANNEL,  // ARGV[1]
                    objectMapper.writeValueAsString(message),   // ARGV[2]
                    String.valueOf(sseProperties.bufferSize()), // ARGV[3]
                    String.valueOf(KEY_TTL.toSeconds()));       // ARGV[4]

        } catch (RuntimeException e) {
            sseMetrics.recordPublishFailure();
            log.error("sse 이벤트 발행 실패: roomId={}, eventName={}", roomId, eventName, e);
        }
    }
}
