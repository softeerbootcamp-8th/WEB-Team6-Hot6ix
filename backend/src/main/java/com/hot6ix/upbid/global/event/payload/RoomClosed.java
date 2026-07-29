package com.hot6ix.upbid.global.event.payload;

import com.hot6ix.upbid.global.event.EventType;
import com.hot6ix.upbid.global.event.RoomEvent;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

// 경매방 종료 이벤트
public record RoomClosed(
        String eventId,
        EventType type,
        Long roomId,
        LocalDateTime occurredAt,
        String roomTitle
) implements RoomEvent {

    public RoomClosed {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(roomTitle, "roomTitle");
    }

    public static RoomClosed of(Long roomId, String roomTitle, LocalDateTime occurredAt) {
        return new RoomClosed(UUID.randomUUID().toString(), EventType.ROOM_CLOSED,
                roomId, occurredAt, roomTitle);
    }
}
