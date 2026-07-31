package com.hot6ix.upbid.domain.auth.oauth.service;

import com.hot6ix.upbid.domain.auth.dto.OAuthUserInfo;

public interface OAuthClient {

    OAuthUserInfo getUserInfo(String authorizationCode);
}
