package com.hot6ix.upbid.domain.bid.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

@Builder
public record BidCreateRequestDto(

        @NotNull(message = "입찰 금액은 필수 값입니다.")
        @Positive(message = "입찰 금액은 0보다 커야 합니다.")
        Long amount
) {
}
