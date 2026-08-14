package com.hot6ix.upbid.domain.sse.service;

import com.hot6ix.upbid.domain.sse.event.SseEventEnvelope;
import com.hot6ix.upbid.domain.sse.event.SseEventMessage;
import com.hot6ix.upbid.domain.sse.event.SseRedisKeys;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 방별 SSE 이벤트를 최대 N개 보관하는 replay 버퍼. 재연결한 클라이언트가 {@code Last-Event-ID}를
 * 보내면 그 이후 이벤트를 돌려준다.
 *
 * <p><b>이 클래스는 읽기만 한다.</b> 쓰기는 발행 스크립트가 한다. 예전에는 채널을 받은 인스턴스가
 * 각자 자기 버퍼에 같은 이벤트를 저장했는데, 버퍼가 Redis 로 나온 지금 그렇게 하면 N대가 같은
 * 값을 N번 덮어쓰게 된다.
 *
 * <p>저장소는 방마다 하나씩인 ZSET 이고 score 가 이벤트 ID 다. <b>ZSET 은 삽입 순서가 아니라
 * score 순으로 읽히므로, 버퍼에 들어간 순서가 어떻든 항상 ID 오름차순으로 나온다.</b> 인메모리
 * 큐를 쓸 때 필요했던 "도착 순서 = ID 순서" 가정이 없어졌다.
 *
 * <p>버퍼가 인스턴스 밖에 있으므로 <b>이벤트를 발행하지 않은 인스턴스, 방금 뜬 인스턴스도
 * replay 를 할 수 있다.</b> 이것이 이 버퍼를 Redis 로 옮긴 이유다.
 *
 * <p>Redis 가 죽으면 replay 도 최근 이벤트 조회도 빈 결과가 된다. 그때는 실시간 전달도 이미
 * 멈춘 상태라 연결 자체를 실패시키는 대신, 구독은 살려 두고 Redis 가 돌아오면 그 연결로
 * 이벤트가 다시 흐르게 둔다. 화면이 놓치는 상태는 물품 조회 API 로 복구된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseEventBuffer {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * {@code lastEventId} 이후에 저장된 이벤트를 ID 순서대로 반환한다.
     *
     * <p>{@code lastEventId}가 버퍼에서 이미 밀려난 경우에도 자연스럽게 처리된다. 남아 있는 것을
     * 최대한 돌려주고, 유실 규모는 로그로 남겨 버퍼 크기 튜닝의 근거로 쓴다.
     *
     * <p>범위 질의({@code ZRANGEBYSCORE}) 대신 전체를 읽고 거른다. 유실 로그가 어차피 버퍼의
     * 시작 ID 를 알아야 해서 질의를 두 번 하게 되는데, 최대 N개(기본 50)를 한 번에 읽는 편이 싸다.
     */
    public List<BufferedEvent> getEventsAfter(Long roomId, long lastEventId) {
        List<BufferedEvent> buffered = getAllEvents(roomId);

        if (buffered.isEmpty()) {
            return List.of();
        }

        logIfLoss(roomId, lastEventId, buffered.getFirst().id());

        return buffered.stream()
                .filter(event -> event.id() > lastEventId)
                .toList();
    }

    /**
     * 버퍼에 남아있는 이벤트를 오래된 것부터 최신 순으로 전부 반환한다.
     */
    public List<BufferedEvent> getAllEvents(Long roomId) {
        try {
            // ZRANGE 는 score(=ID) 오름차순으로 준다. 저장된 순서와 무관하다.
            Set<String> raw = stringRedisTemplate.opsForZSet().range(SseRedisKeys.events(roomId), 0, -1);

            if (raw == null || raw.isEmpty()) {
                return List.of();
            }

            return raw.stream().map(this::toBufferedEvent).toList();

        } catch (RuntimeException e) {
            log.warn("sse 버퍼 조회 실패: roomId={}", roomId, e);
            return List.of();
        }
    }

    /**
     * 경매 종료 시 방의 버퍼와 순차 ID 카운터를 함께 지운다.
     *
     * <p>채널을 받은 모든 인스턴스가 각자 부르므로 같은 삭제가 여러 번 일어난다. {@code DEL}은
     * 멱등이라 문제가 없고, 한 인스턴스만 지우게 만들려면 그 인스턴스가 죽었을 때를 또 처리해야 한다.
     */
    public void clear(Long roomId) {
        try {
            stringRedisTemplate.delete(List.of(SseRedisKeys.events(roomId), SseRedisKeys.sequence(roomId)));
            log.debug("sse 버퍼 삭제: roomId={}", roomId);

        } catch (RuntimeException e) {
            log.warn("sse 버퍼 삭제 실패: roomId={}", roomId, e);
        }
    }

    private BufferedEvent toBufferedEvent(String raw) {
        SseEventEnvelope envelope = SseEventEnvelope.decode(raw, objectMapper);
        SseEventMessage message = envelope.message();

        return new BufferedEvent(
                envelope.id(), message.eventName(), message.data(), message.occurredAt());
    }

    private void logIfLoss(Long roomId, long lastEventId, long bufferStart) {
        if (lastEventId >= bufferStart) {
            return;
        }

        long lostCount = bufferStart - lastEventId - 1;

        log.warn("sse 이벤트 유실: roomId={}, lastEventId={}, bufferStart={}, 유실={}개",
                roomId, lastEventId, bufferStart, lostCount);
    }
}
