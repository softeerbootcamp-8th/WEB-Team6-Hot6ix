package com.hot6ix.upbid.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auth.domain.OauthProvider;
import com.hot6ix.upbid.domain.auth.dto.AuthLoginResponseDto;
import com.hot6ix.upbid.domain.auth.dto.OAuthUserInfo;
import com.hot6ix.upbid.domain.auth.exception.AuthErrorType;
import com.hot6ix.upbid.domain.auth.oauth.service.OauthClientManager;
import com.hot6ix.upbid.domain.user.dto.UserOAuthLoginDto;
import com.hot6ix.upbid.domain.user.service.UserService;
import com.hot6ix.upbid.global.exception.ApplicationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private OauthClientManager oauthClientManager;

    @InjectMocks
    private AuthService authService;

    private static final String AUTHORIZATION_CODE = "code";

    private OAuthUserInfo kakaoUserInfo() {
        return new OAuthUserInfo(OauthProvider.KAKAO, "1", "010-1234-5678", "a@b.com", "닉네임");
    }

    @Test
    @DisplayName("신규 유저면 findOrCreateByOAuth 결과로 로그인 응답을 반환한다")
    void login_newUser() {

        OAuthUserInfo userInfo = kakaoUserInfo();
        when(oauthClientManager.getUserInfo(OauthProvider.KAKAO, AUTHORIZATION_CODE)).thenReturn(userInfo);
        when(userService.findOrCreateByOAuth(userInfo))
                .thenReturn(new UserOAuthLoginDto(1L, "닉네임", true));

        AuthLoginResponseDto response = authService.login(AUTHORIZATION_CODE);

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.isNewUser()).isTrue();
    }

    @Test
    @DisplayName("동시 요청으로 유니크 제약을 위반하면 재조회한 유저 정보로 응답한다")
    void login_duplicateKeyRace_fallsBackToExistingUser() {

        OAuthUserInfo userInfo = kakaoUserInfo();
        when(oauthClientManager.getUserInfo(OauthProvider.KAKAO, AUTHORIZATION_CODE)).thenReturn(userInfo);
        when(userService.findOrCreateByOAuth(userInfo)).thenThrow(new DuplicateKeyException("duplicate"));
        when(userService.getByOAuth(userInfo))
                .thenReturn(new UserOAuthLoginDto(1L, "닉네임", false));

        AuthLoginResponseDto response = authService.login(AUTHORIZATION_CODE);

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.isNewUser()).isFalse();
    }

    @Test
    @DisplayName("길이 초과·NOT NULL 위반 등 유니크 제약이 아닌 데이터 오류는 USER_INFO_INVALID 예외로 변환한다")
    void login_nonDuplicateDataIntegrityViolation_throwsKakaoUserInfoInvalid() {

        OAuthUserInfo userInfo = kakaoUserInfo();
        when(oauthClientManager.getUserInfo(OauthProvider.KAKAO, AUTHORIZATION_CODE)).thenReturn(userInfo);
        when(userService.findOrCreateByOAuth(userInfo))
                .thenThrow(new DataIntegrityViolationException("data too long"));

        assertThatThrownBy(() -> authService.login(AUTHORIZATION_CODE))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuthErrorType.USER_INFO_INVALID);
    }
}
