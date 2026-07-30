package com.hot6ix.upbid.global.event.payload;

import com.hot6ix.upbid.global.event.EventType;
import com.hot6ix.upbid.global.event.ItemEvent;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

// 물품 낙찰 이벤트
public record ItemEnded(
        String eventId,
        EventType type,
        Long roomId,
        Long itemId,
        LocalDateTime occurredAt,
        String itemName,
        Long finalPrice,
        String winnerNickname
) implements ItemEvent {

    public ItemEnded {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(itemName, "itemName");
        Objects.requireNonNull(finalPrice, "finalPrice");
        Objects.requireNonNull(winnerNickname, "winnerNickname");
    }

    public static ItemEnded of(Long roomId, Long itemId, String itemName, Long finalPrice,
                               String winnerNickname, LocalDateTime occurredAt) {
        return new ItemEnded(UUID.randomUUID().toString(), EventType.ITEM_ENDED,
                roomId, itemId, occurredAt, itemName, finalPrice, winnerNickname);
    }
}
