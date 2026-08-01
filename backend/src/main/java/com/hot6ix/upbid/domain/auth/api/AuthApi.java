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
            description = "현재 세션을 무효화한다. 세션 쿠키(SESSION)도 함께 만료되므로 이후 요청은 인증되지 않은 요청으로 처리된다. "
                    + "세션이 이미 없거나 만료된 상태로 호출해도 성공으로 응답한다(멱등) — 로그아웃의 목적이 이미 달성된 상태이며, "
                    + "만료된 세션으로 로그아웃을 시도하는 것이 정상적인 흐름이기 때문이다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그아웃 성공 (세션이 없던 경우 포함)")
    })
    ResponseEntity<CommonResponse<Void>> logout(HttpServletRequest request);
}
