package com.hot6ix.upbid.domain.user.api;

import com.hot6ix.upbid.domain.user.dto.request.SellerProfileCreateRequestDto;
import com.hot6ix.upbid.domain.user.dto.request.SellerProfileUpdateRequestDto;
import com.hot6ix.upbid.domain.user.dto.response.SellerProfileResponseDto;
import com.hot6ix.upbid.global.interceptor.LoginUserId;
import com.hot6ix.upbid.global.response.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "판매자 프로필", description = "판매자 프로필 등록·조회·수정·삭제(soft delete) API")
public interface SellerProfileApi {

    @Operation(
            summary = "판매자 프로필 등록",
            description = "회원의 판매자 프로필을 등록한다. 회원당 하나만 허용하며, 이미 등록된 프로필이 있으면 거절한다. "
                    + "로그인 세션의 회원으로 등록한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(responseCode = "400", description = "요청 필드 형식 위반 (code 2002)"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 회원 (code 2003)"),
            @ApiResponse(responseCode = "409", description = "이미 등록된 판매자 프로필이 있음 (code 3001)")
    })
    ResponseEntity<CommonResponse<SellerProfileResponseDto>> create(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Valid @RequestBody SellerProfileCreateRequestDto request);

    @Operation(
            summary = "판매자 프로필 조회",
            description = "로그인한 회원의 판매자 프로필을 조회한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)"),
            @ApiResponse(responseCode = "404", description = "판매자 프로필을 찾을 수 없음 (code 3002)")
    })
    ResponseEntity<CommonResponse<SellerProfileResponseDto>> getMyProfile(
            @Parameter(hidden = true) @LoginUserId Long userId);

    @Operation(
            summary = "판매자 프로필 수정",
            description = "로그인한 회원의 판매자 프로필을 요청 값으로 전체 교체한다. storeName은 필수이며, "
                    + "나머지 선택 필드를 생략하면 null로 지워진다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "요청 필드 형식 위반 (code 2002)"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)"),
            @ApiResponse(responseCode = "404", description = "판매자 프로필을 찾을 수 없음 (code 3002)")
    })
    ResponseEntity<CommonResponse<SellerProfileResponseDto>> update(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Valid @RequestBody SellerProfileUpdateRequestDto request);

    @Operation(
            summary = "판매자 프로필 삭제",
            description = "로그인한 회원의 판매자 프로필을 soft delete 한다. 경매 이력 보존을 위해 실제 row는 남긴다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)"),
            @ApiResponse(responseCode = "404", description = "판매자 프로필을 찾을 수 없음 (code 3002)")
    })
    ResponseEntity<CommonResponse<Void>> delete(
            @Parameter(hidden = true) @LoginUserId Long userId);
}
