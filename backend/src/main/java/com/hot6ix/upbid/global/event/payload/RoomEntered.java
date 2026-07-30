package com.hot6ix.upbid.global.event.payload;

import com.hot6ix.upbid.global.event.EventType;
import com.hot6ix.upbid.global.event.RoomEvent;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

// 경매방 입장 이벤트
public record RoomEntered(
        String eventId,
        EventType type,
        Long roomId,
        LocalDateTime occurredAt,
        int participantCount
) implements RoomEvent {

    public RoomEntered {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static RoomEntered of(Long roomId, int participantCount, LocalDateTime occurredAt) {
        return new RoomEntered(UUID.randomUUID().toString(), EventType.ROOM_ENTERED,
                roomId, occurredAt, participantCount);
    }
}
