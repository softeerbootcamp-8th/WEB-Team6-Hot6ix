package com.hot6ix.upbid.domain.product.dto.response;

import com.hot6ix.upbid.domain.product.entity.ProductListingStatus;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record ProductSummaryResponseDto(
        Long productId,
        String name,
        String imageUrl,
        ProductListingStatus status,
        LocalDateTime createdAt
) {
}
