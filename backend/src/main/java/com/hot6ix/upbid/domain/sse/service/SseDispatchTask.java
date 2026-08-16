package com.hot6ix.upbid.domain.sse.service;

/**
 * emitter 하나에 전달할 SSE 작업 단위.
 *
 * <p>실제 이벤트({@link Event})와 연결 유지용 heartbeat({@link Heartbeat})를 구분한다.
 * subscriber에서 {@code switch}로 분기하여 처리한다.
 */
public sealed interface SseDispatchTask
        permits SseDispatchTask.Event, SseDispatchTask.Heartbeat {

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
}
