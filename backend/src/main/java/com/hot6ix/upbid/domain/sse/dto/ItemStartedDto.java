package com.hot6ix.upbid.domain.sse.dto;

import java.time.LocalDateTime;

public record ItemStartedDto(
        Long itemId,
        String itemName,
        LocalDateTime endedTime,
        Long revision
) {
    public ItemStartedDto(Long itemId, String itemName, LocalDateTime endedTime) {
        this(itemId, itemName, endedTime, 0L);
    }
}
