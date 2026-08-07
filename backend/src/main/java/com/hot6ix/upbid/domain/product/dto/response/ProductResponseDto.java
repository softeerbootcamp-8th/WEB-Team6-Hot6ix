package com.hot6ix.upbid.domain.product.dto.response;

import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.product.entity.ProductListingStatus;
import com.hot6ix.upbid.global.common.ServerTime;
import java.time.OffsetDateTime;
import lombok.Builder;

@Builder
public record ProductResponseDto(
        Long productId,
        String name,
        String description,
        String imageUrl,
        String referenceUrl,
        ProductListingStatus status,
        OffsetDateTime createdAt
) {
    public static ProductResponseDto from(Product product, ProductListingStatus status) {
        return ProductResponseDto.builder()
                .productId(product.getProductId())
                .name(product.getName())
                .description(product.getDescription())
                .imageUrl(product.getImageUrl())
                .referenceUrl(product.getReferenceUrl())
                .status(status)
                .createdAt(ServerTime.toOffset(product.getCreatedAt()))
                .build();
    }
}
