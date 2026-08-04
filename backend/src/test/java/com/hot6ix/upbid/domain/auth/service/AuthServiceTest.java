package com.hot6ix.upbid.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auth.domain.OAuthLoginResult;
import com.hot6ix.upbid.domain.auth.domain.OauthProvider;
import com.hot6ix.upbid.domain.auth.domain.PendingSignup;
import com.hot6ix.upbid.domain.auth.dto.OAuthUserInfo;
import com.hot6ix.upbid.domain.auth.exception.AuthErrorType;
import com.hot6ix.upbid.domain.auth.exception.SmsVerificationErrorType;
import com.hot6ix.upbid.domain.auth.oauth.service.OauthClientManager;
import com.hot6ix.upbid.domain.auth.session.PendingSignupManager;
import com.hot6ix.upbid.domain.user.service.UserService;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.session.SessionManager;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String AUTHORIZATION_CODE = "code";

    @Mock
    private UserService userService;

    @Mock
    private OauthClientManager oauthClientManager;

    @Mock
    private SessionManager sessionManager;

    @Mock
    private PendingSignupManager pendingSignupManager;

    @Mock
    private SmsVerificationService smsVerificationService;

    @InjectMocks
    private AuthService authService;

    private final MockHttpServletRequest request = new MockHttpServletRequest();

    private OAuthUserInfo kakaoUserInfo() {
        return new OAuthUserInfo(OauthProvider.KAKAO, "1", "010-1234-5678", "a@b.com", "닉네임");
    }

    @Test
    @DisplayName("이미 가입된 회원이면 로그인 세션을 발급하고 LOGGED_IN을 반환한다")
    void login_existingUser() {

        OAuthUserInfo userInfo = kakaoUserInfo();
        when(oauthClientManager.getUserInfo(OauthProvider.KAKAO, AUTHORIZATION_CODE)).thenReturn(userInfo);
        when(userService.findByOAuth(userInfo)).thenReturn(Optional.of(1L));

        OAuthLoginResult result = authService.login(request, AUTHORIZATION_CODE);

        assertThat(result).isEqualTo(OAuthLoginResult.LOGGED_IN);
        verify(sessionManager).create(request, 1L);
        verify(pendingSignupManager, never()).save(any(), any());
    }

    @Test
    @DisplayName("신규 사용자면 회원을 만들지 않고 가입 대기 정보를 세션에 저장한다")
    void login_newUser() {

        OAuthUserInfo userInfo = kakaoUserInfo();
        when(oauthClientManager.getUserInfo(OauthProvider.KAKAO, AUTHORIZATION_CODE)).thenReturn(userInfo);
        when(userService.findByOAuth(userInfo)).thenReturn(Optional.empty());

        OAuthLoginResult result = authService.login(request, AUTHORIZATION_CODE);

        assertThat(result).isEqualTo(OAuthLoginResult.SIGNUP_REQUIRED);
        verify(sessionManager, never()).create(any(), any());

        ArgumentCaptor<PendingSignup> captor = ArgumentCaptor.forClass(PendingSignup.class);
        verify(pendingSignupManager).save(any(), captor.capture());

        PendingSignup saved = captor.getValue();
        assertThat(saved.provider()).isEqualTo(OauthProvider.KAKAO);
        assertThat(saved.providerId()).isEqualTo("1");
        assertThat(saved.email()).isEqualTo("a@b.com");
        assertThat(saved.nickname()).isEqualTo("닉네임");
        assertThat(saved.isVerified()).isFalse();
    }

    @Test
    @DisplayName("가입 대기 상태에서 인증에 성공하면 인증된 전화번호를 세션에 기록한다")
    void verifyPhone_recordsVerifiedNumber() {

        PendingSignup pendingSignup = new PendingSignup(
                OauthProvider.KAKAO, "1", "a@b.com", "닉네임", null);
        when(pendingSignupManager.find(request)).thenReturn(Optional.of(pendingSignup));

        authService.verifyPhone(request, "01012345678", "123456");

        verify(smsVerificationService).verifyCode("01012345678", "123456");

        ArgumentCaptor<PendingSignup> captor = ArgumentCaptor.forClass(PendingSignup.class);
        verify(pendingSignupManager).save(any(), captor.capture());

        assertThat(captor.getValue().isVerified()).isTrue();
        assertThat(captor.getValue().verifiedPhoneNumber()).isEqualTo("01012345678");
    }

    @Test
    @DisplayName("가입 대기 상태가 아니면 검증만 하고 세션에 기록하지 않는다")
    void verifyPhone_withoutPendingSignup() {

        when(pendingSignupManager.find(request)).thenReturn(Optional.empty());

        authService.verifyPhone(request, "01012345678", "123456");

        verify(smsVerificationService).verifyCode("01012345678", "123456");
        verify(pendingSignupManager, never()).save(any(), any());
    }

    @Test
    @DisplayName("인증에 실패하면 세션에 기록하지 않는다")
    void verifyPhone_verificationFailed() {

        doThrow(new ApplicationException(SmsVerificationErrorType.CODE_MISMATCH))
                .when(smsVerificationService).verifyCode("01012345678", "999999");

        assertThatThrownBy(() -> authService.verifyPhone(request, "01012345678", "999999"))
                .isInstanceOf(ApplicationException.class);

        verify(pendingSignupManager, never()).save(any(), any());
    }

    @Test
    @DisplayName("OAuth 응답에 식별자가 없으면 OAUTH_LOGIN_FAILED 예외가 발생한다")
    void login_missingProviderId() {

        OAuthUserInfo userInfo = new OAuthUserInfo(OauthProvider.KAKAO, "", "010-1234-5678", "a@b.com", "닉네임");
        when(oauthClientManager.getUserInfo(OauthProvider.KAKAO, AUTHORIZATION_CODE)).thenReturn(userInfo);

        assertThatThrownBy(() -> authService.login(request, AUTHORIZATION_CODE))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuthErrorType.OAUTH_LOGIN_FAILED);
    }
}
