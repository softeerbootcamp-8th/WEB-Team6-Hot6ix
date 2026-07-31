package com.hot6ix.upbid.domain.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hot6ix.upbid.global.exception.GlobalExceptionHandler;
import com.hot6ix.upbid.global.support.AbstractControllerTest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;

@WebMvcTest(controllers = AuthController.class)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest extends AbstractControllerTest {

    private static final String URL = "/api/v1/auth/logout";

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
