package com.hot6ix.upbid.domain.sse.dto;

import java.time.LocalDateTime;

public record ItemCloseAdvancedDto(
        Long itemId,
        String itemName,
        int remainingSeconds,
        LocalDateTime endedTime,
        Long revision
) {
    public ItemCloseAdvancedDto(
            Long itemId, String itemName, int remainingSeconds, LocalDateTime endedTime) {
        this(itemId, itemName, remainingSeconds, endedTime, 0L);
    }
}
