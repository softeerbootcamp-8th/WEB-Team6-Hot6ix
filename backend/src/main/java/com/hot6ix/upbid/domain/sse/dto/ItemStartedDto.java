package com.hot6ix.upbid.domain.sse.dto;

import java.time.OffsetDateTime;

public record ItemStartedDto(
        Long itemId,
        String itemName,
        OffsetDateTime endedTime
) {
}
