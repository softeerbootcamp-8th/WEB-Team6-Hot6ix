package com.hot6ix.upbid.global.event.payload;

import com.hot6ix.upbid.global.event.EventType;
import com.hot6ix.upbid.global.event.ItemEvent;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

// 물품 마감 임박 이벤트 (마감 1분 전)
public record ItemClosingSoon(
        String eventId,
        EventType type,
        Long roomId,
        Long itemId,
        LocalDateTime occurredAt,
        String itemName
) implements ItemEvent {

    public ItemClosingSoon {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(itemName, "itemName");
    }

    public static ItemClosingSoon of(Long roomId, Long itemId, String itemName, LocalDateTime occurredAt) {
        return new ItemClosingSoon(UUID.randomUUID().toString(), EventType.ITEM_CLOSING_SOON,
                roomId, itemId, occurredAt, itemName);
    }
}
