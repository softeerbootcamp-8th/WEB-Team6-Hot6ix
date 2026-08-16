package com.hot6ix.upbid.domain.sse.event;

import com.hot6ix.upbid.domain.sse.config.SseProperties;
import com.hot6ix.upbid.domain.sse.service.SseMetrics;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
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
    private final RedisScript<Long> ssePublishIfHashMatchesScript = RedisScript.of(
            new ClassPathResource("redis/sse-publish-if-hash-matches.lua"), Long.class);

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

            sseMetrics.recordEventPublished(eventName);

        } catch (RuntimeException e) {
            sseMetrics.recordPublishFailure(eventName, e);
            log.error("sse 이벤트 발행 실패: roomId={}, eventName={}", roomId, eventName, e);
        }
    }

    /**
     * 경매 Hash가 결정 직후 스냅샷과 같을 때만 SSE를 발행한다.
     *
     * <p>Lua 결정 뒤 Java 스레드가 멈춘 사이 더 높은 입찰이나 마감이 처리될 수 있다. 이때
     * 과거 이벤트를 뒤늦게 내보내면 화면이 이전 가격과 상태로 돌아가므로, Hash 비교와 기존
     * sequence/replay/PubSub 발행을 한 Lua 실행으로 묶는다.
     *
     * @return 발행했으면 {@code true}, 더 최신 상태가 있어 건너뛰었거나 발행에 실패하면
     *         {@code false}
     */
    public boolean publishIfHashMatches(
            String eventName,
            Long roomId,
            String hashKey,
            Map<String, String> expectedFields,
            Object data) {

        try {
            SseEventMessage message = new SseEventMessage(roomId, eventName, data, Instant.now(clock));
            List<String> args = new ArrayList<>();
            args.add(EVENT_CHANNEL);
            args.add(objectMapper.writeValueAsString(message));
            args.add(String.valueOf(sseProperties.bufferSize()));
            args.add(String.valueOf(KEY_TTL.toSeconds()));
            expectedFields.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                    .forEach(entry -> {
                        args.add(entry.getKey());
                        args.add(entry.getValue());
                    });

            Long eventId = stringRedisTemplate.execute(
                    ssePublishIfHashMatchesScript,
                    List.of(
                            hashKey,
                            SseRedisKeys.sequence(roomId),
                            SseRedisKeys.events(roomId)),
                    args.toArray());

            if (eventId == null) {
                throw new IllegalStateException("조건부 SSE Lua가 결과를 반환하지 않았다");
            }
            if (eventId == 0L) {
                sseMetrics.recordPublishSkipped(eventName, "stale_state");
                return false;
            }

            sseMetrics.recordEventPublished(eventName);
            return true;
        } catch (RuntimeException e) {
            sseMetrics.recordPublishFailure(eventName, e);
            log.error("조건부 sse 이벤트 발행 실패: roomId={}, eventName={}", roomId, eventName, e);
            return false;
        }
    }
}
