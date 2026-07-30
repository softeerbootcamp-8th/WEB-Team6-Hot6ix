package com.hot6ix.upbid.global.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auth.dto.OAuthUserInfo;
import com.hot6ix.upbid.domain.auth.oauth.service.OauthClientManager;
import com.hot6ix.upbid.domain.auth.domain.OauthProvider;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.domain.auth.exception.AuthErrorType;
import com.hot6ix.upbid.domain.auth.oauth.kakao.KakaoOauthClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OauthClientManagerTest {

    @Mock
    private KakaoOauthClient kakaoOauthClient;

    @Test
    @DisplayName("KAKAO provider로 요청하면 KakaoOauthClient에 위임한다")
    void delegatesToKakaoClient() {
        OauthClientManager manager = new OauthClientManager(kakaoOauthClient);
        OAuthUserInfo expected = new OAuthUserInfo("kakao", "1", "010-1234-5678", "a@b.com", "닉네임");
        when(kakaoOauthClient.getUserInfo("code")).thenReturn(expected);

        OAuthUserInfo actual = manager.getUserInfo(OauthProvider.KAKAO, "code");

        assertThat(actual).isEqualTo(expected);
        verify(kakaoOauthClient).getUserInfo("code");
    }

    @Test
    @DisplayName("등록되지 않은 provider로 요청하면 예외가 발생한다")
    void throwsWhenProviderUnsupported() {
        OauthClientManager manager = new OauthClientManager(kakaoOauthClient);

        assertThatThrownBy(() -> manager.getUserInfo(null, "code"))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuthErrorType.UNSUPPORTED_OAUTH_PROVIDER);
    }
}
