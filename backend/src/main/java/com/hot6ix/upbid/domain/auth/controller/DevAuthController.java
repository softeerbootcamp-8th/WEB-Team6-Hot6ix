package com.hot6ix.upbid.domain.auth.controller;

import com.hot6ix.upbid.domain.auth.config.DevLoginCondition;
import com.hot6ix.upbid.domain.auth.config.DevLoginGate;
import com.hot6ix.upbid.domain.auth.service.DevAuthService;
import com.hot6ix.upbid.global.interceptor.GuestAllowed;
import com.hot6ix.upbid.global.response.CommonResponse;
import com.hot6ix.upbid.global.session.SessionManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Conditional(DevLoginCondition.class)
@Tag(name = "개발용 인증", description = "로컬 개발·부하 측정 환경 전용 — 운영에서는 토큰을 준 동안에만 열린다")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class DevAuthController {

    private final DevAuthService devAuthService;
    private final SessionManager sessionManager;
    private final DevLoginGate devLoginGate;

    @GuestAllowed
    @PostMapping("/dev-login")
    @Operation(
            summary = "[로컬·부하 측정 전용] 테스트 로그인",
            description = """
                    테스트 유저(provider=dev)로 세션을 발급합니다. 최초 호출 시 유저를 자동 생성합니다.

                    key로 회원을 가릅니다. 같은 key는 같은 회원, 다른 key는 다른 회원입니다.
                    부하 측정에서 가상 사용자 N명을 서로 다른 회원으로 만들 때 씁니다.
                    key를 생략하면 예전과 같은 단일 테스트 유저가 나옵니다.

                    로컬과 부하 측정 환경에서는 헤더 없이 부를 수 있습니다.
                    운영에서 측정 창구에만 열어 둘 때는 X-Dev-Login-Token 헤더가 필요합니다.
                    """
    )
    public ResponseEntity<CommonResponse<Void>> devLogin(
            HttpServletRequest request,

            @Parameter(description = "회원을 가르는 값. 생략하면 기본 테스트 유저", example = "bidder-1")
            @RequestParam(required = false) String key,

            @Parameter(description = "운영에서 게이트를 걸어 둔 동안에만 필요한 토큰")
            @RequestHeader(value = DevLoginGate.HEADER, required = false) String devLoginToken) {

        devLoginGate.verify(devLoginToken);

        Long userId = devAuthService.findOrCreateDevUser(key);
        sessionManager.create(request, userId);

        return ResponseEntity.ok(CommonResponse.ok("테스트 로그인 성공"));
    }
}
