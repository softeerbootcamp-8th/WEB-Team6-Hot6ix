package com.hot6ix.upbid.domain.sse.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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

    /** 방 전원에게 한 번 쏘는 데 걸리는 시간. 전송 루프만 잰다 — 큐에서 기다린 시간은 아래 타이머다. */
    private final Timer broadcast;

    /**
     * 브로드캐스트 작업이 실행기 큐에서 기다린 시간.
     *
     * <p>{@link #broadcast}는 전송 루프만 재므로 큐 대기가 빠져 있다. 동기 전송이던 시절에는
     * 큐가 없어서 그 값이 곧 이벤트 도달 시간이었지만, 전송을 실행기로 넘긴 뒤로는
     * <b>대기 + 전송</b>이 도달 시간이다. 이 타이머가 없으면 "입찰 응답은 빨라졌는데 이벤트가
     * 화면에 늦게 도착하는지"를 알 수 없다.
     *
     * <p>실측(2026-08-11, SSE 접속 400): 브로드캐스트가 초당 121건, 한 건에 평균 11.6ms 였다.
     * 풀이 2스레드라 사용률 70%고, 이 수준에서는 대기가 전송 시간과 맞먹는다.
     *
     * <p>{@code SseBroadcastExecutor}가 {@code Executor} 타입 빈이 아니라서 Micrometer 의
     * 실행기 자동 계측(큐 깊이)을 못 받는다. 그 이유는 그 클래스 javadoc 에 있다.
     */
    private final Timer broadcastQueueWait;

    /**
     * 큐가 넘쳐 호출 스레드가 직접 전송한 횟수({@link java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy}).
     *
     * <p>이게 0보다 크면 그만큼은 비동기화 이전 동작으로 돌아간 것이다. <b>위 대기 시간과 짝으로
     * 봐야 한다</b> — 호출 스레드가 즉시 실행하면 대기가 0으로 찍혀서, 대기 0이 "큐가 한가하다"인지
     * "비동기가 아니었다"인지 이 값 없이는 구분할 수 없다.
     */
    private final Counter broadcastRanOnCaller;

    public SseMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.heartbeat = registry.timer("upbid.sse.heartbeat");
        this.broadcast = registry.timer("upbid.sse.broadcast");
        this.broadcastQueueWait = registry.timer("upbid.sse.broadcast.queue");
        this.broadcastRanOnCaller = registry.counter("upbid.sse.broadcast.caller.runs");
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

    /** @param nanos {@code execute()}를 부른 시점부터 작업이 실제로 시작될 때까지 걸린 시간 */
    public void recordBroadcastQueueWait(long nanos) {
        broadcastQueueWait.record(nanos, TimeUnit.NANOSECONDS);
    }

    public void countBroadcastRanOnCaller() {
        broadcastRanOnCaller.increment();
    }
}
