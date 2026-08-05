package com.hot6ix.upbid.domain.auth.controller;

import com.hot6ix.upbid.domain.auth.domain.OAuthLoginResult;
import com.hot6ix.upbid.domain.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Hidden
@RestController
@RequestMapping("/api/v1/oauth")
@RequiredArgsConstructor
public class OAuthController {

    // 이미 가입된 회원이 로그인했을 때 이동할 경로
    private static final String HOME_PATH = "/rooms";

    // 신규 사용자가 전화번호 인증을 진행할 온보딩 경로
    private static final String SIGNUP_PATH = "/signup/phone";

    @Value("${app.frontend-url}")
    private String frontendUrl;

    private final AuthService authService;

    @GetMapping("/kakao/callback")
    public void kakaoLogin(
            @RequestParam String code,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        OAuthLoginResult result = authService.login(request, code);

        String path = (result == OAuthLoginResult.SIGNUP_REQUIRED) ? SIGNUP_PATH : HOME_PATH;

        response.sendRedirect(frontendUrl + path);
    }
}
