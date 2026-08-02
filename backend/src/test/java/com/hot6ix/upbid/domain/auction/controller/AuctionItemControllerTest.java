package com.hot6ix.upbid.domain.auction.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemDetailResponseDto;
import com.hot6ix.upbid.domain.auction.dto.response.AuctionItemSummaryResponseDto;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.service.AuctionItemService;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.exception.GlobalExceptionHandler;
import com.hot6ix.upbid.global.support.AbstractControllerTest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = AuctionItemController.class)
@Import(GlobalExceptionHandler.class)
class AuctionItemControllerTest extends AbstractControllerTest {

    @MockitoBean
    private AuctionItemService auctionItemService;

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
}
