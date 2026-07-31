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
import com.hot6ix.upbid.domain.auction.dto.response.AuctionRoomPublicResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionRoomStatus;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.service.AuctionRoomService;
import com.hot6ix.upbid.domain.user.exception.SellerProfileErrorType;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = AuctionRoomController.class)
@Import(GlobalExceptionHandler.class)
class AuctionRoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuctionRoomService auctionRoomService;

    private AuctionRoomCreateRequestDto newCreateRequest() {
        return AuctionRoomCreateRequestDto.builder()
                .name("승민의 경매방")
                .coverImageUrl("https://cdn.hot6ix.com/cover.png")
                .description("한정판 피규어 경매")
                .liveUrl("https://instagram.com/hot6ix")
                .softCloseTriggerSeconds(30)
                .softCloseExtendSeconds(60)
                .build();
    }

    private AuctionRoomPublicResponseDto sampleResponse() {
        return AuctionRoomPublicResponseDto.builder()
                .auctionRoomId(1L)
                .name("승민의 경매방")
                .status(AuctionRoomStatus.BEFORE)
                .softCloseTriggerSeconds(30)
                .softCloseExtendSeconds(60)
                .sellerStoreName("승민상점")
                .build();
    }

    @Test
    @DisplayName("경매방을 생성하면 201과 생성된 정보를 반환한다")
    void create() throws Exception {

        when(auctionRoomService.create(eq(1L), any(AuctionRoomCreateRequestDto.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/auction-rooms")
                        .header("X-User-Id", "1")
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
                .name("")
                .softCloseTriggerSeconds(30)
                .softCloseExtendSeconds(60)
                .build();

        mockMvc.perform(post("/api/v1/auction-rooms")
                        .header("X-User-Id", "1")
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
                .name("<script>")
                .softCloseTriggerSeconds(30)
                .softCloseExtendSeconds(60)
                .build();

        mockMvc.perform(post("/api/v1/auction-rooms")
                        .header("X-User-Id", "1")
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
                .name("승민의 경매방")
                .coverImageUrl("not-a-url")
                .softCloseTriggerSeconds(30)
                .softCloseExtendSeconds(60)
                .build();

        mockMvc.perform(post("/api/v1/auction-rooms")
                        .header("X-User-Id", "1")
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
                .name("승민의 경매방")
                .build();

        mockMvc.perform(post("/api/v1/auction-rooms")
                        .header("X-User-Id", "1")
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
                .name("승민의 경매방")
                .softCloseTriggerSeconds(30)
                .softCloseExtendSeconds(3601)
                .build();

        mockMvc.perform(post("/api/v1/auction-rooms")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.errors[0].field").value("softCloseExtendSeconds"));
    }

    @Test
    @DisplayName("판매자 프로필이 없으면 생성 시 404를 반환한다")
    void create_sellerProfileNotFound() throws Exception {

        when(auctionRoomService.create(eq(1L), any(AuctionRoomCreateRequestDto.class)))
                .thenThrow(new ApplicationException(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND));

        mockMvc.perform(post("/api/v1/auction-rooms")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newCreateRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(3002));
    }

    @Test
    @DisplayName("경매방 정보를 조회하면 200과 공개 정보를 반환한다")
    void getRoom() throws Exception {

        when(auctionRoomService.getRoom(1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/auction-rooms/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("승민의 경매방"))
                .andExpect(jsonPath("$.data.status").value("BEFORE"));
    }

    @Test
    @DisplayName("존재하지 않는 경매방을 조회하면 404를 반환한다")
    void getRoom_notFound() throws Exception {

        when(auctionRoomService.getRoom(999L))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));

        mockMvc.perform(get("/api/v1/auction-rooms/999"))
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
                        .header("X-User-Id", "1")
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
                        .header("X-User-Id", "1")
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
                        .header("X-User-Id", "1")
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
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4003));
    }
}
