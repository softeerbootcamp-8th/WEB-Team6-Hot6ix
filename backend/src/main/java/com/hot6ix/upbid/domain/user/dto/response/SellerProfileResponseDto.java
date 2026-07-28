package com.hot6ix.upbid.domain.user.dto.response;

import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record SellerProfileResponseDto(
        Long sellerProfileId,
        String storeName,
        String storeImageUrl,
        String snsUrl,
        String storePhoneNumber,
        String storeDescription,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static SellerProfileResponseDto from(SellerProfile sellerProfile) {
        return SellerProfileResponseDto.builder()
                .sellerProfileId(sellerProfile.getSellerProfileId())
                .storeName(sellerProfile.getStoreName())
                .storeImageUrl(sellerProfile.getStoreImageUrl())
                .snsUrl(sellerProfile.getSnsUrl())
                .storePhoneNumber(sellerProfile.getStorePhoneNumber())
                .storeDescription(sellerProfile.getStoreDescription())
                .createdAt(sellerProfile.getCreatedAt())
                .updatedAt(sellerProfile.getUpdatedAt())
                .build();
    }
}
