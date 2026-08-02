package com.hot6ix.upbid.domain.bid.api;

import com.hot6ix.upbid.domain.bid.dto.request.BidCreateRequestDto;
import com.hot6ix.upbid.domain.bid.dto.response.BidCreateResponseDto;
import com.hot6ix.upbid.global.interceptor.LoginUserId;
import com.hot6ix.upbid.global.response.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "입찰", description = "입찰자용 입찰 등록 API")
public interface BidApi {

    @Operation(
            summary = "입찰 등록",
            description = "진행중인 물품에 입찰한다. 접수되면 물품의 현재가와 최고 입찰자가 함께 갱신된다. "
                    + "최소 입찰 금액은 입찰이 아직 없으면 시작가, 있으면 현재가 + 입찰 단위다. "
                    + "시작가와 같은 금액으로 첫 입찰을 할 수 있다. "
                    + "금액은 (금액 - 시작가)가 입찰 단위의 배수여야 하며, 여러 단위를 한 번에 올릴 수 있다. "
                    + "이미 최고 입찰자인 회원은 다시 입찰할 수 없다. "
                    + "물품을 올린 판매자 본인은 입찰할 수 없다. "
                    + "로그인 세션의 회원으로 입찰한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "입찰 접수"),
            @ApiResponse(responseCode = "400", description = "입찰 금액 누락·음수 (code 2002), "
                    + "경로 변수가 숫자가 아님 (code 2002)"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)"),
            @ApiResponse(responseCode = "403", description = "판매자 본인의 입찰 (code 7007). "
                    + "재시도해도 통과하지 않는다"),
            @ApiResponse(responseCode = "404", description = "물품이 없음 (code 4001), "
                    + "회원이 없거나 탈퇴함 (code 2003)"),
            @ApiResponse(responseCode = "409", description = "진행중인 물품이 아님 (code 7001), "
                    + "마감된 물품 (code 7002), 이미 최고 입찰자 (code 7003), "
                    + "최소 입찰 금액 미달 (code 7004), 입찰 단위 불일치 (code 7005), "
                    + "동시 입찰 충돌 (code 7006)")
    })
    ResponseEntity<CommonResponse<BidCreateResponseDto>> place(
            @Parameter(description = "입찰할 물품 ID", required = true)
            @PathVariable Long auctionItemId,
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Valid @RequestBody BidCreateRequestDto request);
}
