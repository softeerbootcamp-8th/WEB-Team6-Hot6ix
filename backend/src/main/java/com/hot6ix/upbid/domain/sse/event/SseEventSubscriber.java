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

    /**
     * 이 메서드는 Redis 구독 스레드에서 불린다. 예외를 올리면 그 스레드가 이벤트 하나 때문에
     * 흔들리므로, 한 건의 실패가 다음 이벤트 수신을 막지 않도록 여기서 끊는다.
     */
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

        // 방 종료는 마지막 이벤트다. 내보낸 뒤 이 인스턴스에 남은 연결을 끊는다. 인스턴스마다
        // 자기 연결만 알기 때문에, 발행한 쪽이 아니라 받은 쪽 전부가 각자 끊어야 한다.
        if (EventType.ROOM_CLOSED.name().equals(event.eventName())) {
            roomSseManager.closeRoom(event.roomId());
        }
    }
}
