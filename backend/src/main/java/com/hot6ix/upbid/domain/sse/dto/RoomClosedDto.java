package com.hot6ix.upbid.domain.sse.dto;

import java.time.OffsetDateTime;

public record RoomClosedDto(
        String roomTitle,
        OffsetDateTime closedTime
) {
}
