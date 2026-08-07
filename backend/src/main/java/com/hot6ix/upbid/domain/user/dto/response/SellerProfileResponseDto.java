package com.hot6ix.upbid.domain.user.dto.response;

import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.global.common.ServerTime;
import java.time.OffsetDateTime;
import lombok.Builder;

@Builder
public record SellerProfileResponseDto(
        Long sellerProfileId,
        String storeName,
        String storeImageUrl,
        String snsUrl,
        String storePhoneNumber,
        String storeDescription,
        OffsetDateTime createdAt
) {
    public static SellerProfileResponseDto from(SellerProfile sellerProfile) {
        return SellerProfileResponseDto.builder()
                .sellerProfileId(sellerProfile.getSellerProfileId())
                .storeName(sellerProfile.getStoreName())
                .storeImageUrl(sellerProfile.getStoreImageUrl())
                .snsUrl(sellerProfile.getSnsUrl())
                .storePhoneNumber(sellerProfile.getStorePhoneNumber())
                .storeDescription(sellerProfile.getStoreDescription())
                .createdAt(ServerTime.toOffset(sellerProfile.getCreatedAt()))
                .build();
    }
}
