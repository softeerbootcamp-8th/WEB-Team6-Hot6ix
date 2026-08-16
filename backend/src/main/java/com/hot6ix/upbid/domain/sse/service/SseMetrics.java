package com.hot6ix.upbid.domain.sse.service;

import com.hot6ix.upbid.global.event.EventType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 계측만 아는 객체. 분리한 이유는 {@code BidMetrics}와 같다.
 */
@Component
public class SseMetrics {

    private static final String BROADCAST = "upbid.sse.broadcast";
    private static final String FANOUT = "upbid.sse.fanout";
    private static final String EVENTS_PUBLISHED = "upbid.sse.events.published";
    private static final String SEND_ATTEMPTS = "upbid.sse.send.attempts";
    private static final String SEND_SUCCESSES = "upbid.sse.send.successes";
    private static final String SEND_FAILURES = "upbid.sse.send.failures";
    private static final String SEND_DURATION = "upbid.sse.send";
    private static final String QUEUE_WAIT = "upbid.sse.queue.wait";
    private static final String CONNECTIONS_CLOSED = "upbid.sse.connections.closed";
    private static final String REJECTED = "upbid.sse.rejected";
    private static final Set<String> SEND_FAILURE_CAUSES = Set.of("io", "illegal_state", "runtime");

    static final String HEARTBEAT_EVENT = "HEARTBEAT";
    static final String CLOSE_COMPLETED = "completed";
    static final String CLOSE_TIMEOUT = "timeout";
    static final String CLOSE_SEND_ERROR = "send_error";
    static final String CLOSE_QUEUE_SATURATED = "queue_saturated";
    static final String CLOSE_ROOM_CLOSED = "room_closed";

    private final MeterRegistry registry;

    /** heartbeat 한 바퀴. 접속 수가 늘 때 이 시간이 어떻게 늘어나는지를 본다. */
    private final Timer heartbeat;

    /** 발행이 실패해 사라진 이벤트 수. */
    private final Counter publishFailures;

    /**
     * emitter 별 큐가 포화되어 연결을 끊은 횟수.
     *
     * <p>값이 0이 아니면 느린 구독자(TCP 버퍼 포화 또는 네트워크 지연)가 있다는 뜻이다.
     * 끊긴 쪽은 {@code EventSource}가 재연결하고 {@code Last-Event-ID}로 빠진 이벤트를 받는다.
     */
    private final Counter queueSaturated;

    /** 연결이 실제 등록된 횟수. 현재 연결 수 gauge와 달리 짧게 붙었다 끊긴 연결도 놓치지 않는다. */
    private final Counter connectionsOpened;

    /** emitter.send() 안에 들어가 아직 반환하지 않은 전송 수. */
    private final AtomicInteger sendsInFlight = new AtomicInteger();

    /**
     * 방 전원의 emitter별 큐에 전송 작업을 배분하는 시간.
     *
     * <p><b>이벤트 이름으로 갈라 둔다.</b> 합쳐 놓으면 방 전원에게 쏘는 비용이 얼마인지는 알아도
     * 그게 어느 경로의 것인지 모른다. 실제 {@code emitter.send()} 시간은 {@code upbid.sse.send},
     * 큐에 머문 시간은 {@code upbid.sse.queue.wait}에서 별도로 잰다.
     *
     * <p>태그값마다 미리 만드는 이유는 {@code AuctionCloseMetrics}와 같다. 첫 이벤트 전까지
     * 시계열이 없으면 측정 구간의 증가분을 구할 수 없다. {@code EventType}에 없는 이름은
     * 참여자 수 알림 하나뿐이라 그것만 따로 넣는다.
     */
    private final Map<String, Timer> broadcasts;
    private final Map<String, Timer> fanouts;
    private final Map<String, Counter> eventsPublished;
    private final Map<String, Counter> sendAttempts;
    private final Map<String, Counter> sendSuccesses;
    private final Map<String, Counter> sendFailures;
    private final Map<String, Timer> sendDurations;
    private final Map<String, Timer> queueWaits;
    private final Map<String, Counter> connectionsClosed;
    private final Map<String, Counter> rejected;

