package com.hot6ix.upbid.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.upload.ImageUrlValidator;
import com.hot6ix.upbid.domain.upload.exception.UploadErrorType;
import com.hot6ix.upbid.domain.user.dto.request.UserUpdateRequestDto;
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

    // 이미지 주소 검증은 ImageUrlValidatorTest에서 본다. 여기서는 통과시킨다.
    @Mock
    private ImageUrlValidator imageUrlValidator;

    @InjectMocks
    private UserService userService;

    private User newUser() {
        return User.builder()
                .provider("kakao")
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

    // ==================== updateMe ====================

    @Test
    @DisplayName("닉네임과 이미지 URL을 전달하면 프로필이 수정되고 수정된 정보를 반환한다")
    void updateMe() {

        User user = newUser();
        when(userRepository.findByUserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));

        String newNickname = "새닉네임";
        String newImageUrl = "https://upbid-bucket.s3.ap-northeast-2.amazonaws.com/user-profiles/1/new.jpg";
        UserUpdateRequestDto request = new UserUpdateRequestDto(newNickname, newImageUrl);

        UserMeResponseDto response = userService.updateMe(1L, request);

        assertThat(response.nickname()).isEqualTo(newNickname);
        assertThat(response.profileImageUrl()).isEqualTo(newImageUrl);
    }

    @Test
    @DisplayName("profileImageUrl을 null로 전달하면 이미지가 제거된다")
    void updateMe_nullImage() {

        User user = newUser();
        when(userRepository.findByUserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));

        UserUpdateRequestDto request = new UserUpdateRequestDto("새닉네임", null);

        UserMeResponseDto response = userService.updateMe(1L, request);

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

        verify(userRepository, org.mockito.Mockito.never()).findByUserIdAndDeletedAtIsNull(1L);
    }
}
