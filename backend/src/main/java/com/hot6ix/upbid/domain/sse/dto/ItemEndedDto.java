package com.hot6ix.upbid.domain.sse.dto;

public record ItemEndedDto(
        Long itemId,
        String itemName,
        Long finalPrice,
        String winnerNickname
) {
}
