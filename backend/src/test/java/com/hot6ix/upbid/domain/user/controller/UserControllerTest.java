package com.hot6ix.upbid.domain.user.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hot6ix.upbid.domain.user.dto.response.UserMeResponseDto;
import com.hot6ix.upbid.domain.user.exception.UserErrorType;
import com.hot6ix.upbid.domain.user.service.UserService;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.exception.GlobalExceptionHandler;
import com.hot6ix.upbid.global.support.AbstractControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = UserController.class)
@Import(GlobalExceptionHandler.class)
class UserControllerTest extends AbstractControllerTest {

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("로그인된 사용자가 /me를 호출하면 200과 내 정보를 반환한다")
    void getMe() throws Exception {

        UserMeResponseDto response = new UserMeResponseDto(
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
                .andExpect(jsonPath("$.code").value(2001));
    }
}
