package com.hot6ix.upbid.domain.auction.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

/**
 * 물품 경매 시작(POST /api/v1/auction-items/{auctionItemId}/start) 요청 바디.
 *
 * <p>상한 30일은 정책이자 마감 스케줄러의 안전장치다. {@code TaskScheduler}가 지연을
 * {@code long} 나노초로 들고 있어 약 292년을 넘기면 오버플로한다.
 */
@Builder
public record AuctionItemStartRequestDto(

        @NotNull(message = "경매 시간은 필수 값입니다.")
        @Min(value = 1, message = "경매 시간은 1분 이상이어야 합니다.")
        @Max(value = 43_200, message = "경매 시간은 43200분(30일) 이하여야 합니다.")
        Integer durationMinutes
) {
}
