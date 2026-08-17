package com.hot6ix.upbid.domain.sse.service;

/**
 * emitter 하나에 전달할 SSE 작업 단위.
 *
 * <p>실제 이벤트({@link Event}), 연결 유지용 heartbeat({@link Heartbeat}), 참여자 수
 * 알림({@link ParticipantCount})을 구분한다. subscriber에서 {@code switch}로 분기하여 처리한다.
 */
public sealed interface SseDispatchTask
        permits SseDispatchTask.Event, SseDispatchTask.Heartbeat, SseDispatchTask.ParticipantCount {

    Long roomId();

    /**
     * 클라이언트로 보낼 SSE 이벤트.
     *
     * @param roomId 에러 발생 시 {@code unregister}에 사용
     * @param name   SSE 이벤트 이름
     * @param id     방별 순차 ID. 클라이언트가 {@code Last-Event-ID}로 저장한다.
     * @param data   클라이언트로 보낼 DTO
     */
    record Event(Long roomId, String name, long id, Object data)
            implements SseDispatchTask {}

    /**
     * 프록시·로드밸런서의 idle timeout을 막기 위한 keep-alive.
     * 버퍼 ID가 없으므로 {@code Last-Event-ID}에 영향을 주지 않는다.
     */
    record Heartbeat(Long roomId)
            implements SseDispatchTask {}

    /**
     * 전역 집계된 참여자 수 알림(#311). {@link Event}와 달리 id 가 없다 — 재연결 replay
     * 버퍼에 안 쌓이는 이벤트라 순차 번호 자체가 없고, {@code Last-Event-ID}에 영향을
     * 주면 안 되기 때문이다.
     */
    record ParticipantCount(Long roomId, int count)
            implements SseDispatchTask {}
}
