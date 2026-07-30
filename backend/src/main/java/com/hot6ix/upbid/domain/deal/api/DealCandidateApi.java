package com.hot6ix.upbid.domain.deal.api;

import com.hot6ix.upbid.global.response.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "거래 상태 변경", description = "판매자용 낙찰 후보 거래 성사·실패 처리 API")
public interface DealCandidateApi {

    @Operation(
            summary = "거래 실패 처리와 차순위 전환",
            description = "현재 낙찰자와의 거래가 깨진 것을 기록하고, 차순위 후보가 있으면 낙찰 권한을 넘긴다. "
                    + "차순위가 없어도 물품은 낙찰(SOLD) 상태로 남는다 — 경매 결과와 거래 결과는 별개다. "
                    + "요청 본문은 없다. 해당 물품의 판매자만 호출할 수 있다. "
                    + "인증 인프라가 아직 없어 X-User-Id 헤더로 회원을 임시 식별하며, 세션 인증이 도입되면 교체돼야 한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 성공"),
            @ApiResponse(responseCode = "400", description = "경로 변수가 숫자가 아님 (code 2002)"),
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
            @Parameter(description = "요청 회원 ID (임시 인증 헤더)", required = true)
            @RequestHeader("X-User-Id") Long userId);

    @Operation(
            summary = "거래 성사 확정",
            description = "현재 낙찰자와의 거래 성사를 확정한다. 확정 후에는 같은 물품의 거래 상태를 더 바꿀 수 없다. "
                    + "요청 본문은 없다. 해당 물품의 판매자만 호출할 수 있다. "
                    + "인증 인프라가 아직 없어 X-User-Id 헤더로 회원을 임시 식별하며, 세션 인증이 도입되면 교체돼야 한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 성공"),
            @ApiResponse(responseCode = "400", description = "경로 변수가 숫자가 아님 (code 2002)"),
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
            @Parameter(description = "요청 회원 ID (임시 인증 헤더)", required = true)
            @RequestHeader("X-User-Id") Long userId);
}
