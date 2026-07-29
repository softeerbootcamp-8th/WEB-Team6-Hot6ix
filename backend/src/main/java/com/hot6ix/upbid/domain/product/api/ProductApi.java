package com.hot6ix.upbid.domain.product.api;

import com.hot6ix.upbid.domain.product.dto.request.ProductCreateRequestDto;
import com.hot6ix.upbid.domain.product.dto.response.ProductResponseDto;
import com.hot6ix.upbid.global.response.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "상품", description = "판매자 상품 등록·조회·수정·삭제 API")
public interface ProductApi {

    @Operation(
            summary = "상품 등록",
            description = "판매자가 경매에 낼 상품을 등록한다. 인증 인프라가 아직 없어 X-User-Id 헤더로 회원을 임시 식별하며, "
                    + "세션 인증이 도입되면 교체돼야 한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(responseCode = "400", description = "요청 필드 형식 위반 (code 2002)"),
            @ApiResponse(responseCode = "404", description = "판매자 프로필이 없음 (code 3002)")
    })
    ResponseEntity<CommonResponse<ProductResponseDto>> create(
            @Parameter(description = "요청 회원 ID (임시 인증 헤더)", required = true)
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ProductCreateRequestDto request);
}
