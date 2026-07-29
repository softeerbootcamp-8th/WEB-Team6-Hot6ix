package com.hot6ix.upbid.global.event.payload;

import com.hot6ix.upbid.global.event.EventType;
import com.hot6ix.upbid.global.event.ItemEvent;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

// 물품 경매 시작 이벤트
public record ItemStarted(
        String eventId,
        EventType type,
        Long roomId,
        Long itemId,
        LocalDateTime occurredAt,
        String itemName
) implements ItemEvent {

    public ItemStarted {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(itemName, "itemName");
    }

    public static ItemStarted of(Long roomId, Long itemId, String itemName, LocalDateTime occurredAt) {
        return new ItemStarted(UUID.randomUUID().toString(), EventType.ITEM_STARTED,
                roomId, itemId, occurredAt, itemName);
    }
}
