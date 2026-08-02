package com.hot6ix.upbid.domain.sse.dto;

public record BidPlacedDto(
        Long itemId,
        String itemName,
        Long bidPrice,
        String bidderNickname
) {
}
