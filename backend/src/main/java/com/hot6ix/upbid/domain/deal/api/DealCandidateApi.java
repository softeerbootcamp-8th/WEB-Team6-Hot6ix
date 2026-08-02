package com.hot6ix.upbid.domain.deal.api;

import com.hot6ix.upbid.domain.deal.dto.response.DealCandidateListResponseDto;
import com.hot6ix.upbid.global.interceptor.LoginUserId;
import com.hot6ix.upbid.global.response.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "낙찰 후보", description = "낙찰 후보 조회와 거래 성사·실패 처리 API")
public interface DealCandidateApi {

    @Operation(
            summary = "낙찰 후보 목록 조회",
            description = "물품의 낙찰 후보를 순위 오름차순으로 5명씩 조회한다. "
                    + "판매자와 입찰자가 같은 경로를 쓰고 역할은 서버가 판정한다 — "
                    + "그 물품의 판매자면 SELLER, 후보로 오른 입찰자면 BIDDER다. "
                    + "닉네임과 입찰가는 경매 결과라 모두에게 공개하고, "
                    + "연락처는 판매자가 볼 때 거래 상대인 후보(거래 중·성사)만 내려간다. "
                    + "구매자에게는 다른 후보의 연락처를 주지 않는다. "
                    + "myRank는 구매자의 순위이며 요청한 페이지 밖에 있어도 값이 나온다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "경로 변수나 page가 숫자가 아님 (code 2002)"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)"),
            @ApiResponse(responseCode = "403", description = "그 물품의 판매자도 후보도 아님 (code 6007)"),
            @ApiResponse(responseCode = "404", description = "물품이 없음 (code 4001)")
    })
    ResponseEntity<CommonResponse<DealCandidateListResponseDto>> getCandidates(
            @Parameter(description = "조회할 물품 ID", required = true)
            @PathVariable Long auctionItemId,
            @Parameter(description = "0부터 시작하는 페이지 번호. 크기는 5로 고정이다")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(hidden = true) @LoginUserId Long userId);

    @Operation(
            summary = "거래 실패 처리와 차순위 전환",
            description = "현재 낙찰자와의 거래가 깨진 것을 기록하고, 차순위 후보가 있으면 낙찰 권한을 넘긴다. "
                    + "차순위가 없어도 물품은 낙찰(SOLD) 상태로 남는다 — 경매 결과와 거래 결과는 별개다. "
                    + "요청 본문은 없다. 해당 물품의 판매자만 호출할 수 있다. "
                    + "로그인 세션의 회원을 판매자로 확인한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 성공"),
            @ApiResponse(responseCode = "400", description = "경로 변수가 숫자가 아님 (code 2002)"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)"),
            @ApiResponse(responseCode = "403", description = "해당 물품의 판매자가 아님 (code 6001)"),
            @ApiResponse(responseCode = "404", description = "물품이 없음 (code 4001), 낙찰 후보가 없음 (code 6003)"),
            @ApiResponse(responseCode = "409", description = "낙찰이 확정되지 않은 물품 (code 6002), "
                    + "이미 처리된 후보 (code 6004), 현재 낙찰 권한자가 아님 (code 6005), "
                    + "이미 완료된 거래 (code 6006)")
    })
    ResponseEntity<CommonResponse<Void>> fail(
            @Parameter(description = "대상 물품 ID", required = true)
            @PathVariable Long auctionItemId,
            @Parameter(description = "실패로 기록할 낙찰 후보 ID", required = true)
            @PathVariable Long candidateId,
            @Parameter(hidden = true) @LoginUserId Long userId);

    @Operation(
            summary = "거래 성사 확정",
            description = "현재 낙찰자와의 거래 성사를 확정한다. 확정 후에는 같은 물품의 거래 상태를 더 바꿀 수 없다. "
                    + "요청 본문은 없다. 해당 물품의 판매자만 호출할 수 있다. "
                    + "로그인 세션의 회원을 판매자로 확인한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 성공"),
            @ApiResponse(responseCode = "400", description = "경로 변수가 숫자가 아님 (code 2002)"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)"),
            @ApiResponse(responseCode = "403", description = "해당 물품의 판매자가 아님 (code 6001)"),
            @ApiResponse(responseCode = "404", description = "물품이 없음 (code 4001), 낙찰 후보가 없음 (code 6003)"),
            @ApiResponse(responseCode = "409", description = "낙찰이 확정되지 않은 물품 (code 6002), "
                    + "이미 처리된 후보 (code 6004), 현재 낙찰 권한자가 아님 (code 6005), "
                    + "이미 완료된 거래 (code 6006)")
    })
    ResponseEntity<CommonResponse<Void>> complete(
            @Parameter(description = "대상 물품 ID", required = true)
            @PathVariable Long auctionItemId,
            @Parameter(description = "성사로 기록할 낙찰 후보 ID", required = true)
            @PathVariable Long candidateId,
            @Parameter(hidden = true) @LoginUserId Long userId);
}
