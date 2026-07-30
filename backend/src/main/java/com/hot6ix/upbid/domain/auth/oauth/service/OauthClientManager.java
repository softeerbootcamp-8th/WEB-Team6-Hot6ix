package com.hot6ix.upbid.domain.auth.oauth.service;

import com.hot6ix.upbid.domain.auth.domain.OauthProvider;
import com.hot6ix.upbid.domain.auth.dto.OAuthUserInfo;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.domain.auth.exception.AuthErrorType;
import com.hot6ix.upbid.domain.auth.oauth.kakao.KakaoOauthClient;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class OauthClientManager {

    private final Map<OauthProvider, OAuthClient> clients;

    public OauthClientManager(KakaoOauthClient kakaoOauthClient) {
        this.clients = Map.of(OauthProvider.KAKAO, kakaoOauthClient);
    }

    public OAuthUserInfo getUserInfo(OauthProvider provider, String authorizationCode) {
        OAuthClient client = Optional.ofNullable(provider)
                .map(clients::get)
                .orElseThrow(() -> new ApplicationException(AuthErrorType.UNSUPPORTED_OAUTH_PROVIDER));
        return client.getUserInfo(authorizationCode);
    }
}
