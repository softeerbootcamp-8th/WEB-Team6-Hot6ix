package com.hot6ix.upbid.domain.oauth;

import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.domain.oauth.exception.OauthErrorType;
import com.hot6ix.upbid.domain.oauth.kakao.KakaoOauthClient;
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
                .orElseThrow(() -> new ApplicationException(OauthErrorType.UNSUPPORTED_OAUTH_PROVIDER));
        return client.getUserInfo(authorizationCode);
    }
}
