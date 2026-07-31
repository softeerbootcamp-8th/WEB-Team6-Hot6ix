package com.hot6ix.upbid.domain.auth.oauth.kakao;

import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.domain.auth.oauth.service.OAuthClient;
import com.hot6ix.upbid.domain.auth.dto.OAuthUserInfo;
import com.hot6ix.upbid.domain.auth.exception.AuthErrorType;
import com.hot6ix.upbid.domain.auth.oauth.kakao.dto.KakaoTokenResponse;
import com.hot6ix.upbid.domain.auth.oauth.kakao.dto.KakaoUserInfoResponse;
import com.hot6ix.upbid.domain.auth.oauth.kakao.dto.KakaoUserInfoResponse.KakaoAccount;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoOauthClient implements OAuthClient {

    private final KakaoApiClient kakaoApiClient;

    @Override
    public OAuthUserInfo getUserInfo(String authorizationCode) {
        KakaoTokenResponse token = issueToken(authorizationCode);
        KakaoUserInfoResponse userInfo = fetchUserInfo(token.accessToken());

        KakaoAccount kakaoAccount = userInfo.kakaoAccount();

        String phoneNumber = Optional.ofNullable(kakaoAccount)
                .map(KakaoAccount::phoneNumber)
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new ApplicationException(AuthErrorType.KAKAO_PHONE_NUMBER_REQUIRED));

        String email = Optional.ofNullable(kakaoAccount)
                .map(KakaoAccount::email)
                .orElse(null);

        String nickname = Optional.ofNullable(kakaoAccount)
                .map(KakaoAccount::profile)
                .map(KakaoUserInfoResponse.Profile::nickname)
                .orElse(null);

        return new OAuthUserInfo("kakao", String.valueOf(userInfo.id()), phoneNumber, email, nickname);
    }

    private KakaoTokenResponse issueToken(String authorizationCode) {
        try {
            return kakaoApiClient.issueToken(authorizationCode);
        } catch (RestClientException e) {
            log.warn("카카오 토큰 발급 실패 - {}", e.getMessage());
            throw new ApplicationException(AuthErrorType.KAKAO_TOKEN_REQUEST_FAILED);
        }
    }

    private KakaoUserInfoResponse fetchUserInfo(String accessToken) {
        try {
            return kakaoApiClient.getUserInfo(accessToken);
        } catch (RestClientException e) {
            log.warn("카카오 사용자 정보 조회 실패 - {}", e.getMessage());
            throw new ApplicationException(AuthErrorType.KAKAO_USER_INFO_REQUEST_FAILED);
        }
    }
}
