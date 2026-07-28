package com.hot6ix.upbid.global.event;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

// 물품 경매 마감 이벤트
public record ItemEnded(
        String eventId,
        EventType type,
        Long roomId,
        Long itemId,
        LocalDateTime occurredAt,
        Long finalPrice
) implements ItemEvent {

    public ItemEnded {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static ItemEnded of(Long roomId, Long itemId, Long finalPrice, LocalDateTime occurredAt) {
        return new ItemEnded(UUID.randomUUID().toString(), EventType.ITEM_ENDED,
                roomId, itemId, occurredAt, finalPrice);
    }
}
