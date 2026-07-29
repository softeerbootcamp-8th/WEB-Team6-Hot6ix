package com.hot6ix.upbid.domain.product.api;

import com.hot6ix.upbid.domain.product.dto.request.ProductCreateRequestDto;
import com.hot6ix.upbid.domain.product.dto.response.ProductResponseDto;
import com.hot6ix.upbid.domain.product.dto.response.ProductSummaryResponseDto;
import com.hot6ix.upbid.domain.product.entity.ProductListingStatus;
import com.hot6ix.upbid.global.response.CommonResponse;
import com.hot6ix.upbid.global.response.CursorPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

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

    @Operation(
            summary = "상품 상세 조회",
            description = "로그인한 판매자 본인 소유의 상품을 상세 조회한다. 본인 소유가 아니거나 존재하지 않는 상품은 "
                    + "동일하게 404로 응답해 다른 판매자의 상품 존재 여부를 노출하지 않는다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "판매자 프로필이 없음 (code 3002) 또는 "
                    + "상품이 없거나 본인 소유가 아님 (code 5001)")
    })
    ResponseEntity<CommonResponse<ProductResponseDto>> getDetail(
            @Parameter(description = "요청 회원 ID (임시 인증 헤더)", required = true)
            @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "조회할 상품 ID", required = true)
            @PathVariable Long productId);

    @Operation(
            summary = "내 상품 목록 조회",
            description = "로그인한 판매자 본인의 상품 목록을 productId 최신순으로 조회한다. 정렬 키를 항상 불변인 "
                    + "productId로 고정해 커서 페이지네이션이 안정적으로 동작하며, 상태(등록 여부·경매 진행 상태)는 "
                    + "정렬이 아니라 필터로만 사용한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "판매자 프로필이 없음 (code 3002)")
    })
    ResponseEntity<CommonResponse<CursorPageResponse<ProductSummaryResponseDto>>> getList(
            @Parameter(description = "요청 회원 ID (임시 인증 헤더)", required = true)
            @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "상품명 검색어")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "파생 상태 필터 — 연결된 AuctionItem이 없으면 UNLISTED, "
                    + "있으면 그 상태에 따라 READY/IN_PROGRESS/ENDED(낙찰·유찰 병합)")
            @RequestParam(required = false) ProductListingStatus status,
            @Parameter(description = "이전 페이지 마지막 상품의 productId, 없으면 첫 페이지")
            @RequestParam(required = false) Long cursor,
            @Parameter(description = "페이지 크기, 기본값 20")
            @RequestParam(required = false) Integer size);
}
