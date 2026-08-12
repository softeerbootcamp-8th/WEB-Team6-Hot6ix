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

    /**
     * 모든 방의 이벤트가 이 채널 하나로 흐르고, 받은 쪽이 {@code roomId}로 거른다.
     *
     * <p>방마다 채널을 나누면 구독자가 붙고 떨어질 때마다 채널을 열고 닫아야 해서 생명주기가
     * 하나 더 생긴다. 지금 규모에서 얻을 게 없다.
     */
    public static final String EVENT_CHANNEL = "upbid:sse:events";

    private static final String SEQUENCE_KEY_PREFIX = "upbid:sse:room:";
    private static final String SEQUENCE_KEY_SUFFIX = ":seq";

    /**
     * 방이 정상 종료되면 카운터를 지우지만, 그러지 못하고 죽은 방의 키가 영영 남지 않도록
     * 걸어 두는 상한. 경매는 이보다 훨씬 짧게 끝난다.
     */
    private static final Duration SEQUENCE_TTL = Duration.ofDays(7);

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
            // 실시간 전달이 안 되는 것과 입찰·마감이 실패하는 것은 다른 일이다. 이 메서드는
            // 트랜잭션이 커밋된 뒤에 불리므로, 여기서 예외를 올리면 DB에 이미 남은 입찰이
            // 클라이언트에게는 500으로 돌아간다. 상태의 원본은 DB이고 화면은 조회로 복구된다.
            log.error("sse 이벤트 발행 실패: roomId={}, eventName={}", roomId, eventName, e);
        }
    }

    /**
     * 방이 닫힐 때 순차 ID 카운터를 지운다. 모든 인스턴스가 방 종료를 수신해 각자 부르지만
     * {@code DEL}은 여러 번 불려도 같은 결과다.
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

        // 첫 이벤트일 때만 만료를 건다. 매번 걸면 이벤트마다 왕복이 하나씩 더 붙는다.
        if (id == 1L) {
            stringRedisTemplate.expire(key, SEQUENCE_TTL);
        }

        return id;
    }

    private String sequenceKey(Long roomId) {
        return SEQUENCE_KEY_PREFIX + roomId + SEQUENCE_KEY_SUFFIX;
    }
}
