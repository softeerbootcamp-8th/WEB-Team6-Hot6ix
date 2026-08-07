package com.hot6ix.upbid.domain.product.dto.response;

import com.hot6ix.upbid.domain.product.entity.ProductListingStatus;
import java.time.OffsetDateTime;
import lombok.Builder;

@Builder
public record ProductSummaryResponseDto(
        Long productId,
        String name,
        String imageUrl,
        ProductListingStatus status,
        Boolean isUnsold,
        OffsetDateTime createdAt
) {
}
