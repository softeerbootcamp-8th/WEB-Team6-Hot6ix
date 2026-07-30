package com.hot6ix.upbid.global.event.payload;

import com.hot6ix.upbid.global.event.EventType;
import com.hot6ix.upbid.global.event.ItemEvent;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

// Soft Close 발동(마감 연장) 이벤트
public record SoftCloseExtended(
        String eventId,
        EventType type,
        Long roomId,
        Long itemId,
        LocalDateTime occurredAt,
        String itemName,
        int extendSeconds
) implements ItemEvent {

    public SoftCloseExtended {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(itemName, "itemName");
    }

    public static SoftCloseExtended of(Long roomId, Long itemId, String itemName, int extendSeconds,
                                       LocalDateTime occurredAt) {
        return new SoftCloseExtended(UUID.randomUUID().toString(), EventType.SOFT_CLOSE_EXTENDED,
                roomId, itemId, occurredAt, itemName, extendSeconds);
    }
}
