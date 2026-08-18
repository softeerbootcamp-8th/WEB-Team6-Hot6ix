package com.hot6ix.upbid.domain.sse.service;

import com.hot6ix.upbid.domain.sse.dto.SseEventsLostDto;
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
     * 최대한 돌려주고, <b>메우지 못한 구간이 있으면 그 사실을 함께 돌려준다.</b> 받는 쪽은 그때
     * 클라이언트에게 상태를 다시 조회하라고 알린다 — 로그만 남기면 화면은 낡은 채로 남는다.
     *
     * <p>범위 질의({@code ZRANGEBYSCORE}) 대신 전체를 읽고 거른다. 유실 판정이 어차피 버퍼의
     * 시작 ID 를 알아야 해서 질의를 두 번 하게 되는데, 최대 N개(기본 50)를 한 번에 읽는 편이 싸다.
     */
    public ReplayResult getEventsAfter(Long roomId, long lastEventId) {
        List<BufferedEvent> buffered = getAllEvents(roomId);

        if (buffered.isEmpty()) {
            return emptyBufferResult(roomId, lastEventId);
        }

        List<BufferedEvent> replayable = buffered.stream()
                .filter(event -> event.id() > lastEventId)
                .toList();

        String lostReason = detectLoss(roomId, lastEventId, buffered);

        return lostReason == null
                ? ReplayResult.intact(replayable)
                : ReplayResult.lost(replayable, lostReason);
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

    /**
     * 버퍼가 비어 있는데 클라이언트가 ID를 들고 온 상황.
     *
     * <p><b>유실로 본다.</b> {@code Last-Event-ID}는 ID가 달린 이벤트를 실제로 받은 뒤에만
     * 나가므로, 이벤트가 한 건도 없던 방이 이 분기로 들어올 수는 없다. 즉 버퍼가 있었는데
     * 사라진 것이다 — Redis 재시작·failover·TTL 만료가 여기다.
     *
     * <p>예전에는 여기서 빈 목록만 돌려주고 판정 자체를 건너뛰었다. 유실 규모가 가장 큰
     * 경우인데 로그 한 줄도 남지 않았다.
     */
    private ReplayResult emptyBufferResult(Long roomId, long lastEventId) {
        // 받은 이벤트가 없다고 들고 온 경우는 놓친 것도 없다. 실제 브라우저는 ID 가 붙은
        // 이벤트를 받은 뒤에만 헤더를 보내므로 여기 오지 않지만, 0 을 유실로 세면
        // 이벤트가 한 건도 없는 방에 붙는 것만으로 재조회가 돈다.
        if (lastEventId <= 0) {
            return ReplayResult.intact(List.of());
        }

        log.warn("sse 이벤트 유실(버퍼 없음): roomId={}, lastEventId={}", roomId, lastEventId);

        return ReplayResult.lost(List.of(), SseEventsLostDto.BUFFER_MISSING);
    }

    /**
     * 버퍼에 남은 구간과 클라이언트가 든 ID를 견줘 메우지 못한 구간을 판정한다.
     *
     * <p>두 방향을 본다.
     * <ul>
     *   <li>ID가 버퍼 시작보다 <b>뒤처진</b> 경우: 끊긴 사이 이벤트가 버퍼 크기를 넘겨
     *       오래된 것부터 밀려났다. 몇 개가 사라졌는지는 계산되므로 로그에 남긴다.
     *   <li>ID가 버퍼 끝보다 <b>앞선</b> 경우: 순차 ID 카운터가 되감겼다(Redis 초기화).
     *       이때 남은 이벤트는 전부 ID가 작아 필터에 걸러지므로, 판정하지 않으면 클라이언트가
     *       아무것도 못 받은 채 조용히 낡는다.
     * </ul>
     */
    private String detectLoss(Long roomId, long lastEventId, List<BufferedEvent> buffered) {
        long bufferStart = buffered.getFirst().id();
        long bufferEnd = buffered.getLast().id();

        if (lastEventId > bufferEnd) {
            log.warn("sse 이벤트 유실(ID 역행): roomId={}, lastEventId={}, bufferEnd={}",
                    roomId, lastEventId, bufferEnd);

            return SseEventsLostDto.SEQUENCE_RESET;
        }

        long lostCount = bufferStart - lastEventId - 1;

        if (lostCount <= 0) {
            return null;
        }

        log.warn("sse 이벤트 유실: roomId={}, lastEventId={}, bufferStart={}, 유실={}개",
                roomId, lastEventId, bufferStart, lostCount);

        return SseEventsLostDto.BUFFER_OVERFLOW;
    }
}
