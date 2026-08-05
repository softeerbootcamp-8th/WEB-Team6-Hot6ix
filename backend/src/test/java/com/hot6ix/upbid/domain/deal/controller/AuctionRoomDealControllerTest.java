package com.hot6ix.upbid.domain.deal.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.deal.dto.response.AuctionItemDealStatusResponseDto;
import com.hot6ix.upbid.domain.deal.dto.response.AuctionRoomDealStatusResponseDto;
import com.hot6ix.upbid.domain.deal.entity.DealItemStatus;
import com.hot6ix.upbid.domain.deal.service.DealService;
import com.hot6ix.upbid.domain.user.exception.SellerProfileErrorType;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.exception.GlobalExceptionHandler;
import com.hot6ix.upbid.global.support.AbstractControllerTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = AuctionRoomDealController.class)
@Import(GlobalExceptionHandler.class)
class AuctionRoomDealControllerTest extends AbstractControllerTest {

    private static final String URL = "/api/v1/auction-rooms/1/deals";

    @MockitoBean
    private DealService dealService;

    @Test
    @DisplayName("거래 현황을 조회하면 200과 물품별 현황을 반환하고 세션 회원으로 조회한다")
    void getRoomDeals() throws Exception {

        AuctionRoomDealStatusResponseDto response = new AuctionRoomDealStatusResponseDto(
                1L, "승민상점 경매방",
                List.of(new AuctionItemDealStatusResponseDto(
                        2L, "포토카드", DealItemStatus.IN_PROGRESS,
                        12_000L, 55L, "원기", 3, 1)));
        when(dealService.getRoomDeals(1L, LOGIN_USER_ID)).thenReturn(response);

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("경매방 거래 현황 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.name").value("승민상점 경매방"))
                .andExpect(jsonPath("$.data.items[0].dealStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.items[0].dealCandidateId").value(55))
                .andExpect(jsonPath("$.data.items[0].amount").value(12_000))
                .andExpect(jsonPath("$.data.items[0].failedCandidateCount").value(1));
    }

    @Test
    @DisplayName("전원 실패한 물품은 ALL_FAILED로 내려가고 거래 상대가 비어 있다")
    void getRoomDeals_allFailed() throws Exception {

        AuctionRoomDealStatusResponseDto response = new AuctionRoomDealStatusResponseDto(
                1L, "승민상점 경매방",
                List.of(new AuctionItemDealStatusResponseDto(
                        2L, "포토카드", DealItemStatus.ALL_FAILED,
                        null, null, null, 3, 3)));
        when(dealService.getRoomDeals(1L, LOGIN_USER_ID)).thenReturn(response);

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].dealStatus").value("ALL_FAILED"))
                .andExpect(jsonPath("$.data.items[0].partnerNickname").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].candidateCount").value(3));
    }

    @Test
    @DisplayName("판매자 프로필이 없으면 404를 반환한다")
    void getRoomDeals_sellerProfileNotFound() throws Exception {

        when(dealService.getRoomDeals(1L, LOGIN_USER_ID))
                .thenThrow(new ApplicationException(SellerProfileErrorType.SELLER_PROFILE_NOT_FOUND));

        mockMvc.perform(get(URL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(3002));
    }

    @Test
    @DisplayName("경매방이 없거나 본인 소유가 아니면 404를 반환한다")
    void getRoomDeals_roomNotFoundOrNotOwned() throws Exception {

        when(dealService.getRoomDeals(1L, LOGIN_USER_ID))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));

        mockMvc.perform(get(URL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(4002));
    }

    /** 소유자만 보는 조회다. 세션이 없으면 인터셉터가 막는다. */
    @Test
    @DisplayName("비로그인 요청은 401을 반환한다")
    void getRoomDeals_requiresLogin() throws Exception {

        비로그인_상태로_바꾼다();

        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(1005));
    }
}
