package com.hot6ix.upbid.domain.auction.api;

import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemDetailResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemSummaryResponseDto;
import com.hot6ix.upbid.global.response.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "경매 물품 조회", description = "입찰자용 경매 물품 목록·상세 조회 API")
public interface AuctionItemApi {

    @Operation(
            summary = "경매방 물품 목록 조회",
            description = "경매방의 물품을 진행중 → 대기 → 낙찰 → 유찰 순으로 조회한다. "
                    + "페이지네이션이 없으며 한 응답에 최대 100건까지 담긴다. "
                    + "지금은 경매방 존재 여부는 확인하지 않으므로(나중에 추가할 예정입니다.), 없는 경매방도 빈 배열로 응답한다. "
                    + "비로그인으로 조회할 수 있다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공. 물품이 없으면 빈 배열"),
            @ApiResponse(responseCode = "400", description = "경로 변수가 숫자가 아니라면 code 2002 반환")
    })
    ResponseEntity<CommonResponse<List<AuctionItemSummaryResponseDto>>> getSummaries(
            @Parameter(description = "조회할 경매방 ID", required = true)
            @PathVariable Long auctionRoomId);

    @Operation(
            summary = "경매 물품 상세 조회",
            description = "물품 상세를 조회한다. 낙찰·유찰된 물품도 조회된다. "
                    + "비로그인으로 조회할 수 있다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "경로 변수가 숫자가 아니라면 code 2002"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 경매 물품이라면 code 4001")
    })
    ResponseEntity<CommonResponse<AuctionItemDetailResponseDto>> getDetail(
            @Parameter(description = "조회할 물품 ID", required = true)
            @PathVariable Long auctionItemId);
}
