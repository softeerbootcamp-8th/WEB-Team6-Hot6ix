package com.hot6ix.upbid.domain.upload.dto.response;

import lombok.Builder;

@Builder
public record PresignedUrlResponseDto(

        // S3에 PUT할 주소. 서명이 쿼리스트링에 붙어 있어 길고, 만료되면 못 쓴다.
        String uploadUrl,

        // 업로드가 끝난 뒤 storeImageUrl·imageUrl로 저장할 주소.
        String fileUrl,

        long expiresInSeconds
) {
}
