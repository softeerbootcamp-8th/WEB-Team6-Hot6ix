package com.hot6ix.upbid.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hot6ix.upbid.domain.upload.exception.UploadErrorType;
import com.hot6ix.upbid.domain.user.dto.request.UserUpdateRequestDto;
import com.hot6ix.upbid.domain.user.dto.response.UserResponseDto;
import com.hot6ix.upbid.domain.user.exception.UserErrorType;
import com.hot6ix.upbid.domain.user.service.UserService;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.exception.GlobalExceptionHandler;
import com.hot6ix.upbid.global.support.AbstractControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = UserController.class)
@Import(GlobalExceptionHandler.class)
class UserControllerTest extends AbstractControllerTest {

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("로그인된 사용자가 /me를 호출하면 200과 내 정보를 반환한다")
    void getMe() throws Exception {

        UserResponseDto response = new UserResponseDto(
                LOGIN_USER_ID, "테스트유저", "test@hot6ix.com", "https://cdn.hot6ix.com/profile.png"
        );
        when(userService.getMe(LOGIN_USER_ID)).thenReturn(response);

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(LOGIN_USER_ID))
                .andExpect(jsonPath("$.data.nickname").value("테스트유저"))
                .andExpect(jsonPath("$.data.email").value("test@hot6ix.com"))
                .andExpect(jsonPath("$.data.profileImageUrl").value("https://cdn.hot6ix.com/profile.png"));
    }

    @Test
    @DisplayName("세션이 없는 비로그인 상태로 /me를 호출하면 401을 반환한다")
    void getMe_unauthorized() throws Exception {

        비로그인_상태로_바꾼다();

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("탈퇴하거나 존재하지 않는 사용자 ID면 404와 에러코드를 반환한다")
    void getMe_userNotFound() throws Exception {

        when(userService.getMe(LOGIN_USER_ID))
                .thenThrow(new ApplicationException(UserErrorType.USER_NOT_FOUND));

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(9001));
    }

    // ==================== PUT /me ====================

    @Test
    @DisplayName("닉네임과 이미지를 모두 전달하면 200과 수정된 내 정보를 반환한다")
    void updateMe() throws Exception {

        UserUpdateRequestDto request = new UserUpdateRequestDto(
                "새닉네임", "https://upbid-bucket.s3.ap-northeast-2.amazonaws.com/user-profiles/1/abc.jpg");
        UserResponseDto response = new UserResponseDto(
                LOGIN_USER_ID, "새닉네임", "test@hot6ix.com",
                "https://upbid-bucket.s3.ap-northeast-2.amazonaws.com/user-profiles/1/abc.jpg");
        when(userService.updateMe(eq(LOGIN_USER_ID), any(UserUpdateRequestDto.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nickname").value("새닉네임"))
                .andExpect(jsonPath("$.data.profileImageUrl").value(
                        "https://upbid-bucket.s3.ap-northeast-2.amazonaws.com/user-profiles/1/abc.jpg"));
    }

    @Test
    @DisplayName("profileImageUrl을 null로 보내면 200을 반환한다 (이미지 제거)")
    void updateMe_nullImage() throws Exception {

        UserUpdateRequestDto request = new UserUpdateRequestDto("새닉네임", null);
        UserResponseDto response = new UserResponseDto(LOGIN_USER_ID, "새닉네임", "test@hot6ix.com", null);
        when(userService.updateMe(eq(LOGIN_USER_ID), any(UserUpdateRequestDto.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageUrl").isEmpty());
    }

    @Test
    @DisplayName("nickname이 blank이면 400을 반환한다")
    void updateMe_blankNickname() throws Exception {

        UserUpdateRequestDto request = new UserUpdateRequestDto("  ", null);

        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("nickname이 10자를 초과하면 400을 반환한다")
    void updateMe_nicknameTooLong() throws Exception {

        UserUpdateRequestDto request = new UserUpdateRequestDto("가나다라마바사아자차카", null); // 11자

        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비로그인 상태로 요청하면 401을 반환한다")
    void updateMe_unauthorized() throws Exception {

        비로그인_상태로_바꾼다();

        UserUpdateRequestDto request = new UserUpdateRequestDto("닉네임", null);

        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 404를 반환한다")
    void updateMe_userNotFound() throws Exception {

        when(userService.updateMe(eq(LOGIN_USER_ID), any(UserUpdateRequestDto.class)))
                .thenThrow(new ApplicationException(UserErrorType.USER_NOT_FOUND));

        UserUpdateRequestDto request = new UserUpdateRequestDto("닉네임", null);

        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(9001));
    }

    @Test
    @DisplayName("우리 버킷 주소가 아닌 이미지 URL이면 400을 반환한다")
    void updateMe_invalidImageUrl() throws Exception {

        when(userService.updateMe(eq(LOGIN_USER_ID), any(UserUpdateRequestDto.class)))
                .thenThrow(new ApplicationException(UploadErrorType.INVALID_IMAGE_URL));

        UserUpdateRequestDto request = new UserUpdateRequestDto("닉네임", "https://evil.com/hack.jpg");

        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
