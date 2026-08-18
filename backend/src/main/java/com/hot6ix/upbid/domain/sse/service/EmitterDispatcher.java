package com.hot6ix.upbid.domain.sse.service;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

    private final Executor vtExecutor;

    private final Long roomId;

    /**
     * emitter 가 종료 상태에 도달했는지. {@link #closeAfterFlush(long)}의 폴백이 이미 끝난 일에
     * 대고 강제 종료 지표를 남기지 않게 하는 용도다.
     */
    private final AtomicBoolean terminated = new AtomicBoolean();

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
        this.vtExecutor = vtExecutor;
        this.roomId = roomId;
        this.publisher = new SubmissionPublisher<>(vtExecutor, queueCapacity);
        this.publisher.subscribe(
                new EmitterSubscriber(roomId, emitter, sseMetrics, () -> terminated.set(true)));
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
     * 이미 ready인 emitter를 다시 "준비 중" 상태로 되돌린다(#378). Redis pub/sub 재연결이
     * 감지돼 이 emitter가 그 사이 놓친 걸 다시 계산하려 할 때, {@code subscribe()} 최초
     * 호출 때의 {@code register()}~{@link #becomeReady} 구간과 같은 보호가 다시 필요하다 —
     * 그러지 않으면 놓친 목록을 조회하는 동안(Redis I/O) 들어오는 라이브 이벤트가 이번에
     * 다시 계산한 replay보다 먼저 나가 순서가 역전된다(3-E, 1번과 같은 형태의 문제).
     *
     * <p>호출 직후부터 {@link #becomeReady}를 부르기 전까지, 들어오는 라이브 이벤트는
     * {@link #enqueue}를 통해 다시 {@code pendingLiveEvents}에 붙잡힌다.
     */
    void pauseForReplay() {
        synchronized (pendingLiveEventsLock) {
            ready = false;
        }
    }

    /**
     * 재연결 시 놓친 이벤트(replay)를 먼저 내보내고, {@code register()} 이후 {@link #enqueue}가
     * 붙잡아 둔 라이브 이벤트를 이어서 내보낸 뒤 이 dispatcher를 준비 완료 상태로 전환한다.
     * {@code RoomSseManager.subscribe()}가 버퍼 조회 직후 딱 한 번 호출한다. 재연결 후 방
     * 단위 replay에서는 {@link #pauseForReplay}로 다시 준비 중 상태를 만든 뒤 재사용한다.
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

    /**
     * <b>큐에 남은 이벤트를 전송한 뒤</b> emitter 를 닫는다. 방 종료 경로가 쓴다.
     *
     * <p>{@code ROOM_CLOSED} 는 이 큐에 들어간 직후 같은 스레드에서 방 종료가 이어진다
     * ({@code SseEventSubscriber}). 그래서 {@link #close()}처럼 대기 항목을 버리면
     * <b>클라이언트는 종료 이벤트를 못 받고 연결만 끊긴 것으로 관찰한다.</b> 재구독은 종료된
     * 방이라 거절되고 replay 버퍼도 함께 지워져 복구 경로가 없다(#307).
     *
     * <p>{@code closeExceptionally}와 달리 {@code close()}는 남은 항목을 전달한 뒤
     * {@code onComplete}을 준다. emitter 를 닫는 것은 그 {@code onComplete}을 받은
     * {@link EmitterSubscriber}다 — 전송 중인 가상 스레드와 겹치지 않게 하려면 닫는 일도
     * 그쪽 순서에 있어야 한다.
     *
     * @param graceMs 이 시간이 지나도 전송이 끝나지 않으면 강제로 닫는다. 클라이언트 소켓이
     *                멈춰 있으면 {@code send}가 반환하지 않아 emitter 타임아웃(기본 1시간)까지
     *                남기 때문이다.
     */
    void closeAfterFlush(long graceMs) {
        flushPendingLiveEvents();
        publisher.close();

        CompletableFuture.delayedExecutor(graceMs, TimeUnit.MILLISECONDS, vtExecutor)
                .execute(this::forceCompleteIfStillOpen);
    }

    /**
     * 준비 중(warmup)이라 붙잡아 둔 이벤트를 큐로 넘긴다(#378 + #307).
     *
     * <p>구독이 진행되는 동안 방이 닫히면 {@code ROOM_CLOSED}가 {@code pendingLiveEvents}에
     * 들어간 상태다. 이건 publisher 큐 밖이라 {@code publisher.close()}로는 흘러나가지 않는다.
     * 그대로 닫으면 종료 이벤트를 버리는 것과 같아진다.
     *
     * <p>{@code ready}로 바꿔 둔다. 이후 들어오는 이벤트는 붙잡지 않고 닫힌 publisher 에서
     * {@code dispatcher_closed}로 처리돼, 나가지도 못하는 큐에 계속 쌓이지 않는다.
     */
    private void flushPendingLiveEvents() {
        synchronized (pendingLiveEventsLock) {
            SseDispatchTask queued;
            while ((queued = pendingLiveEvents.poll()) != null) {
                submit(queued);
            }
            ready = true;
        }
    }

    /**
     * 유예 시간이 지나도 안 닫힌 emitter 를 닫는다. 여기까지 온 연결은 종료 이벤트를 받지
     * 못했으므로 지표로 남긴다 — 조용히 버려지면 모니터링에서 정상 종료와 구분되지 않는다.
     */
    private void forceCompleteIfStillOpen() {
        if (terminated.get()) {
            return;
        }

        log.warn("sse 종료 이벤트 전송이 유예 시간 안에 끝나지 않아 강제 종료: roomId={}", roomId);
        sseMetrics.recordRejected("close_flush_timeout");

        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
            // 폴백과 정상 종료가 겹칠 수 있다. 이미 닫혔으면 할 일이 없다.
        }
    }

    static final class QueueSaturatedException extends IllegalStateException {

        QueueSaturatedException() {
            super("emitter queue saturated");
        }
    }
}
