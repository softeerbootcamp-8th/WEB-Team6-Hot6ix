package com.hot6ix.upbid.global.event.payload;

import com.hot6ix.upbid.global.event.EventType;
import com.hot6ix.upbid.global.event.ItemEvent;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 물품 경매 시작 이벤트.
 *
 * <p>{@code endAt}은 구독자가 카운트다운을 바로 그릴 수 있게 하려고 싣는다. 이 값이 없으면
 * 이벤트를 받은 화면이 물품 상세를 한 번 더 조회해야 하고, 접속자가 많을수록 시작할 때마다
 * 그 조회가 몰린다.
 */
public record ItemStarted(
        String eventId,
        EventType type,
        Long roomId,
        Long itemId,
        LocalDateTime occurredAt,
        String itemName,
        LocalDateTime endAt
) implements ItemEvent {

    public ItemStarted {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(itemName, "itemName");
        Objects.requireNonNull(endAt, "endAt");
    }

    public static ItemStarted of(Long roomId, Long itemId, String itemName,
                                 LocalDateTime occurredAt, LocalDateTime endAt) {
        return new ItemStarted(UUID.randomUUID().toString(), EventType.ITEM_STARTED,
                roomId, itemId, occurredAt, itemName, endAt);
    }
}
