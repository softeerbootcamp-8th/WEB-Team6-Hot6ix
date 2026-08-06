package com.hot6ix.upbid.global.event.payload;

import com.hot6ix.upbid.global.event.EventType;
import com.hot6ix.upbid.global.event.ItemEvent;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 판매자가 마감을 앞당긴 이벤트. 마감 시각이 Soft Close 연장 구간이 열리는 순간으로 당겨졌다는
 * 뜻이라, <b>이 이벤트가 나가는 순간부터가 연장 구간</b>이다.
 *
 * <p>{@code SoftCloseExtended}와 나눠 두는 것은 방향이 반대이기 때문이다. 합치면 화면이 앞당김을
 * "+n초 연장"으로 그리고, 마감 예약을 미루는 쪽과 당기는 쪽을 로그에서도 구분할 수 없다.
 *
 * <p>{@code endAt}은 <b>앞당김이 반영된 뒤의 마감 시각</b>이며 절대값을 싣는 이유는
 * {@code SoftCloseExtended}와 같다. {@code remainingSeconds}는 그 시각까지 남은 초, 곧 경매방의
 * 트리거 값이라 화면이 이 값으로 문구를 만든다.
 */
public record ItemCloseAdvanced(
        String eventId,
        EventType type,
        Long roomId,
        Long itemId,
        LocalDateTime occurredAt,
        String itemName,
        int remainingSeconds,
        LocalDateTime endAt
) implements ItemEvent {

    public ItemCloseAdvanced {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(itemName, "itemName");
        Objects.requireNonNull(endAt, "endAt");
    }

    public static ItemCloseAdvanced of(Long roomId, Long itemId, String itemName,
                                       int remainingSeconds, LocalDateTime endAt,
                                       LocalDateTime occurredAt) {
        return new ItemCloseAdvanced(UUID.randomUUID().toString(), EventType.ITEM_CLOSE_ADVANCED,
                roomId, itemId, occurredAt, itemName, remainingSeconds, endAt);
    }
}
