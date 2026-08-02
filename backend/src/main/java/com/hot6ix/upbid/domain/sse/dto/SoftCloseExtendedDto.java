package com.hot6ix.upbid.domain.sse.dto;

import java.time.LocalDateTime;

public record SoftCloseExtendedDto(
        Long itemId,
        String itemName,
        int extendSeconds,
        LocalDateTime endedTime
) {
}
