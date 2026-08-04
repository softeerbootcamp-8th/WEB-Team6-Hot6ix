package com.hot6ix.upbid.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auth.domain.OauthProvider;
import com.hot6ix.upbid.domain.auth.domain.PendingSignup;
import com.hot6ix.upbid.domain.auth.exception.AuthErrorType;
import com.hot6ix.upbid.domain.user.dto.response.UserMeResponseDto;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.exception.UserErrorType;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.exception.ApplicationException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User newUser() {
        return User.builder()
                .provider(OauthProvider.KAKAO)
                .providerId("kakao-123")
                .nickname("테스트유저")
                .email("test@hot6ix.com")
                .profileImageUrl("https://cdn.hot6ix.com/profile.png")
                .build();
    }

    @Test
    @DisplayName("가입 대기 정보로 회원을 저장하고 userId를 반환한다")
    void create() {

        PendingSignup pendingSignup = new PendingSignup(
                OauthProvider.KAKAO, "kakao-123", "test@hot6ix.com", "테스트유저", "01099998888");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.create(pendingSignup);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());

        User saved = captor.getValue();
        assertThat(saved.getProvider()).isEqualTo(OauthProvider.KAKAO);
        assertThat(saved.getProviderId()).isEqualTo("kakao-123");
        assertThat(saved.getNickname()).isEqualTo("테스트유저");
        assertThat(saved.getPhoneNumber()).isEqualTo("01099998888");
    }

    @Test
    @DisplayName("가입된 회원이면 userId를 반환한다")
    void findByOAuth_existingUser() {

        when(userRepository.findByProviderAndProviderId(OauthProvider.KAKAO, "kakao-123"))
                .thenReturn(Optional.of(newUser()));

        assertThat(userService.findByOAuth(OauthProvider.KAKAO, "kakao-123")).isPresent();
    }

    @Test
    @DisplayName("가입되지 않은 사용자면 빈 Optional을 반환하고 회원을 생성하지 않는다")
    void findByOAuth_newUser() {

        when(userRepository.findByProviderAndProviderId(OauthProvider.KAKAO, "kakao-123"))
                .thenReturn(Optional.empty());

        assertThat(userService.findByOAuth(OauthProvider.KAKAO, "kakao-123")).isEmpty();
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("탈퇴한 회원이면 WITHDRAWN_USER 예외가 발생한다")
    void findByOAuth_withdrawnUser() {

        User withdrawn = newUser();
        withdrawn.softDelete(LocalDateTime.now());

        when(userRepository.findByProviderAndProviderId(OauthProvider.KAKAO, "kakao-123"))
                .thenReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> userService.findByOAuth(OauthProvider.KAKAO, "kakao-123"))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(AuthErrorType.WITHDRAWN_USER);
    }

    @Test
    @DisplayName("내 정보를 조회하면 userId·nickname·email·profileImageUrl을 반환한다")
    void getMe() {

        User user = newUser();
        when(userRepository.findByUserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));

        UserMeResponseDto response = userService.getMe(1L);

        assertThat(response.nickname()).isEqualTo("테스트유저");
        assertThat(response.email()).isEqualTo("test@hot6ix.com");
        assertThat(response.profileImageUrl()).isEqualTo("https://cdn.hot6ix.com/profile.png");
    }

    @Test
    @DisplayName("존재하지 않거나 탈퇴한 사용자 ID로 조회하면 USER_NOT_FOUND 예외가 발생한다")
    void getMe_userNotFound() {

        when(userRepository.findByUserIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMe(99L))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(UserErrorType.USER_NOT_FOUND);
    }
}
