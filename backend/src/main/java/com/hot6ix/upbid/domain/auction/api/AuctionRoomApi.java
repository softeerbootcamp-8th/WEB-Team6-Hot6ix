package com.hot6ix.upbid.domain.auction.api;

import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomCreateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomUpdateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomCountsResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomListItemResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomPublicResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomResultResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomShareResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomRole;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import com.hot6ix.upbid.global.interceptor.LoginUserId;
import com.hot6ix.upbid.global.response.CommonResponse;
import com.hot6ix.upbid.global.response.CursorPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;


@Tag(name = "경매방", description = "경매방 생성·조회·수정·종료 API")
public interface AuctionRoomApi {

    @Operation(
            summary = "경매방 생성",
            description = "판매자가 경매방을 생성한다. shareCode는 서버가 발급해 응답에 담아 준다 — 공개 화면이 "
                    + "이 방을 지목하는 유일한 식별자이며, 완성된 공유 링크는 GET /{roomId}/share로 받는다. "
                    + "로그인 세션의 회원으로 생성한다. "
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
            summary = "내 경매방 목록 조회",
            description = "로그인한 사용자의 경매방을 auctionRoomId 최신순으로 조회한다. "
                    + "**내가 만든 방과 내가 참여한 방이 함께 나온다.** 방마다 role로 갈리며, "
                    + "role 파라미터로 한쪽만 볼 수 있다. 판매자로 등록하지 않은 사용자도 조회할 수 있다. "
                    + "정렬 키를 항상 불변인 auctionRoomId로 고정해 커서 페이지네이션이 안정적으로 동작하며, "
                    + "상태는 정렬이 아니라 필터로만 사용한다. "
                    + "itemCount는 그 방에 등록된 물품 수이며, participantCount는 방송 중(OPEN)인 방의 "
                    + "지금 접속 중인 수다 — 시작 전·종료된 방은 null이다. "
                    + "전체 개수는 커서 페이지네이션이라 이 응답으로 알 수 없다. 상태별 개수는 "
                    + "GET /auction-rooms/me/counts로 따로 조회한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공. 해당하는 방이 없으면 빈 배열"),
            @ApiResponse(responseCode = "400", description = "cursor가 양수가 아니거나 size가 1 미만 (code 2002)"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)")
    })
    ResponseEntity<CommonResponse<CursorPageResponse<AuctionRoomListItemResponseDto>>> getMyRooms(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "경매방 이름 검색어")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "경매방 상태 필터 — BEFORE(시작 전) / OPEN(방송 중) / CLOSED(종료)")
            @RequestParam(required = false) AuctionRoomStatus status,
            @Parameter(description = "역할 필터 — SELLER(내가 만든 방) / BUYER(참여한 남의 방)")
            @RequestParam(required = false) AuctionRoomRole role,
            @Parameter(description = "이전 페이지 마지막 경매방의 auctionRoomId, 없으면 첫 페이지")
            @RequestParam(required = false) @Positive(message = "cursor는 양수여야 합니다.") Long cursor,
            @Parameter(description = "페이지 크기, 기본값 20")
            @RequestParam(required = false) @Min(value = 1, message = "size는 1 이상이어야 합니다.") Integer size);

    @Operation(
            summary = "내 경매방 상태별 개수 조회",
            description = "목록 화면 필터 바의 탭 숫자용이다. 목록과 같은 조건에서 상태만 빼고 세므로 "
                    + "상태 탭을 바꿔도 이 값은 변하지 않는다. 역할 필터나 검색어를 바꿀 때만 다시 부른다. "
                    + "화면의 '전체'는 세 값을 더한 수다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공. 해당하는 방이 없으면 전부 0"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)")
    })
    ResponseEntity<CommonResponse<AuctionRoomCountsResponseDto>> getMyRoomCounts(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "경매방 이름 검색어")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "역할 필터 — SELLER(내가 만든 방) / BUYER(참여한 남의 방)")
            @RequestParam(required = false) AuctionRoomRole role);

    @Operation(
            summary = "경매방 낙찰 결과 조회",
            description = "경매방의 물품별 낙찰·유찰 결과를 한 번에 조회한다. 인증이 필요 없고, 로그인한 "
                    + "요청에만 물품마다 요청자의 최종 순위(myRank)와 부른 최고가(myAmount)가 함께 담긴다. "
                    + "낙찰가와 낙찰자는 낙찰(SOLD)인 물품에만 있다 — 유찰 물품의 현재가는 아무도 부르지 "
                    + "않은 시작가라 가격으로 내리지 않는다. "
                    + "방 상태로 거르지 않으므로 아직 열려 있는 방도 조회되며, 진행 중인 물품은 status로 드러난다. "
                    + "낙찰 건수·유찰 건수·총 낙찰액은 화면이 items에서 직접 세므로 응답에 없다. "
                    + "참여자 수도 없다 — 종료된 방의 참여자 수는 입찰한 사람 수인지 방송을 보던 사람 수인지 "
                    + "구분되지 않아 내리지 않는다.\n\n"
                    + "인증이 필요 없는 공개 경로라 경매방을 숫자 ID가 아닌 공유 코드로 지목한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "해당 공유 코드의 경매방이 없거나 삭제됨 (code 4002)")
    })
    ResponseEntity<CommonResponse<AuctionRoomResultResponseDto>> getResults(
            @Parameter(description = "결과를 조회할 경매방의 공유 코드", required = true)
            @PathVariable String shareCode,
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
            description = "공유 링크·QR로 들어온 사람이 방에 진입할 때 쓴다. 인증이 필요 없으며, 경매 시작 "
                    + "전(BEFORE)을 포함한 모든 상태에서 동일하게 노출한다.\n\n"
                    + "**경매방 공개 조회의 유일한 진입점이다.** 숫자 PK를 받는 공개 경로는 두지 않는다 — "
                    + "auto_increment PK를 URL에 노출하면 1, 2, 3...을 순서대로 불러 공유 링크 없이 남의 방을 "
                    + "전부 훑을 수 있다. 물품 목록·물품 상세·SSE 구독도 같은 이유로 이 코드 아래에 있다.\n\n"
                    + "isOwner만 보는 사람에 따라 달라진다 — 방 주인이 로그인한 상태로 조회했을 때만 true이며, "
                    + "화면이 판매자 조작(물품 추가·빼기·시작) UI를 띄울지 정하는 값이다. "
                    + "실제 권한은 각 조작 API가 다시 검증하므로 이 값을 권한의 근거로 쓰지 않는다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "해당 공유 코드의 경매방이 없거나 삭제됨 (code 4002)")
    })
    ResponseEntity<CommonResponse<AuctionRoomPublicResponseDto>> getRoomByShareCode(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "경매방 공유 코드", required = true)
            @PathVariable String shareCode);

    @Operation(
            summary = "경매방 설정 수정",
            description = "소유자가 경매방 설정을 부분 수정한다. 요청에서 생략된 필드는 기존 값을 유지한다. "
                    + "로그인 세션의 회원을 소유자로 확인한다.\n\n"
                    + "description과 liveUrl은 **빈 문자열을 보내면 지워진다**(null로 저장). 생략은 "
                    + "\"유지\", 빈 문자열은 \"삭제\"로 갈린다. 나머지 필드는 빈 값을 받지 않는다.\n\n"
                    + "수정 가능 범위는 요청에 담긴 필드에 따라 다르다.\n"
                    + "- **name·liveUrl만 보낸 요청**: 경매가 진행 중이어도 통과한다. 둘 다 방송을 켠 "
                    + "뒤에야 잘못이 드러나는 값이라(이름은 오타, 방송 링크는 \"안 열려요\"라는 말), "
                    + "그때 못 고치면 고칠 방법이 아예 없다.\n"
                    + "- **그 밖의 필드(coverImageUrl·description·softClose\\*)를 하나라도 보낸 요청**: "
                    + "이 방의 물품 중 하나라도 READY가 아닌 상태로 경매에 올라간 적 있으면"
                    + "(=경매가 시작된 적 있으면) 이후로도 계속 거절된다. 참여자가 이미 보고 입찰을 "
                    + "판단한 조건이라 진행 중에 바뀌면 안 된다.\n"
                    + "- **종료된 방**: 어떤 필드도 바꿀 수 없다. 참여자에게는 결과 기록이라 나중에 "
                    + "제목이 바뀌면 자기가 참여했던 방을 알아볼 수 없게 된다.\n\n"
                    + "bidIncrement는 애초에 수정 대상이 아니다 — 물품이 방의 값을 복사해 갖고 있어서 "
                    + "방 값만 바꾸면 어긋난다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "요청 필드 형식 위반 (code 2002)"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)"),
            @ApiResponse(responseCode = "404", description = "판매자 프로필이 없음 (code 3002) 또는 "
                    + "경매방이 없거나 본인 소유가 아님 (code 4002)"),
            @ApiResponse(responseCode = "409", description = "name·liveUrl 밖의 필드를 경매가 시작된 "
                    + "뒤에 바꾸려 함 (code 4003) 또는 종료된 경매방 (code 4004)")
    })
    ResponseEntity<CommonResponse<AuctionRoomPublicResponseDto>> update(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "수정할 경매방 ID", required = true)
            @PathVariable Long roomId,
            @Valid @RequestBody AuctionRoomUpdateRequestDto request);

    @Operation(
            summary = "경매방 종료",
            description = "소유자가 방송을 끝내고 경매방을 종료한다. 요청 본문은 없다. "
                    + "진행 중이던 물품은 모두 그 자리에서 마감되어 입찰이 있으면 낙찰(SOLD), "
                    + "없으면 유찰(FAILED)로 확정된다. **아직 시작하지 않은 READY 물품은 그대로 남는다** "
                    + "— 시작한 적 없는 물품을 유찰로 적으면 결과 집계에서 실제 유찰과 섞이기 때문이다. "
                    + "물품을 하나도 시작하지 않은 방(BEFORE)도 종료할 수 있다. "
                    + "종료된 방에서는 물품 추가·시작이 모두 막히며, **되돌리는 API는 없다.** "
                    + "응답의 closedAt이 종료 시각이고, 종료를 참여자에게 알리는 ROOM_CLOSED 이벤트가 "
                    + "SSE로 함께 나간다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "종료 성공"),
            @ApiResponse(responseCode = "400", description = "경로 변수가 숫자가 아니라면 code 2002"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)"),
            @ApiResponse(responseCode = "404", description = "판매자 프로필이 없음 (code 3002) 또는 "
                    + "경매방이 없거나 본인 소유가 아님 (code 4002)"),
            @ApiResponse(responseCode = "409", description = "이미 종료된 경매방 (code 4004)")
    })
    ResponseEntity<CommonResponse<AuctionRoomPublicResponseDto>> close(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "종료할 경매방 ID", required = true)
            @PathVariable Long roomId);

    @Operation(
            summary = "경매방 입장 약관 동의",
            description = "경매방에 입장하기 전 이용 약관에 동의한다. 동의 시각과 약관 버전을 기록한다. "
                    + "이미 동의한 적 있으면 동의 시각과 버전을 갱신한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "동의 완료"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요함 (code 1005)"),
            @ApiResponse(responseCode = "404", description = "해당 공유 코드의 경매방이 없거나 삭제됨 (code 4002)")
    })
    ResponseEntity<CommonResponse<Void>> agree(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "동의할 경매방의 공유 코드", required = true)
            @PathVariable String shareCode);
}
