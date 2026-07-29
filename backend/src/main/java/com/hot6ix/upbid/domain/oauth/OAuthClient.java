package com.hot6ix.upbid.domain.oauth;

public interface OAuthClient {

    OAuthUserInfo getUserInfo(String authorizationCode);
}
