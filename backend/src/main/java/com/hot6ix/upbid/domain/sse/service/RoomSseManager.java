package com.hot6ix.upbid.domain.sse.service;

import com.hot6ix.upbid.domain.sse.config.SseProperties;
import com.hot6ix.upbid.domain.sse.dto.ParticipantCountDto;
import com.hot6ix.upbid.domain.sse.event.SseEventPublisher;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(SseProperties.class)
public class RoomSseManager {

    /** {@code EventType}에 없는 유일한 이벤트 이름이라 {@code SseMetrics}가 태그값으로 가져간다. */
    static final String PARTICIPANT_COUNT_EVENT = "PARTICIPANT_COUNT_UPDATED";

    private final Map<Long, Set<SseEmitter>> roomEmitters = new ConcurrentHashMap<>();
    private final Map<SseEmitter, EmitterDispatcher> dispatchers = new ConcurrentHashMap<>();

    private final SseProperties sseProperties;
    /** 계측은 이 객체가 안다. 이 클래스는 Micrometer 를 모른다. */
    private final SseMetrics sseMetrics;
    private final SseEventBuffer sseEventBuffer;
    /** 참여자 수도 다른 이벤트와 같은 경로로 나간다. 여기서 직접 쏘면 다른 인스턴스가 모른다. */
    private final SseEventPublisher sseEventPublisher;
    /** emitter 별 drain 을 실행하는 executor. VT 또는 고정 플랫폼 스레드 풀을 전역 공유한다. */
    private final Executor sseVirtualThreadExecutor;

    @PostConstruct
    void bindMetrics() {
        sseMetrics.bindConnections(roomEmitters);
        sseMetrics.bindDispatchers(dispatchers);
    }

    /**
     * SSE 구독을 등록한다. 구독 시점의 현재 상태는 물품 조회 API가 내려주므로 초기 이벤트는 없다.
     *
     * <p>재연결({@code lastEventId != null})이면 버퍼에서 그 ID 이후 이벤트를 replay한다. 최초
     * 연결이면 아래 참여자 수 브로드캐스트가 이 연결의 첫 이벤트가 되고, 클라이언트는 그 ID를
     * {@code Last-Event-ID}의 시작점으로 잡는다.
     */
    public SseEmitter subscribe(Long roomId, Long lastEventId) {
        SseEmitter emitter = createEmitter();

        register(roomId, emitter);

        emitter.onCompletion(() -> disconnect(roomId, emitter, SseMetrics.CLOSE_COMPLETED));
        emitter.onTimeout(() -> disconnect(roomId, emitter, SseMetrics.CLOSE_TIMEOUT));
        emitter.onError(e -> disconnect(roomId, emitter, closeReason(e)));

        if (lastEventId != null) {
            List<BufferedEvent> missed = sseEventBuffer.getEventsAfter(roomId, lastEventId);
            EmitterDispatcher dispatcher = dispatchers.get(emitter);
            for (BufferedEvent event : missed) {
                if (dispatcher != null) {
                    dispatcher.enqueue(new SseDispatchTask.Event(
                            roomId, event.eventName(), event.id(), event.data()));
                }
            }
        }

        broadcastParticipantCount(roomId);

        log.info("sse 연결 완료: roomId={}, lastEventId={}", roomId, lastEventId);

        return emitter;
    }

