package com.hot6ix.upbid.domain.deal.api;

import com.hot6ix.upbid.domain.deal.dto.response.AuctionRoomDealStatusResponseDto;
import com.hot6ix.upbid.global.interceptor.LoginUserId;
import com.hot6ix.upbid.global.response.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "경매방 거래 현황", description = "판매자가 자기 경매방의 거래 진행 상황을 보는 API")
public interface AuctionRoomDealApi {

    @Operation(
            summary = "경매방 거래 현황 조회",
            description = "판매자가 자기 경매방의 물품별 거래 진행 상황을 조회한다. 마감된 물품만 담기며, "
                    + "아직 시작하지 않았거나 진행 중인 물품은 거래가 시작된 적이 없어 빠진다. "
                    + "거래 상대는 성사된 후보가 있으면 그 사람, 없으면 순서를 기다리는 최저 순위 후보다 — "
                    + "실패한 후보와 탈퇴 회원은 제외된다. amount는 낙찰가가 아니라 그 상대가 부른 금액이라, "
                    + "1순위가 실패해 차순위로 넘어가면 금액도 함께 바뀐다. "
                    + "dealCandidateId를 함께 주므로 화면이 거래 성사·실패를 바로 호출할 수 있다. "
                    + "연락처는 내려가지 않는다 — 낙찰 후보 조회에서 판매자에게만 보인다. "
                    + "경매방이 없을 때와 본인 소유가 아닐 때를 구분하지 않고 모두 404로 응답한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "경로 변수가 숫자가 아니라면 code 2002"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)"),
            @ApiResponse(responseCode = "404", description = "판매자 프로필이 없음 (code 3002) 또는 "
                    + "경매방이 없거나 본인 소유가 아님 (code 4002)")
    })
    ResponseEntity<CommonResponse<AuctionRoomDealStatusResponseDto>> getRoomDeals(
            @Parameter(description = "거래 현황을 조회할 경매방 ID", required = true)
            @PathVariable Long roomId,
            @Parameter(hidden = true) @LoginUserId Long userId);
}
