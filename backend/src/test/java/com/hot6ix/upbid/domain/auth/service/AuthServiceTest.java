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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
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
        when(userService.findByOAuth(userInfo.provider(), userInfo.providerId())).thenReturn(Optional.of(1L));

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
        when(userService.findByOAuth(userInfo.provider(), userInfo.providerId())).thenReturn(Optional.empty());

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
    @DisplayName("인증까지 마친 가입 대기 정보로 회원을 저장하고 로그인 세션을 발급한다")
    void signup() {

        PendingSignup verified = new PendingSignup(
                OauthProvider.KAKAO, "1", "a@b.com", "닉네임", "01012345678");
        when(pendingSignupManager.find(request)).thenReturn(Optional.of(verified));
        when(userService.create(verified)).thenReturn(10L);

        authService.signup(request);

        verify(userService).create(verified);
        verify(pendingSignupManager).clear(request);
        verify(sessionManager).create(request, 10L);
    }

    @Test
    @DisplayName("가입 대기 정보가 없으면 PENDING_SIGNUP_NOT_FOUND 예외가 발생한다")
    void signup_withoutPendingSignup() {

        when(pendingSignupManager.find(request)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuthErrorType.PENDING_SIGNUP_NOT_FOUND);

        verify(userService, never()).create(any());
    }

    @Test
    @DisplayName("전화번호 인증을 마치지 않았으면 PHONE_NOT_VERIFIED 예외가 발생하고 회원을 저장하지 않는다")
    void signup_phoneNotVerified() {

        PendingSignup notVerified = new PendingSignup(
                OauthProvider.KAKAO, "1", "a@b.com", "닉네임", null);
        when(pendingSignupManager.find(request)).thenReturn(Optional.of(notVerified));

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuthErrorType.PHONE_NOT_VERIFIED);

        verify(userService, never()).create(any());
        verify(sessionManager, never()).create(any(), any());
    }

    @Test
    @DisplayName("동시 가입으로 유니크 제약을 위반하면 먼저 저장된 회원으로 로그인시킨다")
    void signup_duplicateKeyRace() {

        PendingSignup verified = new PendingSignup(
                OauthProvider.KAKAO, "1", "a@b.com", "닉네임", "01012345678");
        when(pendingSignupManager.find(request)).thenReturn(Optional.of(verified));
        when(userService.create(verified)).thenThrow(new DuplicateKeyException("duplicate"));
        when(userService.findByOAuth(OauthProvider.KAKAO, "1")).thenReturn(Optional.of(7L));

        authService.signup(request);

        verify(sessionManager).create(request, 7L);
    }

    @Test
    @DisplayName("유니크 제약이 아닌 데이터 오류는 USER_INFO_INVALID 예외로 변환한다")
    void signup_nonDuplicateDataIntegrityViolation() {

        PendingSignup verified = new PendingSignup(
                OauthProvider.KAKAO, "1", "a@b.com", "닉네임", "01012345678");
        when(pendingSignupManager.find(request)).thenReturn(Optional.of(verified));
        when(userService.create(verified)).thenThrow(new DataIntegrityViolationException("data too long"));

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuthErrorType.USER_INFO_INVALID);

        verify(sessionManager, never()).create(any(), any());
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
