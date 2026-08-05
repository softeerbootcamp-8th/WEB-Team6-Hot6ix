package com.hot6ix.upbid.domain.auth.domain;

import com.hot6ix.upbid.domain.auth.dto.OAuthUserInfo;

public record PendingSignup(
        OauthProvider provider,
        String providerId,
        String email,
        String nickname,
        String verifiedPhoneNumber
) {

    public static PendingSignup from(OAuthUserInfo userInfo) {
        return new PendingSignup(
                userInfo.provider(),
                userInfo.providerId(),
                userInfo.email(),
                userInfo.name(),
                null
        );
    }

    public PendingSignup withVerified(String phoneNumber) {
        return new PendingSignup(provider, providerId, email, nickname, phoneNumber);
    }

    public boolean isVerified() {
        return verifiedPhoneNumber != null;
    }
}
