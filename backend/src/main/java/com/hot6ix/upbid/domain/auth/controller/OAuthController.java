package com.hot6ix.upbid.domain.auth.controller;

import com.hot6ix.upbid.domain.auth.dto.AuthLoginResponseDto;
import com.hot6ix.upbid.domain.auth.service.AuthService;
import com.hot6ix.upbid.global.session.SessionManager;
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

    @Value("${app.frontend-url}")
    private String frontendUrl;

    private final AuthService authService;
    private final SessionManager sessionManager;

    @GetMapping("/kakao/callback")
    public void kakaoLogin(
            @RequestParam String code,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        AuthLoginResponseDto login = authService.login(code);
        sessionManager.create(request, login.userId());

        response.sendRedirect(frontendUrl + (login.isNewUser() ? "/onboarding" : "/"));
    }
}
