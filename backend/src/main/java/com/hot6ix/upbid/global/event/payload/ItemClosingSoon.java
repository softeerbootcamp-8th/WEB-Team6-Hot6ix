package com.hot6ix.upbid.global.event.payload;

import com.hot6ix.upbid.global.event.EventType;
import com.hot6ix.upbid.global.event.ItemEvent;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 물품 마감 임박 이벤트. 경매방의 {@code soft_close_trigger_seconds}만큼 남았을 때 나간다.
 * 곧 <b>이 이벤트가 나가는 순간부터가 Soft Close 연장 구간</b>이다.
 *
 * <p>{@code remainingSeconds}는 그 트리거 값 그대로이며, 방마다 다르기 때문에 싣는다. 화면 문구를
 * 서버가 만들지 않고 값만 내리는 것은 {@code SoftCloseExtended}의 {@code extendSeconds}와 같은
 * 이유다. 실제로 남은 시간을 재서 넣지 않는 것은, 그러면 스케줄러가 몇십 밀리초 늦게 깰 때마다
 * "59초 전" 같은 문구가 나오기 때문이다.
 */
public record ItemClosingSoon(
        String eventId,
        EventType type,
        Long roomId,
        Long itemId,
        LocalDateTime occurredAt,
        String itemName,
        int remainingSeconds
) implements ItemEvent {

    public ItemClosingSoon {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(itemName, "itemName");
    }

    public static ItemClosingSoon of(Long roomId, Long itemId, String itemName,
                                     int remainingSeconds, LocalDateTime occurredAt) {
        return new ItemClosingSoon(UUID.randomUUID().toString(), EventType.ITEM_CLOSING_SOON,
                roomId, itemId, occurredAt, itemName, remainingSeconds);
    }
}
