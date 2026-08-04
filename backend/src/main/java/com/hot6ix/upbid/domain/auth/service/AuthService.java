package com.hot6ix.upbid.domain.auth.service;

import com.hot6ix.upbid.domain.auth.domain.OAuthLoginResult;
import com.hot6ix.upbid.domain.auth.domain.OauthProvider;
import com.hot6ix.upbid.domain.auth.domain.PendingSignup;
import com.hot6ix.upbid.domain.auth.dto.OAuthUserInfo;
import com.hot6ix.upbid.domain.auth.exception.AuthErrorType;
import com.hot6ix.upbid.domain.auth.oauth.service.OauthClientManager;
import com.hot6ix.upbid.domain.auth.session.PendingSignupManager;
import com.hot6ix.upbid.domain.user.service.UserService;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.session.SessionManager;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final OauthClientManager oauthClientManager;
    private final SessionManager sessionManager;
    private final PendingSignupManager pendingSignupManager;

    public OAuthLoginResult login(HttpServletRequest request, String authorizationCode) {

        OAuthUserInfo userInfo = oauthClientManager.getUserInfo(OauthProvider.KAKAO, authorizationCode);

        if (userInfo == null || !StringUtils.hasText(userInfo.providerId())) {
            throw new ApplicationException(AuthErrorType.OAUTH_LOGIN_FAILED);
        }

        Optional<Long> userId = userService.findByOAuth(userInfo);

        if (userId.isPresent()) {
            sessionManager.create(request, userId.get());
            return OAuthLoginResult.LOGGED_IN;
        }

        pendingSignupManager.save(request, PendingSignup.from(userInfo));

        return OAuthLoginResult.SIGNUP_REQUIRED;
    }
}
