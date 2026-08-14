package com.hot6ix.upbid.domain.sse.service;

import com.hot6ix.upbid.domain.sse.config.SseProperties;
import com.hot6ix.upbid.domain.sse.dto.ParticipantCountDto;
import com.hot6ix.upbid.domain.sse.event.SseEventPublisher;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 방별 SSE 연결을 들고, 이벤트를 이 인스턴스에 붙은 emitter 로 내보낸다.
 *
 * <p><b>전송은 emitter 별 큐 없이 이벤트마다 VT 하나로 처리한다.</b> 느린 구독자가 다른
 * emitter 를 막지 않는다는 목적은 이것만으로 달성된다 — 막히는 것은 그 emitter 를 맡은 VT
 * 하나뿐이다.
 *
 * <p>대신 <b>같은 emitter 에 VT 여러 개가 동시에 붙을 수 있어 도착 순서가 뒤집힐 수 있다.</b>
 * 클라이언트가 {@code Last-Event-ID} 로 역전을 걸러낸다(프론트 {@code use-realtime-status.ts}).
 * 큐를 두는 대안과의 비교 실험이 목적이며, 판단 기준은 {@code jdk.VirtualThreadPinned} 와
 * 실제 역전 횟수다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(SseProperties.class)
public class RoomSseManager {

    /** {@code EventType}에 없는 유일한 이벤트 이름이라 {@code SseMetrics}가 태그값으로 가져간다. */
    static final String PARTICIPANT_COUNT_EVENT = "PARTICIPANT_COUNT_UPDATED";

    private final Map<Long, Set<SseEmitter>> roomEmitters = new ConcurrentHashMap<>();

    private final SseProperties sseProperties;
    /** 계측은 이 객체가 안다. 이 클래스는 Micrometer 를 모른다. */
    private final SseMetrics sseMetrics;
    private final SseEventBuffer sseEventBuffer;
    /** 참여자 수도 다른 이벤트와 같은 경로로 나간다. 여기서 직접 쏘면 다른 인스턴스가 모른다. */
    private final SseEventPublisher sseEventPublisher;
    /** 전송을 실행하는 VT executor. 전역 하나를 공유한다. */
    private final Executor sseVirtualThreadExecutor;

    @PostConstruct
    void bindMetrics() {
        sseMetrics.bindConnections(roomEmitters);
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

        emitter.onCompletion(() -> disconnect(roomId, emitter));
        emitter.onTimeout(() -> disconnect(roomId, emitter));
        emitter.onError(e -> disconnect(roomId, emitter));

        if (lastEventId != null) {
            replay(roomId, emitter, sseEventBuffer.getEventsAfter(roomId, lastEventId));
        }

        broadcastParticipantCount(roomId);

        log.info("sse 연결 완료: roomId={}, lastEventId={}", roomId, lastEventId);

        return emitter;
    }

    /**
     * 놓친 이벤트를 <b>VT 하나에서 순서대로</b> 내보낸다.
     *
     * <p>실시간 전송과 달리 여기를 이벤트마다 VT 로 흩뿌리면 안 된다. replay 는 최대
     * {@code buffer-size}(50)개를 한 번에 보내는 <b>이 시스템에서 가장 큰 버스트</b>라 역전이
     * 가장 심하게 일어나고, 클라이언트가 역전을 버리므로 <b>복구하려고 보낸 것이 대부분
     * 버려진다.</b> 순서대로 보내면 그런 일이 없다.
     *
     * <p>호출 스레드(톰캣)에서 직접 보내지 않는 이유는 최대 50번의 블로킹 write 를 구독 요청에
     * 얹지 않기 위해서다.
     */
    private void replay(Long roomId, SseEmitter emitter, List<BufferedEvent> missed) {
        if (missed.isEmpty()) {
            return;
        }

        sseVirtualThreadExecutor.execute(() -> {
            for (BufferedEvent event : missed) {
                if (!send(roomId, emitter, event.eventName(), event.id(), event.data())) {
                    return;
                }
            }
        });
    }

