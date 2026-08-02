package com.hot6ix.upbid.domain.auction.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hot6ix.upbid.domain.auction.dto.request.AuctionItemAddRequestDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemDetailResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemSummaryResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.service.AuctionItemService;
import com.hot6ix.upbid.domain.product.exception.ProductErrorType;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.exception.GlobalExceptionHandler;
import com.hot6ix.upbid.global.support.AbstractControllerTest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = AuctionItemController.class)
@Import(GlobalExceptionHandler.class)
class AuctionItemControllerTest extends AbstractControllerTest {

    @MockitoBean
    private AuctionItemService auctionItemService;

    private AuctionItemAddRequestDto newAddRequest() {
        return AuctionItemAddRequestDto.builder()
                .productId(20L)
                .startingPrice(50_000L)
                .build();
    }

    private AuctionItemSummaryResponseDto sampleSummary() {
        return new AuctionItemSummaryResponseDto(
                1L,
                "한정판 피규어",
                "https://cdn.hot6ix.com/item.png",
                50_000L,
                AuctionItemStatus.IN_PROGRESS,
                LocalDateTime.of(2026, 7, 29, 21, 0));
    }

    private AuctionItemDetailResponseDto sampleDetail() {
        return new AuctionItemDetailResponseDto(
                1L,
                10L,
                "한정판 피규어",
                "미개봉 정품",
                "https://cdn.hot6ix.com/item.png",
                "https://instagram.com/hot6ix",
                10_000L,
                50_000L,
                1_000L,
                AuctionItemStatus.IN_PROGRESS,
                LocalDateTime.of(2026, 7, 29, 21, 0));
    }

