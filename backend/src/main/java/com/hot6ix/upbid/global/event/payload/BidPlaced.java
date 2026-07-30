package com.hot6ix.upbid.global.event.payload;

import com.hot6ix.upbid.global.event.EventType;
import com.hot6ix.upbid.global.event.ItemEvent;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

// 물품 입찰 이벤트
public record BidPlaced(
        String eventId,
        EventType type,
        Long roomId,
        Long itemId,
        LocalDateTime occurredAt,
        String itemName,
        String bidderNickname,
        Long bidPrice
) implements ItemEvent {

    public BidPlaced {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(itemName, "itemName");
        Objects.requireNonNull(bidderNickname, "bidderNickname");
        Objects.requireNonNull(bidPrice, "bidPrice");
    }

    public static BidPlaced of(Long roomId, Long itemId, String itemName, String bidderNickname,
                               Long bidPrice, LocalDateTime occurredAt) {
        return new BidPlaced(UUID.randomUUID().toString(), EventType.BID_PLACED,
                roomId, itemId, occurredAt, itemName, bidderNickname, bidPrice);
    }
}
