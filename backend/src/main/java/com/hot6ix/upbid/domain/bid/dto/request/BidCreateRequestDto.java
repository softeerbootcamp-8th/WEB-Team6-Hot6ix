package com.hot6ix.upbid.domain.bid.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

@Builder
public record BidCreateRequestDto(

        @NotNull(message = "입찰 금액은 필수 값입니다.")
        @Positive(message = "입찰 금액은 0보다 커야 합니다.")
        @Max(value = 5_000_000_000_000L, message = "입찰 가능한 금액 범위를 초과했어요")
        Long amount
) {
}
