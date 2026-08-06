package com.hot6ix.upbid.global.event.payload;

import com.hot6ix.upbid.global.event.EventType;
import com.hot6ix.upbid.global.event.ItemEvent;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 경매방에서 물품이 빠진 이벤트. {@link ItemAdded}와 같이 <b>목록을 다시 읽으라는 신호</b>이며,
 * 빠진 물품의 이름 같은 표시용 값은 싣지 않는다.
 *
 * <p>제외는 늘 물품 하나 단위라 {@code itemId}가 있고, 그래서 {@code ItemEvent}다. 다만 화면은
 * 이 값으로 목록에서 한 줄을 지우지 않고 목록을 통째로 다시 읽는다 — 지운 뒤 서버와 어긋나면
 * 되돌릴 방법이 없기 때문이다.
 */
public record ItemRemoved(
        String eventId,
        EventType type,
        Long roomId,
        Long itemId,
        LocalDateTime occurredAt
) implements ItemEvent {

    public ItemRemoved {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static ItemRemoved of(Long roomId, Long itemId, LocalDateTime occurredAt) {
        return new ItemRemoved(UUID.randomUUID().toString(), EventType.ITEM_REMOVED,
                roomId, itemId, occurredAt);
    }
}
