package com.hot6ix.upbid.domain.sse.service;

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
     */
    void enqueue(SseDispatchTask task) {
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
        publisher.close();

        CompletableFuture.delayedExecutor(graceMs, TimeUnit.MILLISECONDS, vtExecutor)
                .execute(this::forceCompleteIfStillOpen);
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
