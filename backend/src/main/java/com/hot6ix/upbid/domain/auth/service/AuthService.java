package com.hot6ix.upbid.domain.auth.service;

import com.hot6ix.upbid.domain.auth.client.OAuthClient;
import com.hot6ix.upbid.domain.auth.client.OAuthUserInfo;
import com.hot6ix.upbid.domain.auth.dto.AuthLoginResponseDto;
import com.hot6ix.upbid.domain.auth.exception.AuthErrorType;
import com.hot6ix.upbid.domain.user.service.UserService;
import com.hot6ix.upbid.global.exception.ApplicationException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final OAuthClient oAuthClient;
    private final UserService userService;

    public AuthLoginResponseDto login(String authorizationCode) {

        OAuthUserInfo userInfo = oAuthClient.getUserInfo(authorizationCode);

        if (userInfo == null || !StringUtils.hasText(userInfo.providerId())) {
            throw new ApplicationException(AuthErrorType.OAUTH_LOGIN_FAILED);
        }

        try {
            return AuthLoginResponseDto.from(userService.findOrCreateByOAuth(userInfo));
        } catch (DataIntegrityViolationException e) {
            return AuthLoginResponseDto.from(userService.getByOAuth(userInfo));
        }
    }
}
