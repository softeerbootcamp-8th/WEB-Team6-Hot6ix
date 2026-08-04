package com.hot6ix.upbid.domain.sse.service;

import com.hot6ix.upbid.domain.sse.dto.ParticipantCountDto;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Component
public class RoomSseManager {

    private static final long TIMEOUT = 60 * 60 * 1000L;
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

        log.info("sse 연결 완료: roomId={}", roomId);

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
}
