package com.hot6ix.upbid.domain.auth.dto;

import com.hot6ix.upbid.domain.auth.domain.OauthProvider;

public record OAuthUserInfo(
        OauthProvider provider,
        String providerId,
        String email,
        String name
) {
}
