package com.hot6ix.upbid.domain.sse.service;

import com.hot6ix.upbid.domain.sse.dto.ParticipantCountDto;
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
class EmitterSubscriber implements Flow.Subscriber<QueuedSseDispatchTask> {

    private final Long roomId;
    private final SseEmitter emitter;
    private final SseMetrics sseMetrics;

    private Flow.Subscription subscription;

    EmitterSubscriber(Long roomId, SseEmitter emitter, SseMetrics sseMetrics) {
        this.roomId = roomId;
        this.emitter = emitter;
        this.sseMetrics = sseMetrics;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(1);
    }

    /**
     * <b>전송이 어떤 이유로 실패하든 연결을 정리한다.</b> 끊긴 연결({@code IOException} 등)만
     * 잡으면, 직렬화 실패 같은 다른 예외는 {@code SubmissionPublisher}가 구독을 취소하고
     * {@code onError}로 넘겨 버린다. 그러면 큐만 죽고 emitter 는 맵에 남아, 그 연결은
     * 타임아웃(1시간)까지 아무것도 못 받으면서 참여자 수에는 계속 잡힌다.
     *
     * <p>예외 종류로 로그 수준을 가른다. 끊긴 연결은 정상 종료라 {@code debug}, 나머지는
     * 코드 문제일 수 있어 {@code warn} 으로 남긴다.
     */
    @Override
    public void onNext(QueuedSseDispatchTask queuedTask) {
        SseDispatchTask task = queuedTask.task();
        String eventName = queuedTask.eventName();

        sseMetrics.recordQueueWait(eventName, queuedTask.enqueuedAtNanos());
        sseMetrics.recordSendAttempt(eventName);
        io.micrometer.core.instrument.Timer.Sample sample = sseMetrics.startSend();

        try {
            send(task);
            sseMetrics.recordSendSuccess(eventName);
        } catch (IOException | IllegalStateException e) {
            sseMetrics.recordSendFailure(eventName, e);
            log.debug("sse 전송 중 끊긴 연결 정리: roomId={}, cause={}", roomId, cause(e));
            abort(e);
            return;
        } catch (RuntimeException e) {
            sseMetrics.recordSendFailure(eventName, e);
            log.warn("sse 전송 실패로 연결 종료: roomId={}", roomId, e);
            abort(e);
            return;
        } finally {
            sseMetrics.finishSend(eventName, sample);
        }
        subscription.request(1);
    }

    /**
     * 큐 소비를 멈추고 emitter 를 종료시킨다. 둘 다 필요하다. {@code cancel()} 만 하면
     * emitter 가 맵에 남아 좀비가 되고, {@code completeWithError} 만 하면 죽은 emitter 에
     * 계속 전송을 시도한다.
     */
    private void abort(Exception e) {
        subscription.cancel();
        completeWithError(e);
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
            case SseDispatchTask.ParticipantCount p ->
                    emitter.send(SseEmitter.event()
                            .name(RoomSseManager.PARTICIPANT_COUNT_EVENT)
                            .data(new ParticipantCountDto(p.count())));
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
