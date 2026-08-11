package com.hot6ix.upbid.domain.sse.service;

import com.hot6ix.upbid.domain.sse.config.SseProperties;
import com.hot6ix.upbid.domain.sse.dto.ParticipantCountDto;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

    private final SseProperties sseProperties;
    /** 계측은 이 객체가 안다. 이 클래스는 Micrometer 를 모른다. */
    private final SseMetrics sseMetrics;
    private final SseEventBuffer sseEventBuffer;

    /**
     * 지금 열려 있는 연결 수를 지표로 내보낸다. 게이지는 스크랩할 때 위 Map 을 읽어 가는
     * 방식이라, 연결을 등록·해제하는 코드에는 계측이 안 들어간다.
     *
     * <p>{@code @PostConstruct} 를 여기 쓰는 건 괜찮다. 테스트가 이 클래스를 직접 생성하면
     * 이 콜백이 안 불려서 게이지가 안 붙지만, 그뿐이고 동작이 깨지지는 않는다. <b>값을 필드에
     * 담는 용도로는 쓰지 않는다</b> — 그러면 직접 생성한 객체에서 null 이 되어 터진다.
     */
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
        SseEmitter emitter = new SseEmitter(sseProperties.emitterTimeoutMs());

        register(roomId, emitter);

        emitter.onCompletion(() -> disconnect(roomId, emitter));
        emitter.onTimeout(() -> disconnect(roomId, emitter));
        emitter.onError(e -> disconnect(roomId, emitter));

        if (lastEventId != null) {
            List<BufferedEvent> missed = sseEventBuffer.getEventsAfter(roomId, lastEventId);
            for (BufferedEvent event : missed) {
                send(roomId, emitter, event.eventName(), event.id(), event.data());
            }
        }

        broadcastParticipantCount(roomId);

        log.info("sse 연결 완료: roomId={}, lastEventId={}", roomId, lastEventId);

        return emitter;
    }

    /**
     * 방에 붙어 있는 모든 연결에 이벤트를 쏜다.
     *
     * <p>지금은 부르는 스레드가 직접 다 쏘고 나서 돌아간다. 입찰을 처리한 톰캣 스레드가
     * 커밋 뒤에 이 메서드를 부르므로, 여기 걸린 시간이 그대로 입찰 응답 시간에 얹힌다.
     * {@code upbid.sse.broadcast}로 그 비용을 잰다(#234).
     *
     * <p>쏠 대상이 없으면 재지 않는다. 0에 가까운 값이 히스토그램에 섞이면 p95가 실제보다
     * 낮게 나온다.
     */
    public void sendBroadCast(String name, Long roomId, Object data) {
        Set<SseEmitter> emitters = roomEmitters.get(roomId);

        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        long id = sseEventBuffer.add(roomId, name, data);

        sseMetrics.recordBroadcast(name, () -> {
            for (SseEmitter emitter : emitters) {
                send(roomId, emitter, name, id, data);
            }
        });
    }

    public int getParticipantCount(Long roomId) {
        Set<SseEmitter> emitters = roomEmitters.get(roomId);
        return emitters == null ? 0 : emitters.size();
    }

    /**
     * @param id 버퍼에 저장된 순차 ID. 클라이언트는 이 값을 {@code Last-Event-ID}로 저장해
     *           재연결 시 서버에 보낸다. 초기 이벤트가 없어진 뒤로 ID 없이 나가는 이벤트는 없다.
     */
    private void send(Long roomId, SseEmitter emitter, String name, long id, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(id))
                    .name(name)
                    .data(data));
        } catch (IOException | IllegalStateException e) {
            unregister(roomId, emitter);
            log.debug("sse 전송 중 끊긴 연결 정리: roomId={}, name={}, remaining={}, cause={}",
                    roomId, name, getParticipantCount(roomId), cause(e));
            emitter.completeWithError(e);
        }
    }

    /**
     * 끊긴 연결은 장애가 아니라 정상적인 종료다. 스택트레이스를 남기면 봐야 할 로그가 묻히므로
     * 예외 종류와 메시지만 남긴다. Broken pipe인지 Connection reset인지, 아니면 이미 완료된
     * emitter였는지는 이 한 줄로 구분된다.
     */
    private String cause(Exception e) {
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    private void disconnect(Long roomId, SseEmitter emitter) {
        unregister(roomId, emitter);

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

    private void broadcastParticipantCount(Long roomId) {
        sendBroadCast(PARTICIPANT_COUNT_EVENT, roomId, new ParticipantCountDto(getParticipantCount(roomId)));
    }

    /**
     * 끊긴 연결은 write를 시도할 때만 드러난다.
     * 주기적으로 ping을 보내 끊긴 연결을 걷어내고
     * 동시에 프록시, 로드밸런서의 idle timeout(통상 60초)에 연결이 끊기는 것을 방지한다.
     *
     * <p>이 작업은 물품 마감 예약과 {@code spring.task.scheduling.pool}을 같이 쓴다. 접속이
     * 많아 한 바퀴가 길어지면 마감이 밀릴 수 있어서, 한 바퀴에 걸린 시간을
     * {@code upbid.sse.heartbeat}로 잰다.
     */
    @Scheduled(fixedRateString = "${upbid.sse.heartbeat-interval-ms}")
    public void sendHeartbeat() {
        sseMetrics.recordHeartbeat(() -> {
            Set<Long> sweptRooms = ConcurrentHashMap.newKeySet();

            roomEmitters.forEach((roomId, emitters) -> {
                for (SseEmitter emitter : emitters) {
                    if (!ping(roomId, emitter)) {
                        sweptRooms.add(roomId);
                    }
                }
            });

            // 방마다 새롭게 업데이트 된 사용자 수를 알린다.
            sweptRooms.forEach(this::broadcastParticipantCount);
        });
    }

    /**
     * Heartbeat 전송.
     *
     * 클라이언트가 브라우저를 종료하거나 네트워크가 단절된 경우
     * 실제 write 시점에 IOException(Broken pipe, Connection reset 등)이 발생할 수 있다.
     *
     * 또한 이미 완료(completed)된 emitter에 전송을 시도하면
     * IllegalStateException이 발생할 수 있다.
     *
     * 예외 발생 시 해당 emitter를 제거하여 죽은 연결을 정리한다.
     *
     * @return 살아 있으면 true, 걷어냈으면 false
     */
    private boolean ping(Long roomId, SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().comment("keep-alive"));
            return true;
        } catch (IOException | IllegalStateException e) {
            unregister(roomId, emitter);
            log.debug("sse heartbeat 중 끊긴 연결 정리: roomId={}, remaining={}, cause={}",
                    roomId, getParticipantCount(roomId), cause(e));
            emitter.completeWithError(e);
            return false;
        }
    }

    /**
     * 경매방 종료 처리.
     *
     * <p>모든 SSE 연결을 종료하고 이벤트 버퍼를 비운다.
     * 단순히 emitter를 Map에서 제거하면 HTTP 연결은 살아있고
     * EventSource가 자동 재연결할 수 있으므로 {@code complete()}를 호출한다.
     *
     * <p>종료된 방에 대한 재연결은 구독 시점의 방 상태 검증으로 차단한다.
     */
    public void closeRoom(Long roomId) {
        Set<SseEmitter> closing = roomEmitters.remove(roomId);

        if (closing != null) {
            closing.forEach(this::complete);
        }

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
