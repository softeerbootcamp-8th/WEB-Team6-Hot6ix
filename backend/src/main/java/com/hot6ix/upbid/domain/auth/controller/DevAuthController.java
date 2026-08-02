package com.hot6ix.upbid.domain.auth.controller;

import com.hot6ix.upbid.domain.auth.service.DevAuthService;
import com.hot6ix.upbid.global.interceptor.GuestAllowed;
import com.hot6ix.upbid.global.response.CommonResponse;
import com.hot6ix.upbid.global.session.SessionManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("local")
@Tag(name = "개발용 인증", description = "로컬 개발 환경 전용 — 운영 배포 시 비활성화됨")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class DevAuthController {

    private final DevAuthService devAuthService;
    private final SessionManager sessionManager;

    @GuestAllowed
    @PostMapping("/dev-login")
    @Operation(
            summary = "[로컬 전용] 테스트 로그인",
            description = "테스트 유저(provider=dev)로 세션을 발급합니다. 최초 호출 시 유저를 자동 생성합니다."
    )
    public ResponseEntity<CommonResponse<Void>> devLogin(HttpServletRequest request) {

        Long userId = devAuthService.findOrCreateDevUser();
        sessionManager.create(request, userId);

        return ResponseEntity.ok(CommonResponse.ok("테스트 로그인 성공"));
    }
}
