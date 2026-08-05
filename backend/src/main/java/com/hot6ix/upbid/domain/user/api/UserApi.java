package com.hot6ix.upbid.domain.user.api;

import com.hot6ix.upbid.domain.user.dto.request.UserUpdateRequestDto;
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

    @Operation(
            summary = "내 프로필 수정",
            description = "닉네임과 프로필 이미지를 수정한다. 모든 필드를 함께 보내야 하며, "
                    + "profileImageUrl을 null로 보내면 이미지가 제거된다. "
                    + "이미지를 바꾸려면 먼저 /api/v1/uploads/presigned-url로 업로드 URL을 발급받아 S3에 올린 뒤 반환된 fileUrl을 넣는다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 오류 또는 허용되지 않는 이미지 주소 (code 5001)"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 회원 (code 9001)")
    })
    ResponseEntity<CommonResponse<UserMeResponseDto>> updateMe(
            @Parameter(hidden = true) @LoginUserId Long userId,
            UserUpdateRequestDto request);
}
