package com.hot6ix.upbid.domain.auth.controller;

import com.hot6ix.upbid.domain.auth.api.AuthApi;
import com.hot6ix.upbid.global.response.CommonResponse;
import com.hot6ix.upbid.global.session.SessionManager;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final SessionManager sessionManager;

    @Override
    @PostMapping("/logout")
    public ResponseEntity<CommonResponse<Void>> logout(HttpServletRequest request) {

        sessionManager.invalidate(request);

        return ResponseEntity.ok(CommonResponse.ok("로그아웃되었습니다."));
    }
}
