package com.hot6ix.upbid.global.event.message;

import com.hot6ix.upbid.global.event.DomainEvent;
import com.hot6ix.upbid.global.event.ItemEvent;
import com.hot6ix.upbid.global.event.RoomEvent;
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
