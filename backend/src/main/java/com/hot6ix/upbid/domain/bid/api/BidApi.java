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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "입찰", description = "입찰자용 입찰 등록 API")
public interface BidApi {

    @Operation(
            summary = "입찰 등록",
            description = "진행중인 물품에 입찰한다. Idempotency-Key는 최초 요청과 재요청에서 동일해야 한다. "
                    + "접수되면 Redis의 현재가와 최고 입찰자가 갱신되고 MySQL에는 비동기로 저장된다. "
                    + "최소 입찰 금액은 입찰이 아직 없으면 시작가, 있으면 현재가 + 입찰 단위다. "
                    + "시작가와 같은 금액으로 첫 입찰을 할 수 있다. "
                    + "금액은 (금액 - 시작가)가 입찰 단위의 배수여야 하며, 여러 단위를 한 번에 올릴 수 있다. "
                    + "이미 최고 입찰자인 회원은 다시 입찰할 수 없다. "
                    + "물품을 올린 판매자 본인은 입찰할 수 없다. "
                    + "공유 링크로 들어와 경매방 입장 약관에 동의한 회원만 입찰할 수 있다. "
                    + "로그인 세션의 회원으로 입찰한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "입찰 접수"),
            @ApiResponse(responseCode = "400", description = "멱등 키 누락·공백·64자 초과 또는 "
                    + "입찰 금액 누락·음수 (code 2002), "
                    + "경로 변수가 숫자가 아님 (code 2002)"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)"),
            @ApiResponse(responseCode = "403", description = "판매자 본인의 입찰 (code 7007) — "
                    + "재시도해도 통과하지 않는다. 경매방 입장 약관 미동의 (code 7008) — "
                    + "약관에 동의한 뒤에는 통과한다"),
            @ApiResponse(responseCode = "409", description = "진행중인 물품이 아님 (code 7001), "
                    + "마감된 물품 (code 7002), 이미 최고 입찰자 (code 7003), "
                    + "최소 입찰 금액 미달 (code 7004), 입찰 단위 불일치 (code 7005), "
                    + "동시 입찰 충돌 (code 7006), 멱등 키 재사용 충돌 (code 7010)"),
            @ApiResponse(responseCode = "429", description = "요청이 너무 많음 (code 7009) — "
                    + "잠시 후 다시 시도하면 통과할 수 있다")
    })
    ResponseEntity<CommonResponse<BidCreateResponseDto>> place(
            @Parameter(description = "입찰할 물품 ID", required = true)
            @PathVariable Long auctionItemId,
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "재요청에도 동일하게 사용하는 입찰 요청 식별자", required = true)
            @RequestHeader(value = "Idempotency-Key", required = false)
            @NotBlank(message = "멱등 키는 필수입니다.")
            @Size(max = 64, message = "멱등 키는 64자 이하여야 합니다.")
            String requestId,
            @Valid @RequestBody BidCreateRequestDto request);
}
