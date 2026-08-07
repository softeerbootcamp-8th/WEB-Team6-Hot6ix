package com.hot6ix.upbid.domain.sse.dto;

import java.time.OffsetDateTime;

public record SoftCloseExtendedDto(
        Long itemId,
        String itemName,
        int extendSeconds,
        OffsetDateTime endedTime
) {
}
