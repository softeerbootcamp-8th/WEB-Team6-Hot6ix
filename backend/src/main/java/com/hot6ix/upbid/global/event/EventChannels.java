package com.hot6ix.upbid.global.event;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EventChannels {
    public static String of(DomainEvent event) {
        return switch (event) {
            case ItemEvent item -> "room:" + item.roomId() + ":item:" + item.itemId();
            case RoomEvent room -> "room:" + room.roomId();
        };
    }
}
