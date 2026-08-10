package com.hot6ix.upbid.domain.sse.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 계측만 아는 객체. 분리한 이유는 {@code BidMetrics}와 같다.
 */
@Component
public class SseMetrics {

    private final MeterRegistry registry;

    /** heartbeat 한 바퀴. 접속 수가 늘 때 이 시간이 어떻게 늘어나는지를 본다. */
    private final Timer heartbeat;

    /** 방 전원에게 한 번 쏘는 데 걸리는 시간. 이 값이 입찰 응답 시간에 그대로 얹힌다. */
    private final Timer broadcast;

    public SseMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.heartbeat = registry.timer("upbid.sse.heartbeat");
        this.broadcast = registry.timer("upbid.sse.broadcast");
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

    public void recordBroadcast(Runnable send) {
        broadcast.record(send);
    }
}
