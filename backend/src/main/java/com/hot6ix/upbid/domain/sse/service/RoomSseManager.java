package com.hot6ix.upbid.domain.sse.service;

import com.hot6ix.upbid.domain.sse.dto.ParticipantCountDto;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Component
public class RoomSseManager {

    private static final long TIMEOUT = 60 * 60 * 1000L;
    private static final String PARTICIPANT_COUNT_EVENT = "PARTICIPANT_COUNT_UPDATED";

    private final Map<Long, List<SseEmitter>> roomEmitters = new HashMap<>();

    public SseEmitter subscribe(String name, Long roomId, Object data) {
        SseEmitter emitter = new SseEmitter(TIMEOUT);

        List<SseEmitter> emitters = roomEmitters.computeIfAbsent(roomId, key -> new ArrayList<>());

        emitters.add(emitter);

        emitter.onCompletion(() -> disconnect(roomId, emitter));
        emitter.onTimeout(() -> disconnect(roomId, emitter));
        emitter.onError(e -> disconnect(roomId, emitter));

        send(emitter, name, data);
        broadcastParticipantCount(roomId);

        log.info("sse 연결 완료: roomId={}", roomId);

        return emitter;
    }

    public void sendBroadCast(String name, Long roomId, Object data) {
        List<SseEmitter> emitters = roomEmitters.get(roomId);

        if (emitters == null) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            send(emitter, name, data);
        }
    }

    public int getParticipantCount(Long roomId) {
        List<SseEmitter> emitters = roomEmitters.get(roomId);
        return emitters == null ? 0 : emitters.size();
    }

    private void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter
                    .event()
                    .name(name)
                    .data(data)
            );
            log.info("sse 전송 완료: name={}", name);
        } catch (IOException e) {
            log.warn("sse 전송 실패: name={}", name, e);
            emitter.completeWithError(e);
        }
    }

    private void disconnect(Long roomId, SseEmitter emitter) {
        List<SseEmitter> emitters = roomEmitters.get(roomId);

        if (emitters != null) {
            emitters.remove(emitter);

            if (emitters.isEmpty()) {
                roomEmitters.remove(roomId);
            }
        }

        log.info("sse 연결 종료: roomId={}", roomId);
        broadcastParticipantCount(roomId);
    }

    private void broadcastParticipantCount(Long roomId) {
        sendBroadCast(PARTICIPANT_COUNT_EVENT, roomId, new ParticipantCountDto(getParticipantCount(roomId)));
    }
}
