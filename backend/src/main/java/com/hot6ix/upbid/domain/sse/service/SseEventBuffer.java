package com.hot6ix.upbid.domain.sse.service;

import com.hot6ix.upbid.domain.sse.config.SseProperties;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 방별 SSE 이벤트를 최대 N개 보관하는 인메모리 버퍼.
 *
 * <p>재연결한 클라이언트가 {@code Last-Event-ID}를 보내면 그 이후 이벤트를 replay한다.
 * N개를 초과하면 오래된 것부터 밀어낸다 (Piggyback 방식).
 *
 * <p>이벤트 ID는 방별 순차 ID다. UUID와 달리 유실 규모를 로그로 파악할 수 있어
 * 운영 후 N을 데이터 기반으로 조정할 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(SseProperties.class)
public class SseEventBuffer {

    /**
     * 버퍼에 저장되는 이벤트 단위.
     *
     * @param id        방별 순차 ID. 클라이언트에게 {@code Last-Event-ID}로 전달된다.
     * @param eventName SSE 이벤트 이름 ({@code EventType} 이름)
     * @param data      클라이언트로 보낼 DTO
     */
    public record BufferedEvent(long id, String eventName, Object data) {
    }

    private final Map<Long, ArrayDeque<BufferedEvent>> buffers = new ConcurrentHashMap<>();
    private final Map<Long, AtomicLong> counters = new ConcurrentHashMap<>();

    private final SseProperties sseProperties;

    /**
     * 이벤트를 버퍼에 추가한다. N개를 초과하면 가장 오래된 것을 제거한다.
     *
     * @return 할당된 순차 ID (SSE {@code id} 필드로 클라이언트에게 전달해야 한다)
     */
    public long add(Long roomId, String eventName, Object data) {
        long id = nextId(roomId);
        BufferedEvent event = new BufferedEvent(id, eventName, data);

        ArrayDeque<BufferedEvent> queue =
                buffers.computeIfAbsent(roomId, k -> new ArrayDeque<>());

        synchronized (queue) {
            queue.addLast(event);
            if (queue.size() > sseProperties.bufferSize()) {
                queue.pollFirst();
            }
        }

        return id;
    }

    /**
     * {@code lastEventId} 이후에 저장된 이벤트를 순서대로 반환한다.
     *
     * <p>{@code lastEventId}가 버퍼에서 이미 밀려난 경우에도 자연스럽게 처리된다.
     *
     * <pre>
     * 버퍼: [id=30 ... id=50]  (1~29는 밀려남)
     * lastEventId=10 → dropWhile(id <= 10) → 버퍼 전체 반환
     * </pre>
     *
     * 유실 규모는 로그로 기록한다. N 튜닝의 근거로 활용한다.
     */
    public List<BufferedEvent> getEventsAfter(Long roomId, long lastEventId) {
        ArrayDeque<BufferedEvent> queue = buffers.get(roomId);
        if (queue == null) {
            return List.of();
        }

        synchronized (queue) {
            List<BufferedEvent> result = queue.stream()
                    .dropWhile(e -> e.id() <= lastEventId)
                    .toList();

            logIfLoss(roomId, lastEventId, queue, result);

            return result;
        }
    }

    /**
     * 경매 종료 시 방 버퍼와 ID 카운터를 삭제한다.
     */
    public void clear(Long roomId) {
        buffers.remove(roomId);
        counters.remove(roomId);
        log.debug("sse 버퍼 삭제: roomId={}", roomId);
    }

    private long nextId(Long roomId) {
        return counters
                .computeIfAbsent(roomId, k -> new AtomicLong(0))
                .incrementAndGet();
    }

    private void logIfLoss(Long roomId, long lastEventId,
                           ArrayDeque<BufferedEvent> queue, List<BufferedEvent> result) {
        if (queue.isEmpty()) {
            return;
        }

        long bufferStart = queue.peekFirst().id();
        boolean isLoss = lastEventId < bufferStart;

        if (isLoss) {
            long lostCount = bufferStart - lastEventId - 1;
            log.warn("sse 이벤트 유실: roomId={}, lastEventId={}, bufferStart={}, 유실={}개",
                    roomId, lastEventId, bufferStart, lostCount);
        }
    }
}