    /**
     * <b>이 인스턴스에</b> 붙어 있는 emitter 별 dispatcher 에 이벤트를 넣는다. 실제 전송은
     * 설정된 executor에서 비동기로 일어나므로 호출 스레드는 즉시 반환된다.
     *
     * <p>{@code SseEventSubscriber}가 부르는 것이 유일한 경로다. 이벤트를 만든 쪽이 여기를
     * 직접 부르면 자기 방 클라이언트에게 같은 이벤트가 두 번 간다.
     *
     * <p>{@code upbid.sse.broadcast}로 이 인스턴스의 enqueue 비용을 잰다. 실제 전송 비용은
     * executor 내부에서 발생하므로 이 타이머는 fan-out 대상 수에 비례하는 enqueue 시간만 측정한다.
     */
    public void deliverLocal(Long roomId, String name, long id, Object data) {
        Set<SseEmitter> emitters = roomEmitters.get(roomId);

        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        SseDispatchTask task = new SseDispatchTask.Event(roomId, name, id, data);

        sseMetrics.recordBroadcast(name, () -> {
            for (SseEmitter emitter : emitters) {
                EmitterDispatcher dispatcher = dispatchers.get(emitter);
                if (dispatcher != null) {
                    dispatcher.enqueue(task);
                }
            }
        });
    }

    /**
     * emitter 생성만 떼어 둔다.
     *
     * <p>이 클래스의 정리는 전부 emitter 생명주기 콜백({@code onError → disconnect})을 타는데,
     * 그 콜백을 발화시키는 것은 Spring MVC 의 async handler 다. 유닛 테스트에는 그 계층이
     * 없어서 맨 {@link SseEmitter}로는 <b>정리가 도는지를 확인할 수 없다.</b> 테스트가 여기를
     * 재정의해 콜백이 실제로 도는 emitter 로 갈아끼운다.
     */
    SseEmitter createEmitter() {
        return new SseEmitter(sseProperties.emitterTimeoutMs());
    }

    public int getParticipantCount(Long roomId) {
        Set<SseEmitter> emitters = roomEmitters.get(roomId);
        return emitters == null ? 0 : emitters.size();
    }

    private void disconnect(Long roomId, SseEmitter emitter, String reason) {
        if (unregister(roomId, emitter)) {
            sseMetrics.recordConnectionClosed(reason);
            broadcastParticipantCount(roomId);
            log.info("sse 연결 종료: roomId={}, reason={}", roomId, reason);
        }
    }

    private void register(Long roomId, SseEmitter emitter) {
        roomEmitters
                .computeIfAbsent(roomId, id -> ConcurrentHashMap.newKeySet())
                .add(emitter);

        dispatchers.put(emitter, new EmitterDispatcher(
                sseVirtualThreadExecutor,
                sseProperties.emitterQueueCapacity(),
                roomId,
                emitter,
                sseMetrics));
        sseMetrics.recordConnectionOpened();
    }

    /**
     * 연결 하나를 걷어내고, 그 방의 마지막 연결이었으면 방 자체를 지운다.
     *
     * <p>방을 안 지우면 {@code upbid.sse.rooms}가 "지금 연결이 붙어 있는 방 수"가 아니라
     * "한 번이라도 연결이 있었던 방 수"가 되어 한 번 오른 뒤 영영 안 내려온다(실측으로 확인).
     * 빈 Set 도 방마다 하나씩 계속 쌓인다.
     *
     * <p><b>{@code get} → {@code isEmpty} → {@code remove} 로 나눠 쓰면 안 된다.</b> 비었는지
     * 확인한 뒤 지우기 전에 다른 스레드가 그 방에 새로 붙으면, 그 연결이 든 Set 을 통째로
     * 버려서 방금 붙은 사람이 아무 이벤트도 못 받는다. {@code compute} 는 키 하나에 대해
     * 원자적이라 그 틈이 없다.
     */
    private boolean unregister(Long roomId, SseEmitter emitter) {
        AtomicBoolean removed = new AtomicBoolean();

        roomEmitters.compute(roomId, (id, emitters) -> {
            if (emitters == null) {
                return null;
            }

            removed.set(emitters.remove(emitter));

            return emitters.isEmpty() ? null : emitters;
        });

        EmitterDispatcher dispatcher = dispatchers.remove(emitter);
        if (dispatcher != null) {
            dispatcher.close();
        }

        return removed.get();
    }

