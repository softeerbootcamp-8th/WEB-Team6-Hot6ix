package com.hot6ix.upbid.global.oauth.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auth.domain.OauthProvider;
import com.hot6ix.upbid.domain.auth.oauth.kakao.KakaoApiClient;
import com.hot6ix.upbid.domain.auth.oauth.kakao.KakaoOauthClient;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.domain.auth.dto.OAuthUserInfo;
import com.hot6ix.upbid.domain.auth.exception.AuthErrorType;
import com.hot6ix.upbid.domain.auth.oauth.kakao.dto.KakaoTokenResponse;
import com.hot6ix.upbid.domain.auth.oauth.kakao.dto.KakaoUserInfoResponse;
import com.hot6ix.upbid.domain.auth.oauth.kakao.dto.KakaoUserInfoResponse.KakaoAccount;
import com.hot6ix.upbid.domain.auth.oauth.kakao.dto.KakaoUserInfoResponse.Profile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class KakaoOauthClientTest {

    @Mock
    private KakaoApiClient kakaoApiClient;

    @InjectMocks
    private KakaoOauthClient kakaoOauthClient;

    @Test
    @DisplayName("전화번호가 있으면 OAuthUserInfo로 변환한다")
    void mapsToOAuthUserInfo() {
        when(kakaoApiClient.issueToken("code")).thenReturn(new KakaoTokenResponse("access-token"));
        when(kakaoApiClient.getUserInfo("access-token")).thenReturn(new KakaoUserInfoResponse(
                1L, new KakaoAccount("010-1234-5678", "a@b.com", new Profile("닉네임"))));

        OAuthUserInfo result = kakaoOauthClient.getUserInfo("code");

        assertThat(result).isEqualTo(new OAuthUserInfo(OauthProvider.KAKAO, "1", "010-1234-5678", "a@b.com", "닉네임"));
    }

    @Test
    @DisplayName("전화번호가 없으면 예외가 발생한다")
    void throwsWhenPhoneNumberMissing() {
        when(kakaoApiClient.issueToken("code")).thenReturn(new KakaoTokenResponse("access-token"));
        when(kakaoApiClient.getUserInfo("access-token")).thenReturn(new KakaoUserInfoResponse(
                1L, new KakaoAccount(null, "a@b.com", new Profile("닉네임"))));

        assertThatThrownBy(() -> kakaoOauthClient.getUserInfo("code"))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuthErrorType.KAKAO_PHONE_NUMBER_REQUIRED);
    }

    @Test
    @DisplayName("토큰 발급이 실패하면 예외로 변환한다")
    void throwsWhenTokenRequestFails() {
        when(kakaoApiClient.issueToken("code")).thenThrow(new ResourceAccessException("timeout"));

        assertThatThrownBy(() -> kakaoOauthClient.getUserInfo("code"))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuthErrorType.KAKAO_TOKEN_REQUEST_FAILED);
    }

    @Test
    @DisplayName("사용자 정보 조회가 실패하면 예외로 변환한다")
    void throwsWhenUserInfoRequestFails() {
        when(kakaoApiClient.issueToken("code")).thenReturn(new KakaoTokenResponse("access-token"));
        when(kakaoApiClient.getUserInfo("access-token"))
                .thenThrow(HttpClientErrorException.create(
                        org.springframework.http.HttpStatus.UNAUTHORIZED, "Unauthorized", null, null, null));

        assertThatThrownBy(() -> kakaoOauthClient.getUserInfo("code"))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuthErrorType.KAKAO_USER_INFO_REQUEST_FAILED);
    }
}
