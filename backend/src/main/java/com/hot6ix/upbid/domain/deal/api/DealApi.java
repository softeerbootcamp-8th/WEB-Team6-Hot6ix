package com.hot6ix.upbid.domain.deal.api;

import com.hot6ix.upbid.domain.deal.dto.response.DealSummaryResponseDto;
import com.hot6ix.upbid.global.interceptor.LoginUserId;
import com.hot6ix.upbid.global.response.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "거래 내역", description = "내가 판 것과 산 것을 모아 보는 API")
public interface DealApi {

    @Operation(
            summary = "거래 내역 목록 조회",
            description = "로그인한 회원이 관여한 거래를 최근 마감 순으로 조회한다. "
                    + "판매 건은 내 경매방의 마감된 물품이고, 구매 건은 내가 낙찰 후보로 오른 물품이다. "
                    + "화면이 전체를 받아 역할·상태로 거르고 건수도 직접 세므로 필터 파라미터가 없다. "
                    + "연락처는 내려가지 않는다 — 구매자는 sellerProfileId로 판매자 프로필을 조회한다. "
                    + "거래가 없으면 빈 배열이다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)")
    })
    ResponseEntity<CommonResponse<List<DealSummaryResponseDto>>> getDeals(
            @Parameter(hidden = true) @LoginUserId Long userId);
}
