package com.hot6ix.upbid.domain.auction.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

/**
 * 경매방 물품 추가(POST /api/v1/auction-rooms/{auctionRoomId}/auction-items) 요청 바디.
 * 입찰 단위는 경매방 값을 복사하므로 여기서 받지 않는다.
 */
@Builder
public record AuctionItemAddRequestDto(

        @NotNull(message = "상품 ID는 필수 값입니다.")
        Long productId,

        @NotNull(message = "시작가는 필수 값입니다.")
        @Min(value = 1, message = "시작가는 1원 이상이어야 합니다.")
        @Max(value = 1_000_000_000_000L, message = "시작가는 1조원 이하여야 합니다.")
        Long startingPrice
) {
}
