package com.hot6ix.upbid.domain.sse.dto;

public record ItemEndedDto(
        Long itemId,
        String itemName,
        Long finalPrice,
        String winnerNickname,
        String winnerBidderKey,
        Long revision
) {
    public ItemEndedDto(
            Long itemId, String itemName, Long finalPrice, String winnerNickname) {
        this(itemId, itemName, finalPrice, winnerNickname, null, 0L);
    }
}
