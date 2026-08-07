package com.hot6ix.upbid.domain.sse.service;

import com.hot6ix.upbid.domain.sse.config.SseProperties;
import com.hot6ix.upbid.domain.sse.dto.ParticipantCountDto;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
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

    private static final String PARTICIPANT_COUNT_EVENT = "PARTICIPANT_COUNT_UPDATED";

    private final Map<Long, Set<SseEmitter>> roomEmitters = new ConcurrentHashMap<>();

    private final SseProperties sseProperties;
    /** 계측은 이 객체가 안다. 이 클래스는 Micrometer 를 모른다. */
    private final SseMetrics sseMetrics;

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

    public SseEmitter subscribe(String name, Long roomId, Object data) {
        SseEmitter emitter = new SseEmitter(sseProperties.emitterTimeoutMs());

        register(roomId, emitter);

        emitter.onCompletion(() -> disconnect(roomId, emitter));
        emitter.onTimeout(() -> disconnect(roomId, emitter));
        emitter.onError(e -> disconnect(roomId, emitter));

        send(roomId, emitter, name, data);
        broadcastParticipantCount(roomId);

        log.info("sse 연결 완료: roomId={}", roomId);

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

        sseMetrics.recordBroadcast(() -> {
            for (SseEmitter emitter : emitters) {
                send(roomId, emitter, name, data);
            }
        });
    }

    public int getParticipantCount(Long roomId) {
        Set<SseEmitter> emitters = roomEmitters.get(roomId);
        return emitters == null ? 0 : emitters.size();
    }

    private void send(Long roomId, SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter
                    .event()
                    .name(name)
                    .data(data)
            );
        } catch (IOException | IllegalStateException e) {
            log.warn("sse 전송 실패: roomId={}, name={}", roomId, name, e);
            unregister(roomId, emitter);
            emitter.completeWithError(e);
        }
    }

    private void disconnect(Long roomId, SseEmitter emitter) {
        unregister(roomId, emitter);

        log.info("sse 연결 종료: roomId={}", roomId);
        broadcastParticipantCount(roomId);
    }

    private void register(Long roomId, SseEmitter emitter) {
        roomEmitters
                .computeIfAbsent(roomId, id -> ConcurrentHashMap.newKeySet())
                .add(emitter);
    }

    private void unregister(Long roomId, SseEmitter emitter) {
        Set<SseEmitter> emitters = roomEmitters.get(roomId);

        if (emitters != null) {
            emitters.remove(emitter);
        }
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
            log.warn("sse heartbeat 실패: roomId={}", roomId, e);
            unregister(roomId, emitter);
            emitter.completeWithError(e);
            return false;
        }
    }
}
