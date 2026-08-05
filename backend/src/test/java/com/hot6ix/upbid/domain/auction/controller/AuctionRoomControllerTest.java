package com.hot6ix.upbid.domain.auction.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomCreateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.request.AuctionRoomUpdateRequestDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemResultResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomCountsResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomListItemResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomPublicResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomResultResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomShareResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomRole;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.service.AuctionRoomCloseService;
import com.hot6ix.upbid.domain.auction.service.AuctionRoomService;
import com.hot6ix.upbid.domain.auction.service.AuctionRoomShareService;
import com.hot6ix.upbid.domain.user.exception.SellerProfileErrorType;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.exception.GlobalExceptionHandler;
import com.hot6ix.upbid.global.response.CursorPageResponse;
import com.hot6ix.upbid.global.support.AbstractControllerTest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = AuctionRoomController.class)
@Import(GlobalExceptionHandler.class)
class AuctionRoomControllerTest extends AbstractControllerTest {

    @MockitoBean
    private AuctionRoomService auctionRoomService;

    @MockitoBean
    private AuctionRoomShareService auctionRoomShareService;

    @MockitoBean
    private AuctionRoomCloseService auctionRoomCloseService;

    private AuctionRoomCreateRequestDto newCreateRequest() {
        return AuctionRoomCreateRequestDto.builder()
                .bidIncrement(1_000L)
                .name("승민의 경매방")
                .coverImageUrl("https://cdn.hot6ix.com/cover.png")
                .description("한정판 피규어 경매")
                .liveUrl("https://instagram.com/hot6ix")
                .softCloseTriggerSeconds(60)
                .softCloseExtendSeconds(60)
                .build();
    }

    private AuctionRoomPublicResponseDto sampleResponse() {
        return AuctionRoomPublicResponseDto.builder()
                .auctionRoomId(1L)
                .name("승민의 경매방")
                .status(AuctionRoomStatus.BEFORE)
                .softCloseTriggerSeconds(60)
                .softCloseExtendSeconds(60)
                .sellerStoreName("승민상점")
                .isOwner(true)
                .build();
    }

