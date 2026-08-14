package com.hot6ix.upbid.domain.sse.service;

import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SubmissionPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * emitter 하나에 대한 이벤트 큐와 drain을 관리한다.
 *
 * <p>{@link SubmissionPublisher}가 bounded 큐와 순차 drain(1개 보장)을 내장한다.
 * drain은 VT에서 실행되어 느린 구독자가 다른 emitter에 영향을 주지 않는다.
 *
 * <p>큐가 포화되면 이벤트를 drop하고 경고 로그를 남긴다. 포화 상태는 이미 연결이
 * 죽어가는 신호이며, 해당 emitter는 곧 heartbeat 실패로 정리된다.
 */
@Slf4j
class EmitterDispatcher {

    private final SubmissionPublisher<SseDispatchTask> publisher;

    EmitterDispatcher(Executor vtExecutor, Semaphore semaphore, int queueCapacity,
                      Long roomId, SseEmitter emitter) {
        this.publisher = new SubmissionPublisher<>(vtExecutor, queueCapacity);
        this.publisher.subscribe(new EmitterSubscriber(semaphore, roomId, emitter));
    }

    /**
     * 이벤트를 큐에 넣는다. 즉시 반환하며 실제 전송은 VT에서 비동기로 일어난다.
     *
     * <p>publisher가 닫혀 있거나 큐가 포화된 경우 이벤트를 drop한다.
     */
    void enqueue(SseDispatchTask task) {
        if (publisher.isClosed()) {
            return;
        }
        publisher.offer(task, (subscriber, dropped) -> {
            log.warn("sse 큐 포화 drop: roomId={}, taskType={}",
                    dropped.roomId(), dropped.getClass().getSimpleName());
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
