package com.hot6ix.upbid.domain.auth.api;

import com.hot6ix.upbid.global.response.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

@Tag(name = "인증", description = "세션 기반 인증 API")
public interface AuthApi {

    @Operation(
            summary = "로그아웃",
            description = "현재 세션을 무효화한다. 세션 쿠키(SESSION)도 함께 만료되므로 이후 요청은 인증되지 않은 요청으로 처리된다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그아웃 성공"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)")
    })
    ResponseEntity<CommonResponse<Void>> logout(HttpServletRequest request);
}
