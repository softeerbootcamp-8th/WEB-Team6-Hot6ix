package com.hot6ix.upbid.domain.auction.api;

import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomCreateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomUpdateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomPublicResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomResultResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomShareResponseDto;
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


@Tag(name = "경매방", description = "경매방 생성·조회·수정 API")
public interface AuctionRoomApi {

    @Operation(
            summary = "경매방 생성",
            description = "판매자가 경매방을 생성한다. share_code는 서버가 내부적으로 발급하며, 이를 노출하는 API는 "
                    + "별도로 제공된다. 로그인 세션의 회원으로 생성한다. "
                    + "입찰 단위(bidIncrement)는 이 방의 모든 물품이 공유하며, 물품을 추가할 때 물품으로 복사된다. "
                    + "복사된 뒤에는 어긋날 수 있어 설정 수정(PATCH)으로는 바꿀 수 없다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공"),
            @ApiResponse(responseCode = "400", description = "요청 필드 형식 위반 (code 2002)"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)"),
            @ApiResponse(responseCode = "404", description = "판매자 프로필이 없음 (code 3002)")
    })
    ResponseEntity<CommonResponse<AuctionRoomPublicResponseDto>> create(
            @Parameter(hidden = true) @LoginUserId Long userId,
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
    ResponseEntity<CommonResponse<AuctionRoomPublicResponseDto>> getRoom(
            @Parameter(description = "조회할 경매방 ID", required = true)
            @PathVariable Long roomId);

    @Operation(
            summary = "경매방 낙찰 결과 조회",
            description = "경매방의 물품별 낙찰·유찰 결과를 한 번에 조회한다. 인증이 필요 없고, 로그인한 "
                    + "요청에만 물품마다 요청자의 최종 순위(myRank)와 부른 최고가(myAmount)가 함께 담긴다. "
                    + "낙찰가와 낙찰자는 낙찰(SOLD)인 물품에만 있다 — 유찰 물품의 현재가는 아무도 부르지 "
                    + "않은 시작가라 가격으로 내리지 않는다. "
                    + "방 상태로 거르지 않으므로 아직 열려 있는 방도 조회되며, 진행 중인 물품은 status로 드러난다. "
                    + "낙찰 건수·유찰 건수·총 낙찰액은 화면이 items에서 직접 세므로 응답에 없다. "
                    + "참여자 수도 없다 — 종료된 방의 참여자 수는 입찰한 사람 수인지 방송을 보던 사람 수인지 "
                    + "구분되지 않아 내리지 않는다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "경로 변수가 숫자가 아니라면 code 2002"),
            @ApiResponse(responseCode = "404", description = "존재하지 않거나 삭제된 경매방 (code 4002)")
    })
    ResponseEntity<CommonResponse<AuctionRoomResultResponseDto>> getResults(
            @Parameter(description = "결과를 조회할 경매방 ID", required = true)
            @PathVariable Long roomId,
            @Parameter(hidden = true) @LoginUserId Long userId);

    @Operation(
            summary = "경매방 공유 링크 조회",
            description = "소유자가 자기 경매방의 공유 링크를 조회한다. QR 코드는 서버가 이미지로 만들지 않고 "
                    + "클라이언트가 이 shareUrl 문자열을 그대로 렌더링한다. 경매방이 없을 때와 본인 소유가 "
                    + "아닐 때를 구분하지 않고 모두 404로 응답한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)"),
            @ApiResponse(responseCode = "404", description = "판매자 프로필이 없음 (code 3002) 또는 "
                    + "경매방이 없거나 본인 소유가 아님 (code 4002)")
    })
    ResponseEntity<CommonResponse<AuctionRoomShareResponseDto>> getShareInfo(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "공유 정보를 조회할 경매방 ID", required = true)
            @PathVariable Long roomId);

    @Operation(
            summary = "공유 코드로 경매방 정보 조회",
            description = "공유 링크·QR로 들어온 사람이 방에 진입할 때 쓴다. 인증이 필요 없으며 응답은 "
                    + "경매방 정보 조회(GET /{roomId})와 동일하다. 숫자 PK인 roomId를 공개 URL에 노출하면 "
                    + "다른 방을 추측해 순회 조회할 수 있어, 공개 진입점은 share_code로만 제공한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "해당 공유 코드의 경매방이 없거나 삭제됨 (code 4002)")
    })
    ResponseEntity<CommonResponse<AuctionRoomPublicResponseDto>> getRoomByShareCode(
            @Parameter(description = "경매방 공유 코드", required = true)
            @PathVariable String shareCode);

    @Operation(
            summary = "경매방 설정 수정",
            description = "소유자가 경매방 설정을 부분 수정한다. 요청에서 생략된 필드는 기존 값을 유지한다. "
                    + "이 방의 물품 중 하나라도 READY가 아닌 상태로 경매에 올라간 적 있으면(=경매가 시작된 적 있으면) "
                    + "이후로도 계속 수정할 수 없다. 로그인 세션의 회원을 소유자로 확인한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "요청 필드 형식 위반 (code 2002)"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)"),
            @ApiResponse(responseCode = "404", description = "판매자 프로필이 없음 (code 3002) 또는 "
                    + "경매방이 없거나 본인 소유가 아님 (code 4002)"),
            @ApiResponse(responseCode = "409", description = "경매방이 시작된 적 있는 물품을 포함해 수정 불가 (code 4003)")
    })
    ResponseEntity<CommonResponse<AuctionRoomPublicResponseDto>> update(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "수정할 경매방 ID", required = true)
            @PathVariable Long roomId,
            @Valid @RequestBody AuctionRoomUpdateRequestDto request);
}
