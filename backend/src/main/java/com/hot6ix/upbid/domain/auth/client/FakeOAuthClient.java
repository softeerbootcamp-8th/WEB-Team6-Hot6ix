package com.hot6ix.upbid.domain.auth.client;

import com.hot6ix.upbid.domain.auth.exception.AuthErrorType;
import com.hot6ix.upbid.global.exception.ApplicationException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class FakeOAuthClient implements OAuthClient {

    @Override
    public OAuthUserInfo getUserInfo(String authorizationCode) {

        if (!StringUtils.hasText(authorizationCode)) {
            throw new ApplicationException(AuthErrorType.OAUTH_LOGIN_FAILED);
        }

        // 카카오는 전화번호를 비즈앱 검수 후에만 내려주므로 null 케이스를 기본으로 흉내낸다.
        return new OAuthUserInfo(
                "kakao",
                authorizationCode,
                null,
                authorizationCode + "@fake.local",
                "테스트사용자"
        );
    }
}
