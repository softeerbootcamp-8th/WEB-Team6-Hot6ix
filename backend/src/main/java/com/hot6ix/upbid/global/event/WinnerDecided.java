package com.hot6ix.upbid.global.event;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

// 낙찰자, 낙찰가 확정 이벤트
public record WinnerDecided(
        String eventId,
        EventType type,
        Long roomId,
        Long itemId,
        LocalDateTime occurredAt,
        Long winnerUserId,
        Long winningPrice
) implements ItemEvent {

    public WinnerDecided {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(winnerUserId, "winnerUserId");
        Objects.requireNonNull(winningPrice, "winningPrice");
    }

    public static WinnerDecided of(Long roomId, Long itemId, Long winnerUserId, Long winningPrice,
                                   LocalDateTime occurredAt) {
        return new WinnerDecided(UUID.randomUUID().toString(), EventType.WINNER_DECIDED,
                roomId, itemId, occurredAt, winnerUserId, winningPrice);
    }
}
