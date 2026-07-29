package com.hot6ix.upbid.global.event;

import java.time.LocalDateTime;

public sealed interface DomainEvent permits RoomEvent, ItemEvent {
    String eventId();
    EventType type();
    Long roomId();
    LocalDateTime occurredAt();
}
