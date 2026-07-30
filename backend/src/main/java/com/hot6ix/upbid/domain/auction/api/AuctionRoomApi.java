package com.hot6ix.upbid.domain.auction.api;

import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomCreateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomUpdateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomResponseDto;
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
import org.springframework.web.bind.annotation.RequestHeader;


@Tag(name = "경매방", description = "경매방 생성·조회·수정 API")
public interface AuctionRoomApi {

    @Operation(
            summary = "경매방 생성",
            description = "판매자가 경매방을 생성한다. share_code는 서버가 내부적으로 발급하며, 이를 노출하는 API는 "
                    + "별도로 제공된다. 인증 인프라가 아직 없어 X-User-Id 헤더로 회원을 임시 식별하며, "
                    + "세션 인증이 도입되면 교체돼야 한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공"),
            @ApiResponse(responseCode = "400", description = "요청 필드 형식 위반 (code 2002)"),
            @ApiResponse(responseCode = "404", description = "판매자 프로필이 없음 (code 3002)")
    })
    ResponseEntity<CommonResponse<AuctionRoomResponseDto>> create(
            @Parameter(description = "요청 회원 ID (임시 인증 헤더)", required = true)
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody AuctionRoomCreateRequestDto request);

    @Operation(
            summary = "경매방 정보 조회",
            description = "경매방의 공개 정보를 조회한다. 인증이 필요 없으며, 경매 시작 전(BEFORE)을 포함한 "
                    + "모든 상태에서 동일하게 노출한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "경로 변수가 숫자가 아니라면 code 2002"),
            @ApiResponse(responseCode = "404", description = "존재하지 않거나 삭제된 경매방 (code 4002)")
    })
    ResponseEntity<CommonResponse<AuctionRoomResponseDto>> getRoom(
            @Parameter(description = "조회할 경매방 ID", required = true)
            @PathVariable Long roomId);

    @Operation(
            summary = "경매방 설정 수정",
            description = "소유자가 경매방 설정을 부분 수정한다. 요청에서 생략된 필드는 기존 값을 유지한다. "
                    + "\"경매 시작 전\"에만 허용되는데, 이번 PR에서는 방 생성 즉시 시작으로 간주하므로 "
                    + "이 API는 존재·권한 확인까지는 정상 동작하되 항상 거절된다(409). 실제 조건부 허용은 "
                    + "이후 PR에서 재정의된다. 인증 인프라가 아직 없어 X-User-Id 헤더로 회원을 임시 식별한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "요청 필드 형식 위반 (code 2002)"),
            @ApiResponse(responseCode = "404", description = "판매자 프로필이 없음 (code 3002) 또는 "
                    + "경매방이 없거나 본인 소유가 아님 (code 4002)"),
            @ApiResponse(responseCode = "409", description = "경매가 시작된 것으로 간주되어 수정 불가 (code 4003)")
    })
    ResponseEntity<CommonResponse<AuctionRoomResponseDto>> update(
            @Parameter(description = "요청 회원 ID (임시 인증 헤더)", required = true)
            @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "수정할 경매방 ID", required = true)
            @PathVariable Long roomId,
            @Valid @RequestBody AuctionRoomUpdateRequestDto request);
}
