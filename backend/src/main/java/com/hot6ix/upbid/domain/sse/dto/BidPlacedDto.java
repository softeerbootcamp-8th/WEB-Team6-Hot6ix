package com.hot6ix.upbid.domain.sse.dto;

public record BidPlacedDto(
        Long itemId,
        String itemName,
        Long bidPrice,
        String bidderNickname,
        String bidderKey,
        Long revision
) {
    public BidPlacedDto(Long itemId, String itemName, Long bidPrice, String bidderNickname) {
        this(itemId, itemName, bidPrice, bidderNickname, null, 0L);
    }
}