    @Test
    @DisplayName("경매방 물품 목록을 조회하면 200과 물품 배열을 반환한다")
    void getSummaries() throws Exception {

        when(auctionItemService.getSummaries(10L)).thenReturn(List.of(sampleSummary()));

        mockMvc.perform(get("/api/v1/auction-rooms/10/auction-items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("경매 물품 목록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].auctionItemId").value(1))
                .andExpect(jsonPath("$.data[0].productName").value("한정판 피규어"))
                .andExpect(jsonPath("$.data[0].status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("물품이 없는 경매방은 200과 빈 배열을 반환한다")
    void getSummariesReturnsEmptyArray() throws Exception {

        when(auctionItemService.getSummaries(10L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/auction-rooms/10/auction-items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("없는 경매방의 물품 목록을 조회하면 404와 4002를 반환한다")
    void getSummariesRoomNotFound() throws Exception {

        when(auctionItemService.getSummaries(999L))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));

        mockMvc.perform(get("/api/v1/auction-rooms/999/auction-items"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4002))
                .andExpect(jsonPath("$.message").value("존재하지 않는 경매방입니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("물품 상세를 조회하면 200과 물품 정보를 반환한다")
    void getDetail() throws Exception {

        when(auctionItemService.getDetail(1L)).thenReturn(sampleDetail());

        mockMvc.perform(get("/api/v1/auction-items/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("경매 물품 상세 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.auctionItemId").value(1))
                .andExpect(jsonPath("$.data.auctionRoomId").value(10))
                .andExpect(jsonPath("$.data.startingPrice").value(10000))
                .andExpect(jsonPath("$.data.currentPrice").value(50000))
                .andExpect(jsonPath("$.data.bidIncrement").value(1000));
    }

    @Test
    @DisplayName("비로그인 사용자도 경매방 물품 목록을 조회할 수 있다")
    void getSummariesAllowsGuest() throws Exception {

        비로그인_상태로_바꾼다();
        when(auctionItemService.getSummaries(10L)).thenReturn(List.of(sampleSummary()));

        mockMvc.perform(get("/api/v1/auction-rooms/10/auction-items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("비로그인 사용자도 물품 상세를 조회할 수 있다")
    void getDetailAllowsGuest() throws Exception {

        비로그인_상태로_바꾼다();
        when(auctionItemService.getDetail(1L)).thenReturn(sampleDetail());

        mockMvc.perform(get("/api/v1/auction-items/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("없는 물품을 상세 조회하면 404와 4001을 반환한다")
    void getDetailNotFound() throws Exception {

        when(auctionItemService.getDetail(999L))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND));

        mockMvc.perform(get("/api/v1/auction-items/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4001))
                .andExpect(jsonPath("$.message").value("존재하지 않는 경매 물품입니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("물품을 추가하면 201과 추가된 물품을 반환한다")
    void add() throws Exception {

        when(auctionItemService.add(eq(LOGIN_USER_ID), eq(10L), any(AuctionItemAddRequestDto.class)))
                .thenReturn(sampleDetail());

        mockMvc.perform(post("/api/v1/auction-rooms/10/auction-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newAddRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("경매방에 물품이 추가되었습니다."))
                .andExpect(jsonPath("$.data.auctionItemId").value(1))
                .andExpect(jsonPath("$.data.bidIncrement").value(1000));
    }

    @Test
    @DisplayName("상품 ID가 없으면 추가 시 400을 반환한다")
    void addMissingProductId() throws Exception {

        AuctionItemAddRequestDto request = AuctionItemAddRequestDto.builder()
                .startingPrice(50_000L)
                .build();

        mockMvc.perform(post("/api/v1/auction-rooms/10/auction-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.errors[0].field").value("productId"));
    }

    @Test
    @DisplayName("시작가가 0이면 추가 시 400을 반환한다")
    void addZeroStartingPrice() throws Exception {

        AuctionItemAddRequestDto request = AuctionItemAddRequestDto.builder()
                .productId(20L)
                .startingPrice(0L)
                .build();

        mockMvc.perform(post("/api/v1/auction-rooms/10/auction-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.errors[0].field").value("startingPrice"));
    }

    @Test
    @DisplayName("시작가가 1조를 넘으면 추가 시 400을 반환한다")
    void addStartingPriceOutOfRange() throws Exception {

        AuctionItemAddRequestDto request = AuctionItemAddRequestDto.builder()
                .productId(20L)
                .startingPrice(1_000_000_000_001L)
                .build();

        mockMvc.perform(post("/api/v1/auction-rooms/10/auction-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.errors[0].field").value("startingPrice"));
    }

    @Test
    @DisplayName("종료된 경매방에 추가하면 409와 4004를 반환한다")
    void addRoomClosed() throws Exception {

        when(auctionItemService.add(eq(LOGIN_USER_ID), eq(10L), any(AuctionItemAddRequestDto.class)))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ROOM_CLOSED));

        mockMvc.perform(post("/api/v1/auction-rooms/10/auction-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newAddRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4004));
    }

    @Test
    @DisplayName("이미 경매방에 올라간 상품을 추가하면 409와 4005를 반환한다")
    void addProductAlreadyInAuction() throws Exception {

        when(auctionItemService.add(eq(LOGIN_USER_ID), eq(10L), any(AuctionItemAddRequestDto.class)))
                .thenThrow(new ApplicationException(AuctionErrorType.PRODUCT_ALREADY_IN_AUCTION));

        mockMvc.perform(post("/api/v1/auction-rooms/10/auction-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newAddRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4005));
    }

    @Test
    @DisplayName("없거나 남의 경매방에 물품을 추가하면 404와 4002를 반환한다")
    void addRoomNotFound() throws Exception {

        when(auctionItemService.add(eq(LOGIN_USER_ID), eq(999L), any(AuctionItemAddRequestDto.class)))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));

        mockMvc.perform(post("/api/v1/auction-rooms/999/auction-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newAddRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4002));
    }

    @Test
    @DisplayName("남의 상품을 추가하면 404와 5001을 반환한다")
    void addProductNotFound() throws Exception {

        when(auctionItemService.add(eq(LOGIN_USER_ID), eq(10L), any(AuctionItemAddRequestDto.class)))
                .thenThrow(new ApplicationException(ProductErrorType.PRODUCT_NOT_FOUND));

        mockMvc.perform(post("/api/v1/auction-rooms/10/auction-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newAddRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(5001));
    }

    @Test
    @DisplayName("비로그인 사용자는 물품을 추가할 수 없다")
    void addRejectsGuest() throws Exception {

        비로그인_상태로_바꾼다();

        mockMvc.perform(post("/api/v1/auction-rooms/10/auction-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newAddRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(1005));
    }

    @Test
    @DisplayName("물품을 빼면 200을 반환한다")
    void remove() throws Exception {

        mockMvc.perform(delete("/api/v1/auction-rooms/10/auction-items/30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("경매방에서 물품이 제외되었습니다."));
    }

    @Test
    @DisplayName("이미 시작된 물품을 빼면 409와 4006을 반환한다")
    void removeAlreadyStarted() throws Exception {

        doThrow(new ApplicationException(AuctionErrorType.AUCTION_ITEM_ALREADY_STARTED))
                .when(auctionItemService).remove(LOGIN_USER_ID, 10L, 30L);

        mockMvc.perform(delete("/api/v1/auction-rooms/10/auction-items/30"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4006));
    }

    @Test
    @DisplayName("없거나 남의 경매방에서 물품을 빼면 404와 4002를 반환한다")
    void removeRoomNotFound() throws Exception {

        doThrow(new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND))
                .when(auctionItemService).remove(LOGIN_USER_ID, 999L, 30L);

        mockMvc.perform(delete("/api/v1/auction-rooms/999/auction-items/30"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4002));
    }

    @Test
    @DisplayName("없는 물품을 빼면 404와 4001을 반환한다")
    void removeItemNotFound() throws Exception {

        doThrow(new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND))
                .when(auctionItemService).remove(LOGIN_USER_ID, 10L, 999L);

        mockMvc.perform(delete("/api/v1/auction-rooms/10/auction-items/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    @DisplayName("비로그인 사용자는 물품을 뺄 수 없다")
    void removeRejectsGuest() throws Exception {

        비로그인_상태로_바꾼다();

        mockMvc.perform(delete("/api/v1/auction-rooms/10/auction-items/30"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(1005));
    }
}
