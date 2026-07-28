package com.hot6ix.upbid.domain.auth.client;

public interface OAuthClient {

    OAuthUserInfo getUserInfo(String authorizationCode);
}