    /**
     * <b>여기서 세는 수는 이 인스턴스에 붙은 연결 수다.</b> 서버가 여러 대면 실제 참여자보다
     * 작게 나오고, 인스턴스마다 자기 값을 발행하므로 화면 숫자가 그 값들 사이로 움직인다.
     * 전역 집계는 후속 작업으로 남긴다. 서버가 한 대인 동안은 지금까지와 같은 값이다.
     */
    private void broadcastParticipantCount(Long roomId) {
        sseEventPublisher.publish(
                PARTICIPANT_COUNT_EVENT, roomId, new ParticipantCountDto(getParticipantCount(roomId)));
    }

    /**
     * 끊긴 연결은 write를 시도할 때만 드러난다. 주기적으로 heartbeat 를 emitter 별 큐에 넣어
     * 실제 전송을 executor에 맡기고, 전송 실패 시 {@link EmitterSubscriber}가 emitter 생명주기를
     * 통해 연결을 정리한다.
     *
     * <p>heartbeat 는 버퍼 ID 없이 comment 만 보내므로 {@code Last-Event-ID}에 영향을 주지
     * 않는다. 큐가 포화된 emitter(느린 구독자)에 대해서는 drop 되며, 이는 이미 죽어가는
     * 연결이라 무방하다.
     */
    @Scheduled(fixedRateString = "${upbid.sse.heartbeat-interval-ms}")
    public void sendHeartbeat() {
        sseMetrics.recordHeartbeat(() ->
                roomEmitters.forEach((roomId, emitters) -> {
                    for (SseEmitter emitter : emitters) {
                        EmitterDispatcher dispatcher = dispatchers.get(emitter);
                        if (dispatcher != null) {
                            dispatcher.enqueue(new SseDispatchTask.Heartbeat(roomId));
                        }
                    }
                }));
    }

    /**
     * 경매방 종료 처리.
     *
     * <p>dispatcher 를 먼저 닫아 큐에 남은 이벤트를 버리고, emitter 를 완료시킨다.
     * 단순히 emitter를 Map에서 제거하면 HTTP 연결은 살아있고
     * EventSource가 자동 재연결할 수 있으므로 {@code complete()}를 호출한다.
     *
     * <p>종료된 방에 대한 재연결은 구독 시점의 방 상태 검증으로 차단한다.
     *
     * <p>{@code SseEventSubscriber}가 {@code ROOM_CLOSED}를 받고 부른다. 인스턴스는 자기에게
     * 붙은 연결만 알기 때문에, 방을 닫은 인스턴스 하나가 아니라 이벤트를 받은 전부가 각자
     * 불러야 모든 연결이 끊긴다.
     */
    public void closeRoom(Long roomId) {
        Set<SseEmitter> closing = roomEmitters.remove(roomId);

        if (closing != null) {
            closing.forEach(emitter -> {
                EmitterDispatcher dispatcher = dispatchers.remove(emitter);
                if (dispatcher != null) {
                    dispatcher.close();
                }
                sseMetrics.recordConnectionClosed(SseMetrics.CLOSE_ROOM_CLOSED);
                complete(emitter);
            });
        }

        // 버퍼와 순차 ID 카운터는 둘 다 Redis 에 있어 이 한 번으로 함께 지워진다.
        sseEventBuffer.clear(roomId);

        log.info("sse 방 종료: roomId={}, 끊은 연결={}", roomId, closing == null ? 0 : closing.size());
    }

    /**
     * 이미 완료된 emitter에 {@code complete()}를 부르면 {@code IllegalStateException}이 난다.
     * 방을 닫는 중이라 그 연결은 어차피 정리 대상이므로 삼킨다.
     */
    private void complete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (IllegalStateException e) {
            log.debug("이미 종료된 sse 연결", e);
        }
    }

    private String closeReason(Throwable cause) {
        return cause instanceof EmitterDispatcher.QueueSaturatedException
                ? SseMetrics.CLOSE_QUEUE_SATURATED
                : SseMetrics.CLOSE_SEND_ERROR;
    }
}
