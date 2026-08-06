package com.hot6ix.upbid.global.event.payload;

import com.hot6ix.upbid.global.event.EventType;
import com.hot6ix.upbid.global.event.RoomEvent;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 경매방에 물품이 추가된 이벤트.
 *
 * <p><b>목록을 다시 읽으라는 신호일 뿐, 물품 자체를 싣지 않는다.</b> {@code ItemStarted}가
 * {@code endAt}까지 담아 조회를 아끼는 것과 반대인데, 이유는 판매자 화면이 이미 추가 직후
 * 목록을 다시 읽고 있어서다. 같은 경로를 구독자도 쓰면 판매자·구매자 화면이 한 흐름으로
 * 합쳐지고, 이벤트로 받은 물품과 조회로 받은 물품이 겹쳐 두 번 들어가는 문제가 아예 생기지
 * 않는다.
 *
 * <p>그래서 {@code ItemEvent}가 아니라 {@code RoomEvent}다 — 벌크 추가는 물품 하나를
 * 가리키지 않는다.
 *
 * @param addedCount 이번에 추가된 물품 수. 벌크 추가도 이벤트는 하나이며, 물품마다 보내면
 *                   구독자가 그 수만큼 목록을 다시 읽는다
 */
public record ItemAdded(
        String eventId,
        EventType type,
        Long roomId,
        LocalDateTime occurredAt,
        int addedCount
) implements RoomEvent {

    public ItemAdded {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static ItemAdded of(Long roomId, LocalDateTime occurredAt, int addedCount) {
        return new ItemAdded(UUID.randomUUID().toString(), EventType.ITEM_ADDED,
                roomId, occurredAt, addedCount);
    }
}
