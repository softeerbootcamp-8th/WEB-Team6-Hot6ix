package com.hot6ix.upbid.domain.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hot6ix.upbid.domain.auth.exception.AuthErrorType;
import com.hot6ix.upbid.domain.auth.service.AuthService;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.exception.GlobalExceptionHandler;
import com.hot6ix.upbid.global.support.AbstractControllerTest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = AuthController.class)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest extends AbstractControllerTest {

    private static final String URL = "/api/v1/auth/logout";
    private static final String SIGNUP_URL = "/api/v1/auth/signup";

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("회원가입에 성공하면 200을 반환한다")
    void signup() throws Exception {

        mockMvc.perform(post(SIGNUP_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("회원가입이 완료되었습니다."));

        verify(authService).signup(any(HttpServletRequest.class));
    }

    @Test
    @DisplayName("비로그인 상태로 회원가입을 요청해도 401이 아닌 200을 반환한다")
    void signupAllowsGuest() throws Exception {

        비로그인_상태로_바꾼다();

        mockMvc.perform(post(SIGNUP_URL))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("전화번호 인증을 마치지 않았으면 400을 반환한다")
    void signup_phoneNotVerified() throws Exception {

        doThrow(new ApplicationException(AuthErrorType.PHONE_NOT_VERIFIED))
                .when(authService).signup(any(HttpServletRequest.class));

        mockMvc.perform(post(SIGNUP_URL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(1010));
    }

    @Test
    @DisplayName("가입 대기 정보가 없으면 401을 반환한다")
    void signup_pendingSignupNotFound() throws Exception {

        doThrow(new ApplicationException(AuthErrorType.PENDING_SIGNUP_NOT_FOUND))
                .when(authService).signup(any(HttpServletRequest.class));

        mockMvc.perform(post(SIGNUP_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(1009));
    }

    @Test
    @DisplayName("로그아웃하면 200을 반환하고 세션을 무효화한다")
    void logout() throws Exception {

        mockMvc.perform(post(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("로그아웃되었습니다."));

        verify(sessionManager).invalidate(any(HttpServletRequest.class));
    }

    @Test
    @DisplayName("세션이 없는 상태로 로그아웃해도 401이 아닌 200을 반환한다")
    void logoutAllowsGuest() throws Exception {

        비로그인_상태로_바꾼다();

        mockMvc.perform(post(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
