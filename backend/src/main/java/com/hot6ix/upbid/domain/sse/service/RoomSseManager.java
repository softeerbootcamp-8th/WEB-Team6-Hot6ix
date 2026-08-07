package com.hot6ix.upbid.domain.sse.service;

import com.hot6ix.upbid.domain.sse.dto.ParticipantCountDto;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Component
public class RoomSseManager {

    private static final long TIMEOUT = 60 * 60 * 1000L;
    private static final long HEARTBEAT_INTERVAL_MS = 30 * 1000L;
    private static final String PARTICIPANT_COUNT_EVENT = "PARTICIPANT_COUNT_UPDATED";

    private final Map<Long, Set<SseEmitter>> roomEmitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String name, Long roomId, Object data) {
        SseEmitter emitter = new SseEmitter(TIMEOUT);

        register(roomId, emitter);

        emitter.onCompletion(() -> disconnect(roomId, emitter));
        emitter.onTimeout(() -> disconnect(roomId, emitter));
        emitter.onError(e -> disconnect(roomId, emitter));

        send(roomId, emitter, name, data);
        broadcastParticipantCount(roomId);

        log.debug("sse 연결 완료: roomId={}", roomId);

        return emitter;
    }

    public void sendBroadCast(String name, Long roomId, Object data) {
        Set<SseEmitter> emitters = roomEmitters.get(roomId);

        if (emitters == null) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            send(roomId, emitter, name, data);
        }
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

        log.debug("sse 연결 종료: roomId={}", roomId);
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
     */
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS)
    public void sendHeartbeat() {
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
}
