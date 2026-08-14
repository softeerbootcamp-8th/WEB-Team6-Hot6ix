package com.hot6ix.upbid.domain.sse.service;

import java.io.IOException;
import java.util.concurrent.Flow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * emitter 하나에 이벤트를 순차로 전달하는 구독자.
 *
 * <p>{@link SubmissionPublisher}가 drain을 1개만 보장하므로 동일 emitter에 동시 전송이
 * 일어나지 않는다. 전송 실패 시 {@link SseEmitter#completeWithError}를 호출해 emitter
 * 생명주기 콜백({@code onError})이 정리를 이어받게 한다. 이 클래스가 {@code RoomSseManager}를
 * 직접 참조하지 않는 이유다.
 */
@Slf4j
class EmitterSubscriber implements Flow.Subscriber<SseDispatchTask> {

    private final Long roomId;
    private final SseEmitter emitter;

    private Flow.Subscription subscription;

    EmitterSubscriber(Long roomId, SseEmitter emitter) {
        this.roomId = roomId;
        this.emitter = emitter;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(1);
    }

    @Override
    public void onNext(SseDispatchTask task) {
        try {
            send(task);
        } catch (IOException | IllegalStateException e) {
            log.debug("sse 전송 중 끊긴 연결 정리: roomId={}, cause={}", roomId, cause(e));
            subscription.cancel();
            completeWithError(e);
            return;
        }
        subscription.request(1);
    }

    @Override
    public void onError(Throwable throwable) {
        log.debug("sse emitter dispatcher 종료: roomId={}", roomId);
    }

    @Override
    public void onComplete() {
        log.debug("sse emitter dispatcher 완료: roomId={}", roomId);
    }

    private void send(SseDispatchTask task) throws IOException {
        switch (task) {
            case SseDispatchTask.Event e ->
                    emitter.send(SseEmitter.event()
                            .id(String.valueOf(e.id()))
                            .name(e.name())
                            .data(e.data()));
            case SseDispatchTask.Heartbeat h ->
                    emitter.send(SseEmitter.event().comment("keep-alive"));
        }
    }

    /**
     * emitter 생명주기 콜백({@code onError → disconnect → unregister})을 통해
     * 정리가 이어지도록 한다. 이미 완료된 emitter 에 호출해도 터지지 않게 삼킨다.
     */
    private void completeWithError(Exception e) {
        try {
            emitter.completeWithError(e);
        } catch (IllegalStateException ignored) {
        }
    }

    private String cause(Exception e) {
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }
}
