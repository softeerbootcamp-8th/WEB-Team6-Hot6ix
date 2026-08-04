package com.hot6ix.upbid.domain.product.api;

import com.hot6ix.upbid.domain.product.dto.request.ProductCreateRequestDto;
import com.hot6ix.upbid.domain.product.dto.request.ProductUpdateRequestDto;
import com.hot6ix.upbid.domain.product.dto.response.ProductResponseDto;
import com.hot6ix.upbid.domain.product.dto.response.ProductSummaryResponseDto;
import com.hot6ix.upbid.domain.product.entity.ProductListingStatus;
import com.hot6ix.upbid.global.interceptor.LoginUserId;
import com.hot6ix.upbid.global.response.CommonResponse;
import com.hot6ix.upbid.global.response.CursorPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "상품", description = "판매자 상품 등록·조회·수정·삭제 API")
public interface ProductApi {

    @Operation(
            summary = "상품 등록",
            description = "판매자가 경매에 낼 상품을 등록한다. 로그인 세션의 회원으로 등록한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(responseCode = "400", description = "요청 필드 형식 위반 (code 2002)"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)"),
            @ApiResponse(responseCode = "404", description = "판매자 프로필이 없음 (code 3002)")
    })
    ResponseEntity<CommonResponse<ProductResponseDto>> create(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Valid @RequestBody ProductCreateRequestDto request);

    @Operation(
            summary = "상품 상세 조회",
            description = "로그인한 판매자 본인 소유의 상품을 상세 조회한다. 본인 소유가 아니거나 존재하지 않는 상품은 "
                    + "동일하게 404로 응답해 다른 판매자의 상품 존재 여부를 노출하지 않는다. 응답의 status는 목록과 같은 "
                    + "규칙으로 계산한 파생 상태다 — 연결된 AuctionItem이 없으면 UNREGISTERED, 있으면 그 상태에 따라 "
                    + "READY/IN_PROGRESS/ENDED(낙찰·유찰 병합)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)"),
            @ApiResponse(responseCode = "404", description = "판매자 프로필이 없음 (code 3002) 또는 "
                    + "상품이 없거나 본인 소유가 아님 (code 5001)")
    })
    ResponseEntity<CommonResponse<ProductResponseDto>> getDetail(
            @Parameter(hidden = true) @LoginUserId Long userId,
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
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)"),
            @ApiResponse(responseCode = "404", description = "판매자 프로필이 없음 (code 3002)")
    })
    ResponseEntity<CommonResponse<CursorPageResponse<ProductSummaryResponseDto>>> getList(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "상품명 검색어")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "파생 상태 필터 — 연결된 AuctionItem이 없으면 UNREGISTERED, "
                    + "있으면 그 상태에 따라 READY/IN_PROGRESS/ENDED(낙찰·유찰 병합)")
            @RequestParam(required = false) ProductListingStatus status,
            @Parameter(description = "이전 페이지 마지막 상품의 productId, 없으면 첫 페이지")
            @RequestParam(required = false) @Positive(message = "cursor는 양수여야 합니다.") Long cursor,
            @Parameter(description = "페이지 크기, 기본값 20")
            @RequestParam(required = false) @Min(value = 1, message = "size는 1 이상이어야 합니다.") Integer size);

    @Operation(
            summary = "상품 수정",
            description = "로그인한 판매자 본인 소유의 상품을 요청 값으로 전체 교체한다. 경매방이 한 번이라도 "
                    + "시작된 적 있는 상품(READY가 아닌 AuctionItem이 하나라도 있으면)은 이후로도 계속 수정할 수 없다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "요청 필드 형식 위반 (code 2002)"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)"),
            @ApiResponse(responseCode = "404", description = "판매자 프로필이 없음 (code 3002) 또는 "
                    + "상품이 없거나 본인 소유가 아님 (code 5001)"),
            @ApiResponse(responseCode = "409", description = "경매방이 시작된 적 있는 상품 (code 5002)")
    })
    ResponseEntity<CommonResponse<ProductResponseDto>> update(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "수정할 상품 ID", required = true)
            @PathVariable Long productId,
            @Valid @RequestBody ProductUpdateRequestDto request);

    @Operation(
            summary = "상품 삭제",
            description = "로그인한 판매자 본인 소유의 상품을 soft delete 한다. 경매방에 물품으로 올라가 있으면 "
                    + "아직 시작 전(READY)이더라도 삭제할 수 없다 — 상품만 지우면 물품이 남아 삭제된 상품이 "
                    + "경매방에 계속 노출되기 때문이다. 시작 전이라면 경매방에서 물품을 먼저 빼면 삭제할 수 있다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)"),
            @ApiResponse(responseCode = "404", description = "판매자 프로필이 없음 (code 3002) 또는 "
                    + "상품이 없거나 본인 소유가 아님 (code 5001)"),
            @ApiResponse(responseCode = "409", description = "경매방에 올라가 있는 상품 (code 5003)")
    })
    ResponseEntity<CommonResponse<Void>> delete(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "삭제할 상품 ID", required = true)
            @PathVariable Long productId);
}
