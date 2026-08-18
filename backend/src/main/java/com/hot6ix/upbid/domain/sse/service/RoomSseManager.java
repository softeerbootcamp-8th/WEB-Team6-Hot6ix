package com.hot6ix.upbid.domain.sse.service;

import com.hot6ix.upbid.domain.sse.config.SseProperties;
import com.hot6ix.upbid.domain.sse.event.ParticipantCountPublisher;
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

    /**
     * {@code EventType}에 없는 유일한 이벤트 이름이라 {@code SseMetrics}가 태그값으로 가져간다.
     * {@code ParticipantCountPublisher}(다른 패키지)도 발행 지표 태그로 같은 값을 쓴다.
     */
    public static final String PARTICIPANT_COUNT_EVENT = "PARTICIPANT_COUNT_UPDATED";

    private static final int PARTICIPANT_JOINED = 1;
    private static final int PARTICIPANT_LEFT = -1;

    private final Map<Long, Set<SseEmitter>> roomEmitters = new ConcurrentHashMap<>();
    private final Map<SseEmitter, EmitterDispatcher> dispatchers = new ConcurrentHashMap<>();

    private final SseProperties sseProperties;
    /** 계측은 이 객체가 안다. 이 클래스는 Micrometer 를 모른다. */
    private final SseMetrics sseMetrics;
    private final SseEventBuffer sseEventBuffer;
    /** 참여자 수 전역 합계 갱신+발행. 여기서 로컬 값만 쏘면 다른 인스턴스가 모른다. */
    private final ParticipantCountPublisher participantCountPublisher;
    /** emitter 별 drain 을 실행하는 executor. 가상 스레드를 전역 공유한다. */
    private final Executor sseVirtualThreadExecutor;

    @PostConstruct
    void bindMetrics() {
        sseMetrics.bindConnections(roomEmitters);
        sseMetrics.bindDispatchers(dispatchers);
    }

    /**
     * SSE 구독을 등록한다. 구독 시점의 현재 상태는 물품 조회 API가 내려주므로 초기 이벤트는 없다.
     *
     * <p>재연결({@code lastEventId != null})이면 버퍼에서 그 ID 이후 이벤트를 replay한다. 아래
     * 참여자 수 브로드캐스트는 id 없이 나가므로(#311) {@code Last-Event-ID}에는 영향을 주지
     * 않는다 — 최초 연결이든 재연결이든 이 값이 {@code Last-Event-ID}의 시작점이 되지 않는다.
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

        incrementParticipantCount(roomId, PARTICIPANT_JOINED);

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
     * 참여자 수를 이 인스턴스에 붙은 연결에만 전달한다. {@code deliverLocal}과 달리 id가
     * 없다 — 재연결 replay 버퍼에 안 쌓이는 이벤트라 순차 번호 자체가 없기 때문이다
     * ({@link SseDispatchTask.ParticipantCount} 참고).
     *
     * <p>{@code ParticipantCountSubscriber}가 부르는 것이 유일한 경로다.
     */
    public void deliverParticipantCountLocal(Long roomId, int count) {
        Set<SseEmitter> emitters = roomEmitters.get(roomId);

        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        SseDispatchTask task = new SseDispatchTask.ParticipantCount(roomId, count);

        sseMetrics.recordBroadcast(PARTICIPANT_COUNT_EVENT, () -> {
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

    /**
     * <b>{@code unregister}가 이 사이클에서 실제로 뭔가를 지웠을 때만</b> 안쪽을 탄다. 같은
     * emitter에 대해 {@code onError}·{@code onCompletion}이 겹쳐 불려도(#250 참고)
     * {@code compute}가 두 번째 호출부터는 항상 {@code false}를 주므로, 참여자 수 갱신도
     * 정확히 한 번만 나간다.
     */
    private void disconnect(Long roomId, SseEmitter emitter, String reason) {
        if (unregister(roomId, emitter)) {
            sseMetrics.recordConnectionClosed(reason);
            log.info("sse 연결 종료: roomId={}, reason={}", roomId, reason);
            incrementParticipantCount(roomId, PARTICIPANT_LEFT);
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
     * 연결/해제 한 건에 대해 참여자 수를 증감으로 반영한다. 절대값을 읽어서 보내면
     * 같은 서버에서 거의 동시에 일어난 두 연결 변화의 도착 순서가 뒤바뀔 때 최신 값이
     * 오래된 값에 덮어써질 수 있는데, 증감은 그 레이스가 없다(#311).
     */
    private void incrementParticipantCount(Long roomId, int delta) {
        participantCountPublisher.increment(roomId, delta);
    }

    /**
     * 이 인스턴스가 아는 로컬 참여자 수(절대값)를 Redis에 다시 쓰고, 전역 합계를 발행한다.
     * heartbeat가 방마다 주기적으로 불러 TTL을 갱신하고 드리프트를 바로잡는 용도로만 쓴다
     * (#311) — 연결/해제 한 건 한 건은 {@link #incrementParticipantCount}를 쓴다.
     */
    private void broadcastParticipantCount(Long roomId) {
        participantCountPublisher.publish(roomId, getParticipantCount(roomId));
    }

    /**
     * 끊긴 연결은 write를 시도할 때만 드러난다. 주기적으로 heartbeat 를 emitter 별 큐에 넣어
     * 실제 전송을 executor에 맡기고, 전송 실패 시 {@link EmitterSubscriber}가 emitter 생명주기를
     * 통해 연결을 정리한다.
     *
     * <p>heartbeat 는 버퍼 ID 없이 comment 만 보내므로 {@code Last-Event-ID}에 영향을 주지
     * 않는다. 큐가 포화된 emitter(느린 구독자)에 대해서는 drop 되며, 이는 이미 죽어가는
     * 연결이라 무방하다.
     *
     * <p>ping(큐 적재)과 별개로, 방마다 <b>연결 변화가 없어도</b> 절대값을 다시 발행한다(#311).
     * 그 방에 한동안 입장·퇴장이 없으면 이 방에 대한 이 서버의 필드가 {@code FIELD_TTL}로
     * 만료돼서, 다른 서버가 나중에 합계를 낼 때 이 서버 몫이 통째로 빠질 수 있었다 — 실제로
     * 수동 테스트에서 재현됐다(서버 두 대 중 한쪽이 조용하면 합계가 그쪽 몫만큼 낮게 나옴).
     *
     * <p>이 작업은 물품 마감 예약과 {@code spring.task.scheduling.pool}을 같이 쓴다. 접속이
     * 많아 한 바퀴가 길어지면 마감이 밀릴 수 있어서, 한 바퀴에 걸린 시간을
     * {@code upbid.sse.heartbeat}로 잰다.
     */
    @Scheduled(fixedRateString = "${upbid.sse.heartbeat-interval-ms}")
    public void sendHeartbeat() {
        sseMetrics.recordHeartbeat(() ->
                roomEmitters.forEach((roomId, emitters) -> {
                    for (SseEmitter emitter : emitters) {
                        SseDispatchTask task = new SseDispatchTask.Heartbeat(roomId);
                        EmitterDispatcher dispatcher = dispatchers.get(emitter);
                        if (dispatcher != null) {
                            dispatcher.enqueue(task);
                        }
                    }
                    broadcastParticipantCount(roomId);
                }));
    }

    /**
     * 경매방 종료 처리.
     *
     * <p><b>큐에 남은 이벤트를 먼저 흘려보낸다.</b> {@code ROOM_CLOSED}는 이 메서드가 불리기
     * 직전에 같은 스레드에서 큐에 들어가는데({@code SseEventSubscriber}), 큐를 버리고 닫으면
     * 클라이언트는 종료 이벤트 없이 연결만 끊긴 것으로 관찰한다(#307). 그래서 여기서
     * {@code complete()}를 부르지 않고, 전송을 끝낸 {@link EmitterSubscriber}가 닫게 한다.
     *
     * <p>단순히 emitter를 Map에서 제거하는 것으로는 부족하다. HTTP 연결은 살아있고
     * EventSource가 자동 재연결할 수 있어 반드시 닫혀야 한다. 클라이언트가 응답하지 않아
     * 전송이 끝나지 않는 경우는 {@code close-flush-timeout-ms} 뒤에 강제로 닫는다.
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
                sseMetrics.recordConnectionClosed(SseMetrics.CLOSE_ROOM_CLOSED);

                if (dispatcher != null) {
                    dispatcher.closeAfterFlush(sseProperties.closeFlushTimeoutMs());
                } else {
                    // 흘려보낼 큐가 없으면 기다릴 이유도 없다.
                    complete(emitter);
                }
            });
        }

        // 버퍼와 순차 ID 카운터는 둘 다 Redis 에 있어 이 한 번으로 함께 지워진다.
        sseEventBuffer.clear(roomId);
        // 참여자 수 집계 키도 같이 지운다(#311). 종료된 방은 재구독이 막혀 있어 급하진
        // 않지만, KEY_TTL(2일)까지 남겨둘 이유가 없다.
        participantCountPublisher.clear(roomId);

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
