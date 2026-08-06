package com.hot6ix.upbid.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auth.domain.OauthProvider;
import com.hot6ix.upbid.domain.auth.domain.PendingSignup;
import com.hot6ix.upbid.domain.auth.exception.AuthErrorType;
import com.hot6ix.upbid.domain.upload.ImageUrlValidator;
import com.hot6ix.upbid.domain.upload.exception.UploadErrorType;
import com.hot6ix.upbid.domain.user.dto.request.UserUpdateRequestDto;
import com.hot6ix.upbid.domain.user.dto.response.UserResponseDto;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    // 이미지 주소 검증은 ImageUrlValidatorTest에서 본다. 여기서는 통과시킨다.
    @Mock
    private ImageUrlValidator imageUrlValidator;

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

    /**
     * userId는 저장 시점에 DB가 채우는 값이라 빌더로는 설정할 수 없다.
     * 조회 결과로 돌려줄 회원은 ID가 있어야 하므로 직접 주입한다.
     */
    private User savedUser(Long userId) {
        User user = newUser();
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }

    @Test
    @DisplayName("가입 대기 정보로 회원을 저장하고 userId를 반환한다")
    void create() {

        PendingSignup pendingSignup = new PendingSignup(
                OauthProvider.KAKAO, "kakao-123", "test@hot6ix.com", "테스트유저", "01099998888");
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(savedUser(1L));

        assertThat(userService.create(pendingSignup)).isEqualTo(1L);

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
                .thenReturn(Optional.of(savedUser(1L)));

        assertThat(userService.findByOAuth(OauthProvider.KAKAO, "kakao-123")).contains(1L);
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

        UserResponseDto response = userService.getMe(1L);

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

    // ==================== updateMe ====================

    @Test
    @DisplayName("닉네임과 이미지 URL을 전달하면 프로필이 수정되고 수정된 정보를 반환한다")
    void updateMe() {

        User user = newUser();
        when(userRepository.findByUserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));

        String newNickname = "새닉네임";
        String newImageUrl = "https://upbid-bucket.s3.ap-northeast-2.amazonaws.com/user-profiles/1/new.jpg";
        UserUpdateRequestDto request = new UserUpdateRequestDto(newNickname, newImageUrl);

        UserResponseDto response = userService.updateMe(1L, request);

        assertThat(response.nickname()).isEqualTo(newNickname);
        assertThat(response.profileImageUrl()).isEqualTo(newImageUrl);
    }

    @Test
    @DisplayName("profileImageUrl을 null로 전달하면 이미지가 제거된다")
    void updateMe_nullImage() {

        User user = newUser();
        when(userRepository.findByUserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));

        UserUpdateRequestDto request = new UserUpdateRequestDto("새닉네임", null);

        UserResponseDto response = userService.updateMe(1L, request);

        assertThat(response.profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 사용자 ID로 수정하면 USER_NOT_FOUND 예외가 발생한다")
    void updateMe_userNotFound() {

        when(userRepository.findByUserIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        UserUpdateRequestDto request = new UserUpdateRequestDto("닉네임", null);

        assertThatThrownBy(() -> userService.updateMe(99L, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(UserErrorType.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("우리 버킷 주소가 아닌 이미지 URL이면 INVALID_IMAGE_URL 예외가 발생한다")
    void updateMe_invalidImageUrl() {

        String invalidUrl = "https://evil.com/hack.jpg";
        doThrow(new ApplicationException(UploadErrorType.INVALID_IMAGE_URL))
                .when(imageUrlValidator).validate(invalidUrl);

        UserUpdateRequestDto request = new UserUpdateRequestDto("닉네임", invalidUrl);

        assertThatThrownBy(() -> userService.updateMe(1L, request))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getErrorType())
                .isEqualTo(UploadErrorType.INVALID_IMAGE_URL);
    }

    @Test
    @DisplayName("이미지 URL 검증은 유저 조회보다 먼저 실행된다")
    void updateMe_validatesImageBeforeFetchingUser() {

        String invalidUrl = "https://evil.com/hack.jpg";
        doThrow(new ApplicationException(UploadErrorType.INVALID_IMAGE_URL))
                .when(imageUrlValidator).validate(invalidUrl);

        UserUpdateRequestDto request = new UserUpdateRequestDto("닉네임", invalidUrl);

        assertThatThrownBy(() -> userService.updateMe(1L, request))
                .isInstanceOf(ApplicationException.class);

        verify(userRepository, never()).findByUserIdAndDeletedAtIsNull(1L);
    }
}
