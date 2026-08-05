package com.hot6ix.upbid.global.event.payload;

import com.hot6ix.upbid.global.event.EventType;
import com.hot6ix.upbid.global.event.RoomEvent;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 판매자가 경매방 설정을 바꾼 이벤트.
 *
 * <p>{@link ItemAdded}와 같이 <b>방 정보를 다시 읽으라는 신호</b>이고 바뀐 값을 싣지 않는다.
 * 어떤 필드가 바뀌었는지 알리기 시작하면 payload가 수정 가능한 필드 목록을 따라다녀야 하는데,
 * 화면은 어차피 방 정보 전체를 한 번에 그린다.
 *
 * <p>이게 없으면 판매자가 방 이름을 고쳐도 <b>이미 방에 들어와 있는 구매자 화면은 새로고침
 * 전까지 옛 이름을 계속 보여준다</b> — 고친 본인 화면만 바뀌어서 눈치채기 어렵다.
 */
public record RoomUpdated(
        String eventId,
        EventType type,
        Long roomId,
        LocalDateTime occurredAt
) implements RoomEvent {

    public RoomUpdated {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static RoomUpdated of(Long roomId, LocalDateTime occurredAt) {
        return new RoomUpdated(UUID.randomUUID().toString(), EventType.ROOM_UPDATED, roomId, occurredAt);
    }
}
