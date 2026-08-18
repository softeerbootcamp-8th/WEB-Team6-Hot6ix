package com.hot6ix.upbid.domain.sse.dto;

import java.time.LocalDateTime;

public record BidPlacedDto(
        Long itemId,
        String itemName,
        Long bidPrice,
        String bidderNickname,
        String bidderKey,
        LocalDateTime endedTime,
        Long revision
) {
    public BidPlacedDto(Long itemId, String itemName, Long bidPrice, String bidderNickname) {
        this(itemId, itemName, bidPrice, bidderNickname, null, null, 0L);
    }
}