    public SseMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.heartbeat = registry.timer("upbid.sse.heartbeat");
        this.publishFailures = registry.counter("upbid.sse.publish.failure");
        this.queueSaturated = registry.counter("upbid.sse.queue.saturated");
        this.connectionsOpened = registry.counter("upbid.sse.connections.opened");
        this.broadcasts = eventTimers(registry, BROADCAST, true);
        this.fanouts = eventTimers(registry, FANOUT, false);
        this.eventsPublished = eventCounters(registry, EVENTS_PUBLISHED);
        this.sendAttempts = eventCounters(registry, SEND_ATTEMPTS);
        this.sendSuccesses = eventCounters(registry, SEND_SUCCESSES);
        this.sendFailures = eventCauseCounters(registry, SEND_FAILURES);
        this.sendDurations = eventTimers(registry, SEND_DURATION, false);
        this.queueWaits = eventTimers(registry, QUEUE_WAIT, false);
        this.connectionsClosed = closeCounters(registry);
        this.rejected = Map.of(
                "queue_saturated", registry.counter(REJECTED, "reason", "queue_saturated"),
                "dispatcher_closed", registry.counter(REJECTED, "reason", "dispatcher_closed"));

        Gauge.builder("upbid.sse.in.flight", sendsInFlight, AtomicInteger::get)
                .description("현재 emitter.send 안에서 완료를 기다리는 전송 수")
                .register(registry);
    }

    private static Map<String, Timer> eventTimers(
            MeterRegistry registry, String metricName, boolean preloadAllEvents) {

        Map<String, Timer> timers = new ConcurrentHashMap<>();

        if (preloadAllEvents) {
            for (EventType type : EventType.values()) {
                timers.put(type.name(), registry.timer(metricName, "event", type.name()));
            }
            timers.put(RoomSseManager.PARTICIPANT_COUNT_EVENT,
                    registry.timer(metricName, "event", RoomSseManager.PARTICIPANT_COUNT_EVENT));
        }

        // preflight에서 histogram bucket 존재를 확인할 수 있게 대표 시계열 하나만 미리 만든다.
        timers.put(HEARTBEAT_EVENT, registry.timer(metricName, "event", HEARTBEAT_EVENT));

        return timers;
    }

    private static Map<String, Counter> eventCounters(MeterRegistry registry, String metricName) {

        Map<String, Counter> counters = new HashMap<>();

        for (EventType type : EventType.values()) {
            counters.put(type.name(), registry.counter(metricName, "event", type.name()));
        }
        counters.put(RoomSseManager.PARTICIPANT_COUNT_EVENT,
                registry.counter(metricName, "event", RoomSseManager.PARTICIPANT_COUNT_EVENT));
        counters.put(HEARTBEAT_EVENT, registry.counter(metricName, "event", HEARTBEAT_EVENT));

        return Map.copyOf(counters);
    }

    private static Map<String, Counter> closeCounters(MeterRegistry registry) {
        return Map.of(
                CLOSE_COMPLETED, registry.counter(CONNECTIONS_CLOSED, "reason", CLOSE_COMPLETED),
                CLOSE_TIMEOUT, registry.counter(CONNECTIONS_CLOSED, "reason", CLOSE_TIMEOUT),
                CLOSE_SEND_ERROR, registry.counter(CONNECTIONS_CLOSED, "reason", CLOSE_SEND_ERROR),
                CLOSE_QUEUE_SATURATED,
                        registry.counter(CONNECTIONS_CLOSED, "reason", CLOSE_QUEUE_SATURATED),
                CLOSE_ROOM_CLOSED,
                        registry.counter(CONNECTIONS_CLOSED, "reason", CLOSE_ROOM_CLOSED));
    }

    private static Map<String, Counter> eventCauseCounters(
            MeterRegistry registry, String metricName) {

        Map<String, Counter> counters = new HashMap<>();

        for (String eventName : eventNames()) {
            for (String cause : SEND_FAILURE_CAUSES) {
                counters.put(eventCauseKey(eventName, cause),
                        registry.counter(metricName, "event", eventName, "cause", cause));
            }
        }

        return Map.copyOf(counters);
    }

    private static Set<String> eventNames() {
        Set<String> names = new HashSet<>();
        for (EventType type : EventType.values()) {
            names.add(type.name());
        }
        names.add(RoomSseManager.PARTICIPANT_COUNT_EVENT);
        names.add(HEARTBEAT_EVENT);
        return Set.copyOf(names);
    }

    /** 지금 열려 있는 연결 수와 방 수를 이 Map에서 읽도록 게이지를 건다. */
    public void bindConnections(Map<Long, Set<SseEmitter>> roomEmitters) {
        Gauge.builder("upbid.sse.connections", roomEmitters,
                        map -> map.values().stream().mapToInt(Set::size).sum())
                .description("지금 열려 있는 SSE 연결 수")
                .register(registry);

        Gauge.builder("upbid.sse.rooms", roomEmitters, Map::size)
                .description("연결이 하나라도 붙어 있는 방 수")
                .register(registry);
    }

    /** emitter별 bounded queue의 추정 대기 작업 수를 모두 더한다. */
    public void bindDispatchers(Map<SseEmitter, EmitterDispatcher> dispatchers) {
        Gauge.builder("upbid.sse.queue.depth", dispatchers,
                        map -> map.values().stream()
                                .mapToLong(EmitterDispatcher::estimatedQueueDepth)
                                .sum())
                .description("모든 emitter dispatcher 큐에 대기 중인 추정 작업 수")
                .register(registry);
    }

    public void recordHeartbeat(Runnable sweep) {
        heartbeat.record(sweep);
    }

    public void recordEventPublished(String eventName) {
        eventCounter(eventsPublished, EVENTS_PUBLISHED, eventName).increment();
    }

    public void recordPublishFailure(String eventName, Throwable cause) {
        publishFailures.increment();
        registry.counter("upbid.sse.publish.failures",
                        "event", eventName,
                        "cause", failureCause(cause))
                .increment();
    }

    public void recordQueueSaturated() {
        queueSaturated.increment();
        recordRejected("queue_saturated");
    }

    public void recordRejected(String reason) {
        Counter counter = rejected.get(reason);
        (counter != null ? counter : registry.counter(REJECTED, "reason", reason)).increment();
    }

    public void recordConnectionOpened() {
        connectionsOpened.increment();
    }

    public void recordConnectionClosed(String reason) {
        Counter counter = connectionsClosed.get(reason);
        (counter != null
                        ? counter
                        : registry.counter(CONNECTIONS_CLOSED, "reason", reason))
                .increment();
    }

    public void recordQueueWait(String eventName, long enqueuedAtNanos) {
        long waitNanos = Math.max(0L, System.nanoTime() - enqueuedAtNanos);
        eventTimer(queueWaits, QUEUE_WAIT, eventName).record(waitNanos, TimeUnit.NANOSECONDS);
    }

    public Timer.Sample startSend() {
        sendsInFlight.incrementAndGet();
        return Timer.start(registry);
    }

    public void recordSendAttempt(String eventName) {
        eventCounter(sendAttempts, SEND_ATTEMPTS, eventName).increment();
    }

    public void recordSendSuccess(String eventName) {
        eventCounter(sendSuccesses, SEND_SUCCESSES, eventName).increment();
    }

    public void recordSendFailure(String eventName, Throwable cause) {
        String causeName = failureCause(cause);
        Counter counter = sendFailures.get(eventCauseKey(eventName, causeName));
        (counter != null
                        ? counter
                        : registry.counter(SEND_FAILURES,
                                "event", eventName,
                                "cause", causeName))
                .increment();
    }

    public void finishSend(String eventName, Timer.Sample sample) {
        try {
            sample.stop(eventTimer(sendDurations, SEND_DURATION, eventName));
        } finally {
            sendsInFlight.decrementAndGet();
        }
    }

    /**
     * 논리 이벤트 하나를 이 인스턴스의 모든 구독자에게 fan-out하는 시간을 잰다.
     * 동기 구현에서는 emitter.send() 전체가 이 범위에 포함된다.
     */
    public void recordBroadcast(String eventName, Runnable send) {
        long startedAt = System.nanoTime();
        try {
            send.run();
        } finally {
            long elapsed = System.nanoTime() - startedAt;
            eventTimer(broadcasts, BROADCAST, eventName).record(elapsed, TimeUnit.NANOSECONDS);
            // 현재 구조의 fan-out은 emitter별 큐에 배분하는 단계다. 실제 send 비용은
            // upbid.sse.send가 별도로 기록한다.
            eventTimer(fanouts, FANOUT, eventName).record(elapsed, TimeUnit.NANOSECONDS);
        }
    }

    private Counter eventCounter(Map<String, Counter> counters, String metricName, String eventName) {
        Counter counter = counters.get(eventName);
        return counter != null ? counter : registry.counter(metricName, "event", eventName);
    }

    private Timer eventTimer(Map<String, Timer> timers, String metricName, String eventName) {
        return timers.computeIfAbsent(eventName,
                name -> registry.timer(metricName, "event", name));
    }

    private String failureCause(Throwable cause) {
        if (cause instanceof IOException) {
            return "io";
        }
        if (cause instanceof IllegalStateException) {
            return "illegal_state";
        }
        return "runtime";
    }

    private static String eventCauseKey(String eventName, String cause) {
        return eventName + '\u0000' + cause;
    }
}
