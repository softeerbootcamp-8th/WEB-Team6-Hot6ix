package com.hot6ix.upbid.domain.auth.dto;

public record OAuthUserInfo(
        String provider,
        String providerId,
        String phoneNumber,
        String email,
        String name
) {
}
