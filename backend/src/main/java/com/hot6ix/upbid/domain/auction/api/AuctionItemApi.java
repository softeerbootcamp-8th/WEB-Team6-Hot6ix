package com.hot6ix.upbid.domain.auction.api;

import com.hot6ix.upbid.domain.auction.dto.request.AuctionItemAddRequestDto;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionItemBulkAddRequestDto;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionItemStartRequestDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemBulkAddResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemDetailResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemSummaryResponseDto;
import com.hot6ix.upbid.global.interceptor.LoginUserId;
import com.hot6ix.upbid.global.response.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "경매 물품", description = "입찰자용 경매 물품 조회 API와 판매자용 물품 추가·제외·시작 API")
public interface AuctionItemApi {

    @Operation(
            summary = "경매방 물품 목록 조회",
            description = "경매방의 물품을 진행중 → 대기 → 낙찰 → 유찰 순으로 조회한다. "
                    + "페이지네이션이 없으며 한 응답에 최대 100건까지 담긴다. "
                    + "물품마다 `leaderboard`에 상위 입찰자 3명이 순위·닉네임·금액으로 담긴다. "
                    + "한 사람이 여러 번 입찰해도 그 사람의 최고가로 한 줄만 나오고, "
                    + "탈퇴한 회원은 순위에서 빠진다. 입찰이 없으면 빈 배열이다. "
                    + "비로그인으로 조회할 수 있다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공. 물품이 없으면 빈 배열"),
            @ApiResponse(responseCode = "400", description = "경로 변수가 숫자가 아니라면 code 2002 반환"),
            @ApiResponse(responseCode = "404", description = "존재하지 않거나 삭제된 경매방이라면 code 4002")
    })
    ResponseEntity<CommonResponse<List<AuctionItemSummaryResponseDto>>> getSummaries(
            @Parameter(description = "조회할 경매방 ID", required = true)
            @PathVariable Long auctionRoomId);

    @Operation(
            summary = "경매 물품 상세 조회",
            description = "물품 상세를 조회한다. 낙찰·유찰된 물품도 조회된다. "
                    + "`leaderboard`에 상위 입찰자 3명이 담긴다. 규칙은 목록 조회와 같다. "
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

    @Operation(
            summary = "경매방 물품 추가",
            description = "소유자가 자기 상품을 경매방에 대기(READY) 물품으로 올린다. "
                    + "입찰 단위는 요청으로 받지 않고 경매방 값을 그대로 복사해, 한 방의 모든 물품이 같은 단위를 갖는다. "
                    + "한 상품은 한 번에 한 경매방에만 올릴 수 있어 이미 어딘가에 올라가 있으면 상태와 무관하게 거절한다"
                    + "(시작 전이라면 그 방에서 빼고 다시 올릴 수 있다). "
                    + "종료된 경매방에는 올릴 수 없고, 방송 중(OPEN)인 방에는 올릴 수 있다. "
                    + "경매방이 없을 때와 본인 소유가 아닐 때를 구분하지 않고 모두 404로 응답한다(상품도 동일)."
                    + " 응답의 `leaderboard`는 항상 빈 배열이다 — 방금 추가한 물품에는 입찰이 없다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "추가 성공"),
            @ApiResponse(responseCode = "400", description = "요청 필드 형식 위반 (code 2002)"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)"),
            @ApiResponse(responseCode = "404", description = "판매자 프로필이 없음 (code 3002), "
                    + "경매방이 없거나 본인 소유가 아님 (code 4002), "
                    + "상품이 없거나 본인 소유가 아님 (code 5001)"),
            @ApiResponse(responseCode = "409", description = "종료된 경매방 (code 4004) 또는 "
                    + "이미 경매방에 올라간 상품 (code 4005)")
    })
    ResponseEntity<CommonResponse<AuctionItemDetailResponseDto>> add(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "물품을 추가할 경매방 ID", required = true)
            @PathVariable Long auctionRoomId,
            @Valid @RequestBody AuctionItemAddRequestDto request);

    @Operation(
            summary = "경매방 물품 벌크 추가",
            description = "소유자가 여러 상품을 한 번에 경매방에 대기(READY) 물품으로 올린다. "
                    + "판정 규칙은 단건 추가와 같지만, **거절된 상품이 있어도 나머지는 추가한다.** "
                    + "거절된 상품은 응답의 failed 배열에 상품 ID와 사유(code, message)로 담기며, "
                    + "그 code는 단건 추가가 같은 상황에서 내보내는 값과 같다. "
                    + "전부 거절돼 added가 비어도 201이다 — 성공 여부는 failed가 비었는지로 판단한다. "
                    + "한 경매방에는 최대 100개까지 등록할 수 있고, 넘기면 앞에서부터 잘라 넣지 않고 "
                    + "요청 전체를 거절한다. 요청 배열에 같은 상품 ID가 두 번 들어오면 400이다."
                    + " added 항목의 `leaderboard`는 항상 빈 배열이다 — 방금 추가한 물품에는 입찰이 없다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "처리 성공. added와 failed가 함께 담긴다"),
            @ApiResponse(responseCode = "400", description = "items가 비었거나 100개를 넘음, "
                    + "요청 필드 형식 위반, 같은 상품 ID 중복 (code 2002)"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)"),
            @ApiResponse(responseCode = "404", description = "판매자 프로필이 없음 (code 3002), "
                    + "경매방이 없거나 본인 소유가 아님 (code 4002)"),
            @ApiResponse(responseCode = "409", description = "종료된 경매방 (code 4004), "
                    + "경매방 물품 상한 초과 (code 4009), "
                    + "동시 요청이 겹쳐 이미 올라간 상품 (code 4005)")
    })
    ResponseEntity<CommonResponse<AuctionItemBulkAddResponseDto>> addAll(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "물품을 추가할 경매방 ID", required = true)
            @PathVariable Long auctionRoomId,
            @Valid @RequestBody AuctionItemBulkAddRequestDto request);

    @Operation(
            summary = "경매방 물품 제외",
            description = "소유자가 아직 시작하지 않은(READY) 물품을 경매방에서 뺀다. 이미 시작된 물품은 뺄 수 없다. "
                    + "물품 행을 물리 삭제하므로, 빠진 상품은 다시 다른 경매방에 올릴 수 있고 상품 목록에서도 "
                    + "'미등록'으로 돌아간다. 경매방 상태는 보지 않는다 — 시작한 적 없는 물품을 빼는 건 방이 어떤 "
                    + "상태든 안전하다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "제외 성공"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)"),
            @ApiResponse(responseCode = "404", description = "판매자 프로필이 없음 (code 3002), "
                    + "경매방이 없거나 본인 소유가 아님 (code 4002), "
                    + "물품이 없거나 그 경매방 소속이 아님 (code 4001)"),
            @ApiResponse(responseCode = "409", description = "이미 시작된 물품 (code 4006)")
    })
    ResponseEntity<CommonResponse<Void>> remove(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "물품이 속한 경매방 ID", required = true)
            @PathVariable Long auctionRoomId,
            @Parameter(description = "뺄 물품 ID", required = true)
            @PathVariable Long auctionItemId);

    @Operation(
            summary = "물품 경매 시작",
            description = "소유자가 대기(READY) 중인 물품의 경매를 설정한 시간만큼 시작한다. "
                    + "마감 시각은 시작 시점에 확정되며 이후 변하지 않는다(연장은 추후 별도 기능). "
                    + "**한 경매방에서 동시에 진행할 수 있는 물품은 최대 3개**이며, 4번째 시작은 거절한다. "
                    + "종료된 경매방의 물품은 시작할 수 없고, 시작 전(BEFORE)인 경매방은 이 호출로 "
                    + "방송 중(OPEN)이 된다 — 경매방 상태는 입장을 막지 않고 구매자 화면의 표시만 가른다. "
                    + "물품이 없을 때와 본인 소유가 아닐 때를 구분하지 않고 모두 404로 응답한다. "
                    + "응답은 물품 상세와 같은 형식이며 갱신된 status와 endAt이 담긴다."
                    + " `leaderboard`는 항상 빈 배열이다 — 시작 전 물품에는 입찰이 들어올 수 없다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "시작 성공"),
            @ApiResponse(responseCode = "400", description = "경매 시간이 없거나 1~43200분(30일) 범위를 "
                    + "벗어남 (code 2002)"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)"),
            @ApiResponse(responseCode = "404", description = "판매자 프로필이 없음 (code 3002), "
                    + "물품이 없거나 본인 소유가 아님 (code 4001)"),
            @ApiResponse(responseCode = "409", description = "종료된 경매방 (code 4004), "
                    + "대기 중인 물품이 아님 (code 4007), "
                    + "이미 3개가 진행 중 (code 4008)")
    })
    ResponseEntity<CommonResponse<AuctionItemDetailResponseDto>> start(
            @Parameter(description = "시작할 물품 ID", required = true)
            @PathVariable Long auctionItemId,
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Valid @RequestBody AuctionItemStartRequestDto request);
}
