package com.hot6ix.upbid.domain.auth.controller;

import com.hot6ix.upbid.domain.auth.dto.AuthLoginResponseDto;
import com.hot6ix.upbid.domain.auth.service.AuthService;
import com.hot6ix.upbid.global.response.CommonResponse;
import com.hot6ix.upbid.global.session.SessionManager;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SessionManager sessionManager;

    @GetMapping("/kakao/login")
    public ResponseEntity<CommonResponse<AuthLoginResponseDto>> kakaoLogin(
            @RequestParam String code,
            HttpServletRequest request
    ) {
        AuthLoginResponseDto login = authService.login(code);
        sessionManager.create(request, login.userId());

        return ResponseEntity.ok(CommonResponse.ok(login, "로그인에 성공했습니다."));
    }

    @PostMapping("/logout")
    public ResponseEntity<CommonResponse<Void>> logout(HttpServletRequest request) {

        sessionManager.invalidate(request);

        return ResponseEntity.ok(CommonResponse.ok("로그아웃되었습니다."));
    }
}
