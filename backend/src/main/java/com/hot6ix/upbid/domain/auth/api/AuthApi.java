package com.hot6ix.upbid.domain.auth.api;

import com.hot6ix.upbid.global.response.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;

@Tag(name = "인증", description = "세션 기반 인증 API")
public interface AuthApi {

    @Operation(
            summary = "회원가입",
            description = "카카오 인증과 전화번호 인증을 마친 가입 대기 상태를 정식 회원으로 저장하고 로그인 세션을 발급한다. "
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "회원가입 및 로그인 성공"),
            @ApiResponse(responseCode = "400", description = "전화번호 인증 미완료 (code 1010)"),
            @ApiResponse(responseCode = "401", description = "가입 진행 정보 없음 또는 만료 (code 1009)"),
            @ApiResponse(responseCode = "422", description = "사용자 정보가 정책에 위배됨 (code 1008)")
    })
    ResponseEntity<CommonResponse<Void>> signup(HttpServletRequest request);

    @Operation(
            summary = "로그아웃",
            description = "현재 세션을 무효화한다. 세션 쿠키(SESSION)도 함께 만료되므로 이후 요청은 인증되지 않은 요청으로 처리된다. "
                    + "세션이 이미 없거나 만료된 상태로 호출해도 성공으로 응답한다(멱등) — 로그아웃의 목적이 이미 달성된 상태이며, "
                    + "만료된 세션으로 로그아웃을 시도하는 것이 정상적인 흐름이기 때문이다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그아웃 성공 (세션이 없던 경우 포함)")
    })
    ResponseEntity<CommonResponse<Void>> logout(HttpServletRequest request, HttpServletResponse response);
}
