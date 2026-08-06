package com.hot6ix.upbid.domain.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hot6ix.upbid.domain.auth.domain.OAuthLoginResult;
import com.hot6ix.upbid.domain.auth.service.AuthService;
import com.hot6ix.upbid.global.exception.GlobalExceptionHandler;
import com.hot6ix.upbid.global.support.AbstractControllerTest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = OAuthController.class, properties = "app.frontend-url=" + OAuthControllerTest.FRONTEND_URL)
@Import(GlobalExceptionHandler.class)
class OAuthControllerTest extends AbstractControllerTest {

    static final String FRONTEND_URL = "http://localhost:5173";

    private static final String URL = "/api/v1/oauth/kakao/callback";

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("이미 가입된 회원이면 홈으로 리다이렉트한다")
    void kakaoLogin_existingUser() throws Exception {

        when(authService.login(any(HttpServletRequest.class), eq("code")))
                .thenReturn(OAuthLoginResult.LOGGED_IN);

        mockMvc.perform(get(URL).param("code", "code"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(FRONTEND_URL + "/rooms"));
    }

    @Test
    @DisplayName("신규 사용자면 전화번호 인증 온보딩 페이지로 리다이렉트한다")
    void kakaoLogin_newUser() throws Exception {

        when(authService.login(any(HttpServletRequest.class), eq("code")))
                .thenReturn(OAuthLoginResult.SIGNUP_REQUIRED);

        mockMvc.perform(get(URL).param("code", "code"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(FRONTEND_URL + "/signup/phone"));
    }

    @Test
    @DisplayName("비로그인 상태로 콜백을 받아도 인터셉터에 막히지 않는다")
    void kakaoLogin_allowsGuest() throws Exception {

        비로그인_상태로_바꾼다();
        when(authService.login(any(HttpServletRequest.class), eq("code")))
                .thenReturn(OAuthLoginResult.SIGNUP_REQUIRED);

        mockMvc.perform(get(URL).param("code", "code"))
                .andExpect(status().is3xxRedirection());
    }

}