    /**
     * <b>이 인스턴스에</b> 붙어 있는 emitter 각각에 이벤트를 보낸다. 전송은 emitter 마다 VT
     * 하나에 맡기므로 호출 스레드는 즉시 반환된다.
     *
     * <p>{@code SseEventSubscriber}가 부르는 것이 유일한 경로다. 이벤트를 만든 쪽이 여기를
     * 직접 부르면 자기 방 클라이언트에게 같은 이벤트가 두 번 간다.
     *
     * <p>{@code upbid.sse.broadcast}는 VT 제출 비용만 잰다. 실제 전송은 VT 안에서 일어난다.
     */
    public void deliverLocal(Long roomId, String name, long id, Object data) {
        Set<SseEmitter> emitters = roomEmitters.get(roomId);

        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        sseMetrics.recordBroadcast(name, () -> {
            for (SseEmitter emitter : emitters) {
                sseVirtualThreadExecutor.execute(() -> send(roomId, emitter, name, id, data));
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
     * emitter 하나에 이벤트를 보낸다. 성공 여부를 돌려주는 것은 replay 가 중간에 실패하면
     * 남은 것을 더 보내지 않기 위해서다.
     *
     * <p>실패하면 {@link SseEmitter#completeWithError}를 불러 emitter 생명주기 콜백
     * ({@code onError → disconnect → unregister})이 정리를 이어받게 한다. 여기서 직접
     * {@code unregister} 를 부르지 않는 이유는 정리 경로를 하나로 모으기 위해서다.
     *
     * <p>끊긴 연결({@code IOException} 등)은 정상 종료라 {@code debug}, 그 밖의 예외는 코드
     * 문제일 수 있어 {@code warn} 으로 남긴다.
     */
    private boolean send(Long roomId, SseEmitter emitter, String name, long id, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(id))
                    .name(name)
                    .data(data));
            return true;

        } catch (IOException | IllegalStateException e) {
            log.debug("sse 전송 중 끊긴 연결 정리: roomId={}, name={}, cause={}", roomId, name, cause(e));
            completeWithError(emitter, e);

        } catch (RuntimeException e) {
            log.warn("sse 전송 실패로 연결 종료: roomId={}, name={}", roomId, name, e);
            completeWithError(emitter, e);
        }
        return false;
    }

    /**
     * 끊긴 연결은 장애가 아니라 정상적인 종료다. 스택트레이스를 남기면 봐야 할 로그가 묻히므로
     * 예외 종류와 메시지만 남긴다.
     */
    private String cause(Exception e) {
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    /** 이미 완료된 emitter 에 불러도 터지지 않게 삼킨다. 그 연결은 어차피 정리 대상이다. */
    private void completeWithError(SseEmitter emitter, Exception e) {
        try {
            emitter.completeWithError(e);
        } catch (IllegalStateException ignored) {
        }
    }

    private void disconnect(Long roomId, SseEmitter emitter) {
        unregister(roomId, emitter);
        broadcastParticipantCount(roomId);
        log.info("sse 연결 종료: roomId={}", roomId);
    }

    private void register(Long roomId, SseEmitter emitter) {
        roomEmitters
                .computeIfAbsent(roomId, id -> ConcurrentHashMap.newKeySet())
                .add(emitter);
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
    private void unregister(Long roomId, SseEmitter emitter) {
        roomEmitters.compute(roomId, (id, emitters) -> {
            if (emitters == null) {
                return null;
            }

            emitters.remove(emitter);

            return emitters.isEmpty() ? null : emitters;
        });
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
     * 끊긴 연결은 write를 시도할 때만 드러난다. 주기적으로 emitter 마다 heartbeat 전송을 VT 에
     * 맡기고, 실패하면 {@link #send} 가 emitter 생명주기를 통해 연결을 정리한다.
     *
     * <p>heartbeat 는 버퍼 ID 없이 comment 만 보내므로 {@code Last-Event-ID}에 영향을 주지
     * 않는다.
     */
    @Scheduled(fixedRateString = "${upbid.sse.heartbeat-interval-ms}")
    public void sendHeartbeat() {
        sseMetrics.recordHeartbeat(() ->
                roomEmitters.forEach((roomId, emitters) -> {
                    for (SseEmitter emitter : emitters) {
                        sseVirtualThreadExecutor.execute(() -> ping(roomId, emitter));
                    }
                }));
    }

    private void ping(Long roomId, SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().comment("keep-alive"));

        } catch (IOException | IllegalStateException e) {
            log.debug("sse heartbeat 실패로 연결 정리: roomId={}, cause={}", roomId, cause(e));
            completeWithError(emitter, e);

        } catch (RuntimeException e) {
            log.warn("sse heartbeat 실패로 연결 종료: roomId={}", roomId, e);
            completeWithError(emitter, e);
        }
    }

    /**
     * 경매방 종료 처리.
     *
     * <p>단순히 emitter를 Map에서 제거하면 HTTP 연결은 살아있고 EventSource가 자동 재연결할 수
     * 있으므로 {@code complete()}를 호출한다.
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
            closing.forEach(this::complete);
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
}
