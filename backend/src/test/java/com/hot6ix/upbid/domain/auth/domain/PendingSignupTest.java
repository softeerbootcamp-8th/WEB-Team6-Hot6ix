package com.hot6ix.upbid.domain.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.domain.auth.dto.OAuthUserInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PendingSignupTest {

    private static final OAuthUserInfo USER_INFO = new OAuthUserInfo(
            OauthProvider.KAKAO, "1234567890", "010-1234-5678", "user@hot6ix.com", "승민");

    @Test
    @DisplayName("OAuthUserInfo로 만들면 인증되지 않은 상태이다")
    void from_notVerified() {

        PendingSignup pendingSignup = PendingSignup.from(USER_INFO);

        assertThat(pendingSignup.provider()).isEqualTo(OauthProvider.KAKAO);
        assertThat(pendingSignup.providerId()).isEqualTo("1234567890");
        assertThat(pendingSignup.email()).isEqualTo("user@hot6ix.com");
        assertThat(pendingSignup.nickname()).isEqualTo("승민");
        assertThat(pendingSignup.verifiedPhoneNumber()).isNull();
        assertThat(pendingSignup.isVerified()).isFalse();
    }

    @Test
    @DisplayName("OAuth 제공자가 준 전화번호는 담지 않는다 - 별도 인증을 거친 번호만 신뢰한다")
    void from_doesNotTrustProviderPhoneNumber() {

        PendingSignup pendingSignup = PendingSignup.from(USER_INFO);

        assertThat(pendingSignup.verifiedPhoneNumber()).isNotEqualTo(USER_INFO.phoneNumber());
    }

    @Test
    @DisplayName("withVerified는 인증된 번호를 담은 새 인스턴스를 반환하고 기존 인스턴스는 변경하지 않는다")
    void withVerified_returnsNewInstance() {

        PendingSignup original = PendingSignup.from(USER_INFO);

        PendingSignup verified = original.withVerified("010-9999-8888");

        assertThat(original.isVerified()).isFalse();
        assertThat(original.verifiedPhoneNumber()).isNull();

        assertThat(verified.isVerified()).isTrue();
        assertThat(verified.verifiedPhoneNumber()).isEqualTo("010-9999-8888");
        assertThat(verified.provider()).isEqualTo(original.provider());
        assertThat(verified.providerId()).isEqualTo(original.providerId());
        assertThat(verified.email()).isEqualTo(original.email());
        assertThat(verified.nickname()).isEqualTo(original.nickname());
    }
}
