package com.hot6ix.upbid.domain.sse.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * register-먼저·홀드 방식(#378)이 실제로 순서·중복·재연결 replay를 어떻게 처리하는지 본다.
 *
 * <p>{@code EmitterDispatcher}는 패키지 프라이빗이라 이 테스트가 직접 인스턴스를 만들어 부른다.
 * dispatch executor는 {@code Runnable::run}으로 줘서 {@code submit()} 호출이 그 자리에서
 * 동기적으로 끝나게 한다 — 그래야 이 테스트가 보는 "순서"가 실제 전송 순서와 일치한다.
 */
class EmitterDispatcherTest {

    private static final Long ROOM_ID = 1L;
    private static final long EMITTER_TIMEOUT_MS = 60 * 60 * 1000L;
    private static final int QUEUE_CAPACITY = 128;

    private final RecordingSseEmitter emitter = new RecordingSseEmitter();
    private final EmitterDispatcher dispatcher = new EmitterDispatcher(
            Runnable::run, QUEUE_CAPACITY, ROOM_ID, emitter, new SseMetrics(new SimpleMeterRegistry()));

    private static SseDispatchTask.Event event(long id) {
        return new SseDispatchTask.Event(ROOM_ID, "BID_PLACED", id, "payload-" + id);
    }

    @Test
    @DisplayName("생성 직후(준비 중 상태)에 들어온 Event는 즉시 전송되지 않는다")
    void holdsEventBeforeReady() {

        dispatcher.enqueue(event(5L));

        assertThat(emitter.deliveredEventIds).isEmpty();
    }

    @Test
    @DisplayName("becomeReady는 replay 이벤트를 그 사이 붙잡힌 라이브 이벤트보다 먼저 내보낸다")
    void sendsReplayBeforeHeldLiveEvents() {

        dispatcher.enqueue(event(12L));               // 아직 준비 중 -> 붙잡힘

        dispatcher.becomeReady(List.of(event(11L)));   // replay(11) 먼저, 그 다음 붙잡힌 라이브(12)

        assertThat(emitter.deliveredEventIds).containsExactly(11L, 12L);
    }

    @Test
    @DisplayName("붙잡힌 라이브 이벤트가 replay 목록에 이미 포함돼 있으면 중복 전송하지 않는다")
    void skipsHeldLiveEventAlreadyCoveredByReplay() {

        dispatcher.enqueue(event(11L));                // 아직 준비 중 -> 붙잡힘

        dispatcher.becomeReady(List.of(event(11L)));    // replay에도 같은 11이 있음

        assertThat(emitter.deliveredEventIds).containsExactly(11L);
    }

    @Test
    @DisplayName("준비 완료 이후에 들어온 Event는 즉시 전송된다")
    void sendsEventImmediatelyOnceReady() {

        dispatcher.becomeReady(List.of());

        dispatcher.enqueue(event(20L));

        assertThat(emitter.deliveredEventIds).containsExactly(20L);
    }

    @Test
    @DisplayName("Heartbeat는 준비 중 상태에서도 즉시 전송된다")
    void sendsHeartbeatEvenWhileNotReady() {

        dispatcher.enqueue(new SseDispatchTask.Heartbeat(ROOM_ID));

        assertThat(emitter.totalSendCount)
                .as("아직 준비 중이라도 heartbeat는 붙잡히지 않고 바로 나가야 한다")
                .isEqualTo(1);
        assertThat(emitter.deliveredEventIds)
                .as("heartbeat는 id가 없어 replay 대상 목록엔 안 잡힌다")
                .isEmpty();
    }

    @Test
    @DisplayName("ParticipantCount는 준비 중 상태에서도 즉시 전송된다")
    void sendsParticipantCountEvenWhileNotReady() {

        dispatcher.enqueue(new SseDispatchTask.ParticipantCount(ROOM_ID, 3));

        assertThat(emitter.totalSendCount).isEqualTo(1);
        assertThat(emitter.deliveredEventIds).isEmpty();
    }

    @Test
    @DisplayName("아직 Event를 하나도 못 받았으면 lastDeliveredEventId는 -1이다")
    void startsWithNoLastDeliveredEventId() {

        assertThat(dispatcher.lastDeliveredEventId()).isEqualTo(-1L);
    }

    @Test
    @DisplayName("Event가 전송될 때마다 lastDeliveredEventId가 그 id로 갱신된다")
    void updatesLastDeliveredEventIdOnEachEvent() {

        dispatcher.becomeReady(List.of());

        dispatcher.enqueue(event(20L));
        assertThat(dispatcher.lastDeliveredEventId()).isEqualTo(20L);

        dispatcher.enqueue(event(21L));
        assertThat(dispatcher.lastDeliveredEventId()).isEqualTo(21L);
    }

    @Test
    @DisplayName("Heartbeat/ParticipantCount 전송은 lastDeliveredEventId를 바꾸지 않는다")
    void doesNotUpdateLastDeliveredEventIdForNonEventTasks() {

        dispatcher.becomeReady(List.of());

        dispatcher.enqueue(new SseDispatchTask.Heartbeat(ROOM_ID));
        dispatcher.enqueue(new SseDispatchTask.ParticipantCount(ROOM_ID, 5));

        assertThat(dispatcher.lastDeliveredEventId()).isEqualTo(-1L);
    }

    @Test
    @DisplayName("pauseForReplay 이후 들어온 라이브 이벤트는 다시 붙잡혔다가, becomeReady로 재개하면 replay 다음에 순서대로 나간다")
    void resumesInOrderAfterReconnectReplay() {

        dispatcher.becomeReady(List.of(event(5L)));    // 최초 연결: 5까지 정상 수신
        assertThat(dispatcher.lastDeliveredEventId()).isEqualTo(5L);

        dispatcher.pauseForReplay();                   // 재연결 감지: 다시 준비 중 상태로
        dispatcher.enqueue(event(8L));                 // 그 사이 들어온 라이브 -> 붙잡힘

        dispatcher.becomeReady(List.of(event(6L), event(7L)));   // 놓친 6, 7을 replay

        assertThat(emitter.deliveredEventIds).containsExactly(5L, 6L, 7L, 8L);
    }

    /**
     * 실제 {@code SseEmitter.send(SseEventBuilder)}가 만드는
     * {@code Set<DataWithMediaType>}에서 헤더 문자열("id:{n}\nevent:{name}\ndata:")을 찾아
     * id만 뽑아낸다. data 페이로드 엔트리는 원본 객체 그대로 담겨 있어 문자열이 아닐 수도
     * 있으므로, 문자열이면서 "id:" 패턴이 매치되는 엔트리만 본다.
     */
    private static class RecordingSseEmitter extends SseEmitter {

        private static final Pattern ID_PATTERN = Pattern.compile("id:(\\d+)");

        final List<Long> deliveredEventIds = new ArrayList<>();
        int totalSendCount = 0;

        @Override
        public synchronized void onCompletion(Runnable callback) {
        }

        @Override
        public synchronized void onError(Consumer<Throwable> callback) {
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            totalSendCount++;
            for (ResponseBodyEmitter.DataWithMediaType entry : builder.build()) {
                if (entry.getData() instanceof String text) {
                    Matcher matcher = ID_PATTERN.matcher(text);
                    if (matcher.find()) {
                        deliveredEventIds.add(Long.parseLong(matcher.group(1)));
                    }
                }
            }
        }
    }
}
