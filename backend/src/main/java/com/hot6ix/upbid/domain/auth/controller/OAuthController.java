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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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
            @RequestParam(required = false) String state,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        OAuthLoginResult result = authService.login(request, code);

        String path;
        if (result == OAuthLoginResult.SIGNUP_REQUIRED) {
            // 신규 가입자도 인증 완료 후 원래 페이지로 돌아갈 수 있도록 redirect를 전달한다.
            path = isValidRedirectPath(state)
                    ? SIGNUP_PATH + "?redirect=" + URLEncoder.encode(state, StandardCharsets.UTF_8)
                    : SIGNUP_PATH;
        } else if (isValidRedirectPath(state)) {
            path = state;
        } else {
            path = HOME_PATH;
        }

        response.sendRedirect(frontendUrl + path);
    }

    /**
     * 오픈 리다이렉트 방어. 상대 경로만 허용한다.
     * 외부 URL(http://, https://, //)은 거부한다.
     */
    private boolean isValidRedirectPath(String path) {
        if (path == null || path.isBlank()) return false;
        if (!path.startsWith("/")) return false;
        if (path.startsWith("//")) return false;
        return !path.contains("://");
    }
}
