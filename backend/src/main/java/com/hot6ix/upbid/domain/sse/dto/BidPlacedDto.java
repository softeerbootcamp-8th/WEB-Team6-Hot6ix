package com.hot6ix.upbid.domain.sse.dto;

public record BidPlacedDto(
        String itemName,
        Long bidPrice,
        String bidderNickname
) {
}
