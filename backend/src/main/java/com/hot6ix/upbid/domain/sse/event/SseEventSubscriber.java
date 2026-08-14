package com.hot6ix.upbid.domain.sse.event;

import com.hot6ix.upbid.domain.sse.service.RoomSseManager;
import com.hot6ix.upbid.global.event.EventType;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis 채널에서 SSE 이벤트를 받아 해당 인스턴스에 있는 커넥션으로 보낸다.
 *
 * <p><b>버퍼에는 쓰지 않는다.</b> 발행 스크립트가 이미 넣었다. 여기서 또 넣으면 인스턴스 수만큼
 * 같은 값을 덮어쓰게 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseEventSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final RoomSseManager roomSseManager;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            deliver(SseEventEnvelope.decode(
                    new String(message.getBody(), StandardCharsets.UTF_8), objectMapper));

        } catch (RuntimeException e) {
            log.error("sse 이벤트 수신 처리 실패", e);
        }
    }

    private void deliver(SseEventEnvelope envelope) {
        SseEventMessage event = envelope.message();

        roomSseManager.deliverLocal(event.roomId(), event.eventName(), envelope.id(), event.data());

        if (EventType.ROOM_CLOSED.name().equals(event.eventName())) {
            roomSseManager.closeRoom(event.roomId());
        }
    }
}
