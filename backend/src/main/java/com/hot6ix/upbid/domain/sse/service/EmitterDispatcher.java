package com.hot6ix.upbid.domain.sse.service;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.SubmissionPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * emitter 하나에 대한 이벤트 큐와 drain을 관리한다.
 *
 * <p>{@link SubmissionPublisher}가 bounded 큐와 순차 drain(1개 보장)을 내장한다.
 * drain은 설정에 따라 가상 스레드 또는 고정 플랫폼 스레드 풀에서 실행된다.
 *
 * <p>큐가 포화되면 emitter를 즉시 종료한다. heartbeat도 같은 큐를 타므로 포화 시
 * heartbeat로는 정리되지 않는다. 포화 즉시 끊으면 EventSource가 재연결해
 * Last-Event-ID 로 빠진 이벤트를 replay 받는다.
 *
 * <p><b>생성 직후에는 "준비 중(warmup)" 상태다(#378).</b> {@code RoomSseManager.register()}가
 * 이 dispatcher를 만드는 순간 이 emitter는 라이브 이벤트를 받을 자격이 생기지만,
 * {@link #becomeReady}가 불리기 전까지 {@link #enqueue}로 들어온 이벤트는 곧바로 전송하지
 * 않고 {@code pendingLiveEvents}에 붙잡아 둔다. 재연결 시 버퍼에서 놓친 이벤트를 조회하는 동안
 * (Redis I/O) 들어오는 라이브 이벤트가 그 조회 결과보다 먼저 나가 순서가 뒤바뀌는 것을
 * 막기 위해서다. {@code becomeReady}가 "놓친 이벤트 → 그 사이 라이브 이벤트(중복 제거)"
 * 순서로 내보낸 뒤에야 준비 완료로 전환한다.
 */
@Slf4j
class EmitterDispatcher {

    private final SubmissionPublisher<QueuedSseDispatchTask> publisher;

    private final SseEmitter emitter;

    private final SseMetrics sseMetrics;

    private final Object pendingLiveEventsLock = new Object();
    private boolean ready = false;
    private final Queue<SseDispatchTask> pendingLiveEvents = new ArrayDeque<>();

    /**
     * 이 emitter에 마지막으로 제출한 {@code Event}의 id(#378). 재연결 시 방 단위로 놓친
     * 이벤트를 replay할 때, 방 전체가 아니라 이 emitter가 실제로 어디까지 받았는지를
     * 기준으로 삼기 위한 것이다 — 개별 SSE 재연결로 이미 캐치업한 emitter에게 중복
     * replay를 보내지 않으려면 방 단위 값 하나로는 부족하다.
     *
     * <p>여러 스레드(라이브 전달을 부르는 dispatch 스레드, replay를 부르는 구독 스레드)가
     * 시차를 두고 쓰고, 나중에 재연결 감지 스레드가 읽으므로 {@code volatile}로 가시성만
     * 보장한다 — 갱신 자체는 {@link #submit}에서 한 스레드씩 순서대로 일어나 경쟁이 없다.
     */
    private volatile long lastDeliveredEventId = -1;

    EmitterDispatcher(Executor vtExecutor, int queueCapacity, Long roomId, SseEmitter emitter,
            SseMetrics sseMetrics) {
        this.emitter = emitter;
        this.sseMetrics = sseMetrics;
        this.publisher = new SubmissionPublisher<>(vtExecutor, queueCapacity);
        this.publisher.subscribe(new EmitterSubscriber(roomId, emitter, sseMetrics));
    }

    /**
     * 이벤트를 큐에 넣는다. 즉시 반환하며 실제 전송은 설정된 executor에서 비동기로 일어난다.
     *
     * <p>publisher가 닫혀 있거나 큐가 포화된 경우 이벤트를 drop하고 emitter를 즉시 종료한다.
     * 큐 포화는 클라이언트가 이벤트를 전혀 소비하지 못하는 상태이므로 연결을 유지해도
     * 이후 이벤트가 계속 drop된다. 끊으면 EventSource가 재연결하고 Last-Event-ID로
     * 빠진 이벤트를 replay 받는다.
     *
     * <p>{@code Event}만 warmup 대상이다. {@code Heartbeat}/{@code ParticipantCount}는 id가
     * 없어 replay와 순서·중복을 따질 대상이 아니고, 오히려 붙잡아 두면 손해다 — heartbeat는
     * 버퍼 조회가 느려지는 바로 그 순간에 프록시 idle timeout을 막아야 하는데, warmup 큐에
     * 갇히면 그 타이밍에 못 나간다. 그래서 이 둘은 {@code ready} 여부와 무관하게 즉시 제출한다.
     */
    void enqueue(SseDispatchTask task) {
        if (!(task instanceof SseDispatchTask.Event)) {
            submit(task);
            return;
        }

        synchronized (pendingLiveEventsLock) {
            if (!ready) {
                pendingLiveEvents.add(task);
                return;
            }
        }
        submit(task);
    }

    /**
     * 재연결 시 놓친 이벤트(replay)를 먼저 내보내고, {@code register()} 이후 {@link #enqueue}가
     * 붙잡아 둔 라이브 이벤트를 이어서 내보낸 뒤 이 dispatcher를 준비 완료 상태로 전환한다.
     * {@code RoomSseManager.subscribe()}가 버퍼 조회 직후 딱 한 번 호출한다.
     *
     * <p>드레인과 상태 전환을 하나의 임계 구역 안에서 처리한다 — 그러지 않으면 드레인 직후,
     * {@code ready}로 바뀌기 직전에 들어온 이벤트가 {@code pendingLiveEvents}에 남아 영영 안 나갈
     * 수 있다.
     *
     * <p>{@code replayEvents}에 이미 포함된 id는 {@code pendingLiveEvents}에서 걸러낸다. 버퍼 조회와
     * 그 사이의 {@link #enqueue} 호출이 같은 이벤트를 각각 다른 경로로 잡을 수 있어서다.
     */
    void becomeReady(List<SseDispatchTask> replayEvents) {
        for (SseDispatchTask task : replayEvents) {
            submit(task);
        }

        long lastReplayEventId = lastEventId(replayEvents);

        synchronized (pendingLiveEventsLock) {
            SseDispatchTask queued;
            while ((queued = pendingLiveEvents.poll()) != null) {
                if (!isCoveredByReplayEvents(queued, lastReplayEventId)) {
                    submit(queued);
                }
            }
            ready = true;
        }
    }

    private static long lastEventId(List<SseDispatchTask> replayEvents) {
        if (replayEvents.isEmpty()) {
            return Long.MIN_VALUE;
        }
        return replayEvents.get(replayEvents.size() - 1) instanceof SseDispatchTask.Event event
                ? event.id()
                : Long.MIN_VALUE;
    }

    private static boolean isCoveredByReplayEvents(SseDispatchTask task, long lastReplayEventId) {
        return task instanceof SseDispatchTask.Event event && event.id() <= lastReplayEventId;
    }

    /**
     * publisher가 닫혀 있거나 큐가 포화된 경우 이벤트를 drop하고 emitter를 즉시 종료한다.
     * 큐 포화는 클라이언트가 이벤트를 전혀 소비하지 못하는 상태이므로 연결을 유지해도
     * 이후 이벤트가 계속 drop된다. 끊으면 EventSource가 재연결하고 Last-Event-ID로
     * 빠진 이벤트를 replay 받는다.
     *
     * <p>{@code Event}가 지나갈 때마다 {@link #lastDeliveredEventId}를 갱신한다(#378). 라이브
     * 전달({@code enqueue}의 fast path)과 replay 전달({@code becomeReady})이 모두 이 메서드
     * 하나를 거치므로, 두 경로를 따로 추적할 필요 없이 여기 한 곳에서만 갱신하면 된다. 이
     * emitter에 대한 {@code Event} 제출은 항상 id 오름차순이라(레이스 없이 replay → 라이브
     * 순서로 나가도록 이미 보장돼 있음) 비교 없이 덮어써도 된다.
     */
    private void submit(SseDispatchTask task) {
        if (task instanceof SseDispatchTask.Event event) {
            lastDeliveredEventId = event.id();
        }
        if (publisher.isClosed()) {
            sseMetrics.recordRejected("dispatcher_closed");
            return;
        }
        try {
            publisher.offer(QueuedSseDispatchTask.now(task), (subscriber, dropped) -> {
                log.warn("sse 큐 포화로 연결 종료: roomId={}, taskType={}",
                        dropped.task().roomId(), dropped.task().getClass().getSimpleName());
                sseMetrics.recordQueueSaturated();
                try {
                    emitter.completeWithError(new QueueSaturatedException());
                } catch (IllegalStateException ignored) {
                    // 완료 콜백과 경합해 이미 닫힌 emitter일 수 있다. 포화 지표는 위에서 남겼다.
                }
                return false;
            });
        } catch (IllegalStateException e) {
            // isClosed 확인 직후 다른 스레드가 close할 수 있다. 이 경우도 제출되지 않은 작업이다.
            sseMetrics.recordRejected("dispatcher_closed");
        }
    }

    long estimatedQueueDepth() {
        return publisher.estimateMaximumLag();
    }

    /**
     * 이 emitter가 마지막으로 받은 {@code Event}의 id. 아직 하나도 못 받았으면 {@code -1}.
     * 재연결 시 방 단위 replay가 emitter마다 어디서부터 다시 보낼지 정하는 데 쓴다(#378).
     */
    long lastDeliveredEventId() {
        return lastDeliveredEventId;
    }

    /**
     * 큐를 즉시 닫고 대기 중인 이벤트를 버린다.
     * emitter가 종료된 이후에는 전송할 이유가 없으므로 graceful drain을 하지 않는다.
     */
    void close() {
        publisher.closeExceptionally(new IllegalStateException("emitter closed"));
    }

    static final class QueueSaturatedException extends IllegalStateException {

        QueueSaturatedException() {
            super("emitter queue saturated");
        }
    }
}
