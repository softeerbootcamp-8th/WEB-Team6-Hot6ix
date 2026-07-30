package com.hot6ix.upbid.global.event.payload;

import com.hot6ix.upbid.global.event.EventType;
import com.hot6ix.upbid.global.event.ItemEvent;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

//유찰 마감 이벤트
public record ItemPassed(
        String eventId,
        EventType type,
        Long roomId,
        Long itemId,
        LocalDateTime occurredAt
) implements ItemEvent {

    public ItemPassed {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static ItemPassed of(Long roomId, Long itemId, LocalDateTime occurredAt) {
        return new ItemPassed(UUID.randomUUID().toString(), EventType.ITEM_PASSED,
                roomId, itemId, occurredAt);
    }
}
