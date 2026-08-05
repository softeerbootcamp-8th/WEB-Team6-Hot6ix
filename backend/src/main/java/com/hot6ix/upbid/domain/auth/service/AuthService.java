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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final OauthClientManager oauthClientManager;
    private final SessionManager sessionManager;
    private final PendingSignupManager pendingSignupManager;
    private final SmsVerificationService smsVerificationService;

    public OAuthLoginResult login(HttpServletRequest request, String authorizationCode) {

        OAuthUserInfo userInfo = oauthClientManager.getUserInfo(OauthProvider.KAKAO, authorizationCode);

        if (userInfo == null || !StringUtils.hasText(userInfo.providerId())) {
            throw new ApplicationException(AuthErrorType.OAUTH_LOGIN_FAILED);
        }

        Optional<Long> userId = userService.findByOAuth(userInfo.provider(), userInfo.providerId());

        if (userId.isPresent()) {
            sessionManager.create(request, userId.get());
            return OAuthLoginResult.LOGGED_IN;
        }

        pendingSignupManager.save(request, PendingSignup.from(userInfo));

        return OAuthLoginResult.SIGNUP_REQUIRED;
    }

    public void verifyPhone(HttpServletRequest request, String phoneNumber, String code) {

        smsVerificationService.verifyCode(phoneNumber, code);

        pendingSignupManager.find(request)
                .ifPresent(pendingSignup ->
                        pendingSignupManager.save(request, pendingSignup.withVerified(phoneNumber)));
    }

    public void signup(HttpServletRequest request) {

        PendingSignup pendingSignup = pendingSignupManager.find(request)
                .orElseThrow(() -> new ApplicationException(AuthErrorType.PENDING_SIGNUP_NOT_FOUND));

        if (!pendingSignup.isVerified()) {
            throw new ApplicationException(AuthErrorType.PHONE_NOT_VERIFIED);
        }

        Long userId = createUser(pendingSignup);

        pendingSignupManager.clear(request);
        sessionManager.create(request, userId);
    }

    private Long createUser(PendingSignup pendingSignup) {

        try {
            return userService.create(pendingSignup);

        } catch (DataIntegrityViolationException e) {
            return userService.findByOAuth(pendingSignup.provider(), pendingSignup.providerId())
                    .orElseThrow(() -> new ApplicationException(AuthErrorType.USER_INFO_INVALID));
        }
    }
}
