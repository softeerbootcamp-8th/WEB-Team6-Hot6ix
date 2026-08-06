package com.hot6ix.upbid.global.event.payload;

import com.hot6ix.upbid.global.event.EventType;
import com.hot6ix.upbid.global.event.ItemEvent;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Soft Close 발동(마감 연장) 이벤트.
 *
 * <p>{@code endAt}은 <b>연장이 반영된 뒤의 마감 시각</b>이다. 화면은 이 값으로 카운트다운을
 * 다시 맞추고, 스케줄러는 이 값으로 마감 예약을 갈아 끼운다. {@code extendSeconds}를 더해
 * 계산하지 않고 절대값을 싣는 것은, 이벤트가 유실되거나 중복으로 도착해도 받는 쪽이 같은
 * 마감 시각에 도달하게 하기 위해서다.
 */
public record SoftCloseExtended(
        String eventId,
        EventType type,
        Long roomId,
        Long itemId,
        LocalDateTime occurredAt,
        String itemName,
        int extendSeconds,
        LocalDateTime endAt
) implements ItemEvent {

    public SoftCloseExtended {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(itemName, "itemName");
        Objects.requireNonNull(endAt, "endAt");
    }

    public static SoftCloseExtended of(Long roomId, Long itemId, String itemName, int extendSeconds,
                                       LocalDateTime endAt, LocalDateTime occurredAt) {
        return new SoftCloseExtended(UUID.randomUUID().toString(), EventType.SOFT_CLOSE_EXTENDED,
                roomId, itemId, occurredAt, itemName, extendSeconds, endAt);
    }
}
