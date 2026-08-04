package com.hot6ix.upbid.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auth.domain.OauthProvider;
import com.hot6ix.upbid.domain.user.dto.response.UserMeResponseDto;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.exception.UserErrorType;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.exception.ApplicationException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
