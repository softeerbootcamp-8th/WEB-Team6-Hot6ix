package com.hot6ix.upbid.domain.user.api;

import com.hot6ix.upbid.domain.user.dto.response.UserMeResponseDto;
import com.hot6ix.upbid.global.interceptor.LoginUserId;
import com.hot6ix.upbid.global.response.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "유저", description = "유저 정보 조회 API")
public interface UserApi {

    @Operation(
            summary = "내 정보 조회",
            description = "세션의 유저 정보를 반환한다. 앱 초기화 시 호출해 sessionStore를 세팅한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 회원 (code 9001)")
    })
    ResponseEntity<CommonResponse<UserMeResponseDto>> getMe(
            @Parameter(hidden = true) @LoginUserId Long userId);
}