    @Test
    @DisplayName("경매방을 생성하면 201과 생성된 정보를 반환한다")
    void create() throws Exception {

        when(auctionRoomService.create(eq(1L), any(AuctionRoomCreateRequestDto.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/auction-rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newCreateRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("승민의 경매방"))
                .andExpect(jsonPath("$.data.status").value("BEFORE"))
                .andExpect(jsonPath("$.data.sellerStoreName").value("승민상점"));
    }

    @Test
    @DisplayName("경매방 이름이 없으면 생성 시 400을 반환한다")
    void create_blankName() throws Exception {

        AuctionRoomCreateRequestDto request = AuctionRoomCreateRequestDto.builder()
                .bidIncrement(1_000L)
                .name("")
                .softCloseTriggerSeconds(60)
                .softCloseExtendSeconds(60)
                .build();

        mockMvc.perform(post("/api/v1/auction-rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    @DisplayName("경매방 이름에 금지 문자가 포함되면 생성 시 400을 반환한다")
    void create_invalidName() throws Exception {

        AuctionRoomCreateRequestDto request = AuctionRoomCreateRequestDto.builder()
                .bidIncrement(1_000L)
                .name("<script>")
                .softCloseTriggerSeconds(60)
                .softCloseExtendSeconds(60)
                .build();

        mockMvc.perform(post("/api/v1/auction-rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    @DisplayName("커버 이미지 URL 형식이 올바르지 않으면 생성 시 400을 반환한다")
    void create_invalidCoverImageUrl() throws Exception {

        AuctionRoomCreateRequestDto request = AuctionRoomCreateRequestDto.builder()
                .bidIncrement(1_000L)
                .name("승민의 경매방")
                .coverImageUrl("not-a-url")
                .softCloseTriggerSeconds(60)
                .softCloseExtendSeconds(60)
                .build();

        mockMvc.perform(post("/api/v1/auction-rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002));
    }

    @Test
    @DisplayName("Soft Close 필드가 없으면 생성 시 400을 반환한다")
    void create_missingSoftCloseFields() throws Exception {

        AuctionRoomCreateRequestDto request = AuctionRoomCreateRequestDto.builder()
                .bidIncrement(1_000L)
                .name("승민의 경매방")
                .build();

        mockMvc.perform(post("/api/v1/auction-rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002));
    }

    @Test
    @DisplayName("Soft Close 연장 초가 3600을 넘으면 생성 시 400을 반환한다")
    void create_softCloseExtendSecondsOutOfRange() throws Exception {

        AuctionRoomCreateRequestDto request = AuctionRoomCreateRequestDto.builder()
                .bidIncrement(1_000L)
                .name("승민의 경매방")
                .softCloseTriggerSeconds(60)
                .softCloseExtendSeconds(3601)
                .build();

        mockMvc.perform(post("/api/v1/auction-rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.errors[0].field").value("softCloseExtendSeconds"));
    }

    @Test
    @DisplayName("Soft Close 트리거 초가 60초 미만이면 생성 시 400을 반환한다")
    void create_softCloseTriggerSecondsBelowMinimum() throws Exception {

        AuctionRoomCreateRequestDto request = AuctionRoomCreateRequestDto.builder()
                .bidIncrement(1_000L)
                .name("승민의 경매방")
                .softCloseTriggerSeconds(59)
                .softCloseExtendSeconds(60)
                .build();

        // 임박 구간이 좁으면 그 안에 드느냐가 회선 상태로 갈려 연장이 사실상 안 걸린다.
        mockMvc.perform(post("/api/v1/auction-rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.errors[0].field").value("softCloseTriggerSeconds"));
    }

    @Test
    @DisplayName("Soft Close 연장 초가 60초 미만이면 생성 시 400을 반환한다")
    void create_softCloseExtendSecondsBelowMinimum() throws Exception {

        AuctionRoomCreateRequestDto request = AuctionRoomCreateRequestDto.builder()
                .bidIncrement(1_000L)
                .name("승민의 경매방")
                .softCloseTriggerSeconds(60)
                .softCloseExtendSeconds(59)
                .build();

        // 연장 폭이 네트워크 지연 편차만 해지면 누가 이기는지가 회선 상태로 갈린다.
        mockMvc.perform(post("/api/v1/auction-rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.errors[0].field").value("softCloseExtendSeconds"));
    }

    @Test
    @DisplayName("입찰 단위가 1조를 넘으면 생성 시 400을 반환한다")
    void create_bidIncrementOutOfRange() throws Exception {

        AuctionRoomCreateRequestDto request = AuctionRoomCreateRequestDto.builder()
                .bidIncrement(1_000_000_000_001L)
                .name("승민의 경매방")
                .softCloseTriggerSeconds(60)
                .softCloseExtendSeconds(60)
                .build();

        mockMvc.perform(post("/api/v1/auction-rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.errors[0].field").value("bidIncrement"));
    }

    @Test
    @DisplayName("입찰 단위가 없으면 생성 시 400을 반환한다")
    void create_missingBidIncrement() throws Exception {

        AuctionRoomCreateRequestDto request = AuctionRoomCreateRequestDto.builder()
                .name("승민의 경매방")
                .softCloseTriggerSeconds(60)
                .softCloseExtendSeconds(60)
                .build();

        mockMvc.perform(post("/api/v1/auction-rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.errors[0].field").value("bidIncrement"));
    }

    @Test
    @DisplayName("입찰 단위가 0이면 생성 시 400을 반환한다")
    void create_bidIncrementZero() throws Exception {

        AuctionRoomCreateRequestDto request = AuctionRoomCreateRequestDto.builder()
                .bidIncrement(0L)
                .name("승민의 경매방")
                .softCloseTriggerSeconds(60)
                .softCloseExtendSeconds(60)
                .build();

        mockMvc.perform(post("/api/v1/auction-rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.errors[0].field").value("bidIncrement"));
    }

    @Test
    @DisplayName("판매자 프로필이 없으면 생성 시 404를 반환한다")
    void create_sellerProfileNotFound() throws Exception {

        when(auctionRoomService.create(eq(1L), any(AuctionRoomCreateRequestDto.class)))
                .thenThrow(new ApplicationException(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND));

        mockMvc.perform(post("/api/v1/auction-rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newCreateRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(3002));
    }

    @Test
    @DisplayName("경매방 정보를 조회하면 200과 공개 정보를 반환한다")
    void getRoom() throws Exception {

        when(auctionRoomService.getRoom(1L, 1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/auction-rooms/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("승민의 경매방"))
                .andExpect(jsonPath("$.data.status").value("BEFORE"))
                // 화면이 판매자 조작 UI를 이 이름으로 읽는다. owner로 줄어들면 안 된다.
                .andExpect(jsonPath("$.data.isOwner").value(true));
    }

    @Test
    @DisplayName("비로그인 사용자도 경매방 정보를 조회할 수 있다")
    void getRoom_allowsGuest() throws Exception {

        비로그인_상태로_바꾼다();
        // 게스트는 회원 ID가 null로 넘어간다. 서비스가 그 값으로 isOwner를 false로 판정한다.
        when(auctionRoomService.getRoom(1L, null)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/auction-rooms/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("존재하지 않는 경매방을 조회하면 404를 반환한다")
    void getRoom_notFound() throws Exception {

        when(auctionRoomService.getRoom(999L, 1L))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));

        mockMvc.perform(get("/api/v1/auction-rooms/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4002));
    }

    private AuctionRoomResultResponseDto sampleResultResponse() {
        return new AuctionRoomResultResponseDto(
                1L, "승민의 경매방", "승민상점", AuctionRoomStatus.CLOSED, null,
                List.of(new AuctionItemResultResponseDto(
                        101L, "한정판 피규어", null, AuctionItemStatus.SOLD,
                        85_000L, "스니커홀릭", 7, 60_000L)));
    }

    @Test
    @DisplayName("경매방 낙찰 결과를 조회하면 200과 물품별 결과를 반환한다")
    void getResults() throws Exception {

        when(auctionRoomService.getResults(1L, 1L)).thenReturn(sampleResultResponse());

        mockMvc.perform(get("/api/v1/auction-rooms/1/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("승민의 경매방"))
                .andExpect(jsonPath("$.data.items[0].finalPrice").value(85_000))
                .andExpect(jsonPath("$.data.items[0].winnerNickname").value("스니커홀릭"))
                .andExpect(jsonPath("$.data.items[0].myRank").value(7));
    }

    /** 비로그인이면 인터셉터가 userId를 담지 않으므로 서비스에 null이 넘어간다. */
    @Test
    @DisplayName("비로그인 사용자도 낙찰 결과를 조회할 수 있다")
    void getResults_allowsGuest() throws Exception {

        비로그인_상태로_바꾼다();
        when(auctionRoomService.getResults(1L, null)).thenReturn(sampleResultResponse());

        mockMvc.perform(get("/api/v1/auction-rooms/1/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("존재하지 않는 경매방의 결과를 조회하면 404를 반환한다")
    void getResults_notFound() throws Exception {

        when(auctionRoomService.getResults(999L, 1L))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));

        mockMvc.perform(get("/api/v1/auction-rooms/999/results"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4002));
    }

    @Test
    @DisplayName("소유자가 공유 링크를 조회하면 200과 shareUrl을 반환한다")
    void getShareInfo() throws Exception {

        when(auctionRoomShareService.getShareInfo(1L, 1L))
                .thenReturn(new AuctionRoomShareResponseDto("https://upbid.com/join/aBcD1234aBcD1234"));

        mockMvc.perform(get("/api/v1/auction-rooms/1/share"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shareUrl").value("https://upbid.com/join/aBcD1234aBcD1234"));
    }

    @Test
    @DisplayName("경매방이 없거나 본인 소유가 아니면 공유 링크 조회 시 404를 반환한다")
    void getShareInfo_roomNotFound() throws Exception {

        when(auctionRoomShareService.getShareInfo(1L, 999L))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));

        mockMvc.perform(get("/api/v1/auction-rooms/999/share"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4002));
    }

    @Test
    @DisplayName("공유 코드로 경매방을 조회하면 200과 공개 정보를 반환한다")
    void getRoomByShareCode() throws Exception {

        when(auctionRoomService.getRoomByShareCode("aBcD1234aBcD1234", 1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/auction-rooms/share/aBcD1234aBcD1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("승민의 경매방"));
    }

    @Test
    @DisplayName("비로그인 사용자도 공유 코드로 경매방을 조회할 수 있다")
    void getRoomByShareCode_allowsGuest() throws Exception {

        비로그인_상태로_바꾼다();
        when(auctionRoomService.getRoomByShareCode("aBcD1234aBcD1234", null)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/auction-rooms/share/aBcD1234aBcD1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("존재하지 않는 공유 코드로 조회하면 404를 반환한다")
    void getRoomByShareCode_notFound() throws Exception {

        when(auctionRoomService.getRoomByShareCode("unknownShareCode", 1L))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));

        mockMvc.perform(get("/api/v1/auction-rooms/share/unknownShareCode"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4002));
    }

    @Test
    @DisplayName("경매방을 수정하면 200과 수정된 정보를 반환한다")
    void update() throws Exception {

        AuctionRoomUpdateRequestDto request = AuctionRoomUpdateRequestDto.builder()
                .name("새로운 경매방 이름")
                .build();

        when(auctionRoomService.update(eq(1L), eq(1L), any(AuctionRoomUpdateRequestDto.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(patch("/api/v1/auction-rooms/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("승민의 경매방"));
    }

    @Test
    @DisplayName("경매방 이름에 금지 문자가 포함되면 수정 시 400을 반환한다")
    void update_invalidName() throws Exception {

        AuctionRoomUpdateRequestDto request = AuctionRoomUpdateRequestDto.builder()
                .name("<script>")
                .build();

        mockMvc.perform(patch("/api/v1/auction-rooms/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    @DisplayName("경매방이 없거나 본인 소유가 아니면 수정 시 404를 반환한다")
    void update_roomNotFound() throws Exception {

        AuctionRoomUpdateRequestDto request = AuctionRoomUpdateRequestDto.builder()
                .name("새로운 경매방 이름")
                .build();

        when(auctionRoomService.update(eq(1L), eq(999L), any(AuctionRoomUpdateRequestDto.class)))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));

        mockMvc.perform(patch("/api/v1/auction-rooms/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4002));
    }

    @Test
    @DisplayName("경매가 시작된 것으로 간주되면 수정 시 409를 반환한다")
    void update_alreadyStarted() throws Exception {

        AuctionRoomUpdateRequestDto request = AuctionRoomUpdateRequestDto.builder()
                .name("새로운 경매방 이름")
                .build();

        when(auctionRoomService.update(eq(1L), eq(1L), any(AuctionRoomUpdateRequestDto.class)))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ROOM_ALREADY_STARTED));

        mockMvc.perform(patch("/api/v1/auction-rooms/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4003));
    }

    @Test
    @DisplayName("내 경매방 목록을 조회하면 200과 커서 페이지를 반환한다")
    void getMyRooms() throws Exception {

        AuctionRoomListItemResponseDto item = AuctionRoomListItemResponseDto.builder()
                .auctionRoomId(10L)
                .name("승민의 경매방")
                .status(AuctionRoomStatus.OPEN)
                .role(AuctionRoomRole.BUYER)
                .storeName("승민상점")
                .createdAt(LocalDateTime.of(2026, 8, 3, 12, 0))
                .itemCount(2L)
                .participantCount(12L)
                .build();

        when(auctionRoomService.getMyRooms(1L, "승민", AuctionRoomStatus.OPEN, null, 20L, 2))
                .thenReturn(CursorPageResponse.of(List.of(item), 10L));

        mockMvc.perform(get("/api/v1/auction-rooms/me")
                        .param("keyword", "승민")
                        .param("status", "OPEN")
                        .param("cursor", "20")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("내 경매방 목록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.content[0].auctionRoomId").value(10))
                .andExpect(jsonPath("$.data.content[0].itemCount").value(2))
                .andExpect(jsonPath("$.data.content[0].role").value("BUYER"))
                .andExpect(jsonPath("$.data.content[0].storeName").value("승민상점"))
                .andExpect(jsonPath("$.data.content[0].participantCount").value(12))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.nextCursor").value(10));
    }

    @Test
    @DisplayName("role을 주면 그대로 서비스로 넘어간다")
    void getMyRooms_role() throws Exception {

        when(auctionRoomService.getMyRooms(1L, null, null, AuctionRoomRole.SELLER, null, null))
                .thenReturn(CursorPageResponse.of(List.of(), null));

        mockMvc.perform(get("/api/v1/auction-rooms/me").param("role", "SELLER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());
    }

    @Test
    @DisplayName("상태별 개수를 조회하면 200과 세 값을 반환한다")
    void getMyRoomCounts() throws Exception {

        when(auctionRoomService.getMyRoomCounts(1L, null, null))
                .thenReturn(new AuctionRoomCountsResponseDto(1L, 2L, 3L));

        mockMvc.perform(get("/api/v1/auction-rooms/me/counts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("내 경매방 상태별 개수 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.before").value(1))
                .andExpect(jsonPath("$.data.open").value(2))
                .andExpect(jsonPath("$.data.closed").value(3));
    }

    @Test
    @DisplayName("만든 경매방이 없으면 200과 빈 배열을 반환한다")
    void getMyRooms_empty() throws Exception {

        when(auctionRoomService.getMyRooms(1L, null, null, null, null, null))
                .thenReturn(CursorPageResponse.of(List.of(), null));

        mockMvc.perform(get("/api/v1/auction-rooms/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("cursor가 양수가 아니면 목록 조회 시 400을 반환한다")
    void getMyRooms_invalidCursor() throws Exception {

        mockMvc.perform(get("/api/v1/auction-rooms/me").param("cursor", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2002));
    }

    @Test
    @DisplayName("경매방을 종료하면 200과 종료된 방 정보를 반환한다")
    void closeRoom() throws Exception {

        when(auctionRoomCloseService.close(1L, 1L)).thenReturn(closedResponse());

        mockMvc.perform(post("/api/v1/auction-rooms/1/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.closedAt").exists());
    }

    @Test
    @DisplayName("경매방이 없거나 본인 소유가 아니면 종료 시 404를 반환한다")
    void closeRoom_roomNotFound() throws Exception {

        when(auctionRoomCloseService.close(1L, 999L))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));

        mockMvc.perform(post("/api/v1/auction-rooms/999/close"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4002));
    }

    @Test
    @DisplayName("이미 종료된 경매방을 다시 종료하면 409를 반환한다")
    void closeRoom_alreadyClosed() throws Exception {

        when(auctionRoomCloseService.close(1L, 1L))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ROOM_CLOSED));

        mockMvc.perform(post("/api/v1/auction-rooms/1/close"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4004));
    }

    @Test
    @DisplayName("판매자 프로필이 없으면 종료 시 404와 3002를 반환한다")
    void closeRoom_sellerProfileNotFound() throws Exception {

        when(auctionRoomCloseService.close(1L, 1L))
                .thenThrow(new ApplicationException(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND));

        mockMvc.perform(post("/api/v1/auction-rooms/1/close"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(3002));
    }

    private AuctionRoomPublicResponseDto closedResponse() {
        return AuctionRoomPublicResponseDto.builder()
                .auctionRoomId(1L)
                .name("승민의 경매방")
                .status(AuctionRoomStatus.CLOSED)
                .sellerStoreName("승민상점")
                .closedAt(LocalDateTime.of(2026, 8, 4, 21, 30))
                .build();
    }
}
