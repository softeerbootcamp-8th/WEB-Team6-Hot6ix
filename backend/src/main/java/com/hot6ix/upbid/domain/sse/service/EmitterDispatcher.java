package com.hot6ix.upbid.domain.sse.service;

import java.util.concurrent.Executor;
import java.util.concurrent.SubmissionPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * emitter 하나에 대한 이벤트 큐와 drain을 관리한다.
 *
 * <p>{@link SubmissionPublisher}가 bounded 큐와 순차 drain(1개 보장)을 내장한다.
 * drain은 VT에서 실행되어 느린 구독자가 다른 emitter에 영향을 주지 않는다.
 *
 * <p>큐가 포화되면 emitter를 즉시 종료한다. heartbeat도 같은 큐를 타므로 포화 시
 * heartbeat로는 정리되지 않는다. 포화 즉시 끊으면 EventSource가 재연결해
 * Last-Event-ID 로 빠진 이벤트를 replay 받는다.
 */
@Slf4j
class EmitterDispatcher {

    private final SubmissionPublisher<SseDispatchTask> publisher;

    private final SseEmitter emitter;

    private final SseMetrics sseMetrics;

    EmitterDispatcher(Executor vtExecutor, int queueCapacity, Long roomId, SseEmitter emitter,
            SseMetrics sseMetrics) {
        this.emitter = emitter;
        this.sseMetrics = sseMetrics;
        this.publisher = new SubmissionPublisher<>(vtExecutor, queueCapacity);
        this.publisher.subscribe(new EmitterSubscriber(roomId, emitter));
    }

    /**
     * 이벤트를 큐에 넣는다. 즉시 반환하며 실제 전송은 VT에서 비동기로 일어난다.
     *
     * <p>publisher가 닫혀 있거나 큐가 포화된 경우 이벤트를 drop하고 emitter를 즉시 종료한다.
     * 큐 포화는 클라이언트가 이벤트를 전혀 소비하지 못하는 상태이므로 연결을 유지해도
     * 이후 이벤트가 계속 drop된다. 끊으면 EventSource가 재연결하고 Last-Event-ID로
     * 빠진 이벤트를 replay 받는다.
     */
    void enqueue(SseDispatchTask task) {
        if (publisher.isClosed()) {
            return;
        }
        publisher.offer(task, (subscriber, dropped) -> {
            log.warn("sse 큐 포화로 연결 종료: roomId={}, taskType={}",
                    dropped.roomId(), dropped.getClass().getSimpleName());
            sseMetrics.recordQueueSaturated();
            emitter.completeWithError(new IllegalStateException("emitter queue saturated"));
            return false;
        });
    }

    /**
     * 큐를 즉시 닫고 대기 중인 이벤트를 버린다.
     * emitter가 종료된 이후에는 전송할 이유가 없으므로 graceful drain을 하지 않는다.
     */
    void close() {
        publisher.closeExceptionally(new IllegalStateException("emitter closed"));
    }
}
