package com.hot6ix.upbid.domain.sse.service;

/** emitter별 큐에 들어간 시각과 실제 SSE 작업을 함께 보관한다. */
record QueuedSseDispatchTask(SseDispatchTask task, long enqueuedAtNanos) {

    static QueuedSseDispatchTask now(SseDispatchTask task) {
        return new QueuedSseDispatchTask(task, System.nanoTime());
    }

    String eventName() {
        return switch (task) {
            case SseDispatchTask.Event event -> event.name();
            case SseDispatchTask.Heartbeat ignored -> SseMetrics.HEARTBEAT_EVENT;
            case SseDispatchTask.ParticipantCount ignored -> RoomSseManager.PARTICIPANT_COUNT_EVENT;
            case SseDispatchTask.EventsLost ignored -> RoomSseManager.EVENTS_LOST_EVENT;
        };
    }
}
