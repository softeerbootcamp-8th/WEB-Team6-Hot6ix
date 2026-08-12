package com.hot6ix.upbid.domain.sse.service;

import com.hot6ix.upbid.global.event.EventType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 계측만 아는 객체. 분리한 이유는 {@code BidMetrics}와 같다.
 */
@Component
public class SseMetrics {

    private static final String BROADCAST = "upbid.sse.broadcast";

    private final MeterRegistry registry;

    /** heartbeat 한 바퀴. 접속 수가 늘 때 이 시간이 어떻게 늘어나는지를 본다. */
    private final Timer heartbeat;

    /**
     * 방 전원에게 한 번 쏘는 데 걸리는 시간. 이 값이 그대로 부른 쪽 시간에 얹힌다. 입찰이면
     * 응답 시간에(#234), 마감이면 마감 소요 시간에 들어간다.
     *
     * <p><b>이벤트 이름으로 갈라 둔다.</b> 합쳐 놓으면 방 전원에게 쏘는 비용이 얼마인지는 알아도
     * 그게 어느 경로의 것인지 모른다. 마감 한 건이 왜 오래 걸리는지 보려면 마감이 쏜 것만
     * 따로 봐야 한다(#198).
     *
     * <p>태그값마다 미리 만드는 이유는 {@code AuctionCloseMetrics}와 같다. 첫 이벤트 전까지
     * 시계열이 없으면 측정 구간의 증가분을 구할 수 없다. {@code EventType}에 없는 이름은
     * 참여자 수 알림 하나뿐이라 그것만 따로 넣는다.
     */
    private final Map<String, Timer> broadcasts;

    /**
     * {@code sseExecutor}가 거부한 브로드캐스트 횟수(#234). 가상 스레드로 바꾼 뒤로는 풀·큐가
     * 없어 사실상 항상 0이다 — {@code sched_active_max}가 가상 스레드 스케줄러에서 NaN이 되는
     * 것과 같은 이유로, 지표 자체는 지우지 않고 그대로 둔다.
     */
    private final Counter rejected;

    public SseMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.heartbeat = registry.timer("upbid.sse.heartbeat");
        this.broadcasts = broadcastTimers(registry);
        this.rejected = registry.counter("upbid.sse.broadcast.rejected");
    }

    private static Map<String, Timer> broadcastTimers(MeterRegistry registry) {

        Map<String, Timer> timers = new HashMap<>();

        for (EventType type : EventType.values()) {
            timers.put(type.name(), registry.timer(BROADCAST, "event", type.name()));
        }
        timers.put(RoomSseManager.PARTICIPANT_COUNT_EVENT,
                registry.timer(BROADCAST, "event", RoomSseManager.PARTICIPANT_COUNT_EVENT));

        return Map.copyOf(timers);
    }

    /**
     * 지금 열려 있는 연결 수와 방 수를 이 Map에서 읽도록 게이지를 건다.
     *
     * <p>게이지는 값을 밀어 넣는 게 아니라 <b>스크랩할 때 읽어 가는</b> 방식이라, 연결을
     * 등록·해제하는 코드에 계측을 넣지 않아도 된다. Micrometer가 Map을 약한 참조로 들고 있어서
     * 이 객체가 살아 있는 동안만 유효하다.
     */
    public void bindConnections(Map<Long, Set<SseEmitter>> roomEmitters) {
        Gauge.builder("upbid.sse.connections", roomEmitters,
                        map -> map.values().stream().mapToInt(Set::size).sum())
                .description("지금 열려 있는 SSE 연결 수")
                .register(registry);

        Gauge.builder("upbid.sse.rooms", roomEmitters, Map::size)
                .description("연결이 하나라도 붙어 있는 방 수")
                .register(registry);
    }

    public void recordHeartbeat(Runnable sweep) {
        heartbeat.record(sweep);
    }

    /**
     * 방 전원에게 한 번 쏘는 시간을 이벤트 이름별로 잰다.
     *
     * <p>미리 만들어 둔 것에 없는 이름이면 그때 만든다. 그런 이름은 지금 없지만, 새 이벤트를
     * 추가하고 여기에 등록하는 걸 잊었을 때 계측이 통째로 빠지는 것보다는 낫다.
     */
    public void recordBroadcast(String eventName, Runnable send) {
        broadcasts.getOrDefault(eventName, registry.timer(BROADCAST, "event", eventName))
                .record(send);
    }

    public void recordRejected() {
        rejected.increment();
    }
}
