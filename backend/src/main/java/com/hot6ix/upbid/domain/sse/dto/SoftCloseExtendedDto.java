package com.hot6ix.upbid.domain.sse.dto;

import java.time.LocalDateTime;

public record SoftCloseExtendedDto(
        Long itemId,
        String itemName,
        int extendSeconds,
        LocalDateTime endedTime,
        Long revision
) {
    public SoftCloseExtendedDto(
            Long itemId, String itemName, int extendSeconds, LocalDateTime endedTime) {
        this(itemId, itemName, extendSeconds, endedTime, 0L);
    }
}
