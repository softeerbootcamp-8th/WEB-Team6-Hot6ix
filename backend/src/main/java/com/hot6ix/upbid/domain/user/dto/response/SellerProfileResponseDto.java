package com.hot6ix.upbid.domain.user.dto.response;

import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import java.time.LocalDateTime;

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
        return new SellerProfileResponseDto(
                sellerProfile.getSellerProfileId(),
                sellerProfile.getStoreName(),
                sellerProfile.getStoreImageUrl(),
                sellerProfile.getSnsUrl(),
                sellerProfile.getStorePhoneNumber(),
                sellerProfile.getStoreDescription(),
                sellerProfile.getCreatedAt(),
                sellerProfile.getUpdatedAt()
        );
    }
}
