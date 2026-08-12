package com.hot6ix.upbid.domain.sse.event;

import com.hot6ix.upbid.domain.sse.service.RoomSseManager;
import com.hot6ix.upbid.domain.sse.service.SseEventBuffer;
import com.hot6ix.upbid.global.event.EventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis 채널에서 SSE 이벤트를 받아 해당 인스턴스에 있는 커넥션으로 보낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseEventSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final RoomSseManager roomSseManager;
    private final SseEventBuffer sseEventBuffer;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            deliver(objectMapper.readValue(message.getBody(), SseEventMessage.class));
        } catch (RuntimeException e) {
            log.error("sse 이벤트 수신 처리 실패", e);
        }
    }

    private void deliver(SseEventMessage event) {
        sseEventBuffer.add(
                event.roomId(), event.id(), event.eventName(), event.data(), event.occurredAt());

        roomSseManager.deliverLocal(event.roomId(), event.eventName(), event.id(), event.data());

        if (EventType.ROOM_CLOSED.name().equals(event.eventName())) {
            roomSseManager.closeRoom(event.roomId());
        }
    }
}
