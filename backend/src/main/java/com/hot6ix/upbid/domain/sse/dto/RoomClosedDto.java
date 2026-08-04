package com.hot6ix.upbid.domain.sse.dto;

import java.time.LocalDateTime;

public record RoomClosedDto(
        String roomTitle,
        LocalDateTime closedTime
) {
}
