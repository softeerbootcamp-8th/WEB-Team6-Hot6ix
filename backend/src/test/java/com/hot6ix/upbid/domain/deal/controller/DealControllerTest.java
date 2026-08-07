package com.hot6ix.upbid.domain.deal.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hot6ix.upbid.domain.deal.dto.response.DealSummaryResponseDto;
import com.hot6ix.upbid.domain.deal.entity.DealItemStatus;
import com.hot6ix.upbid.domain.deal.entity.DealRole;
import com.hot6ix.upbid.domain.deal.service.DealService;
import com.hot6ix.upbid.global.common.ServerTime;
import com.hot6ix.upbid.global.exception.GlobalExceptionHandler;
import com.hot6ix.upbid.global.support.AbstractControllerTest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = DealController.class)
@Import(GlobalExceptionHandler.class)
class DealControllerTest extends AbstractControllerTest {

    private static final String URL = "/api/v1/deals";

    @MockitoBean
    private DealService dealService;

    @Test
    @DisplayName("거래 내역을 조회하면 200과 거래 배열을 반환하고 세션 회원으로 조회한다")
    void getDeals() throws Exception {

        DealSummaryResponseDto deal = new DealSummaryResponseDto(
                2L, 1L, "aBcD1234aBcD1234", 3L, "포토카드",
                "https://upbid-bucket.s3.ap-northeast-2.amazonaws.com/products/1/photocard.png",
                "승민상점 경매방",
                DealRole.SELLER, DealItemStatus.IN_PROGRESS, 15_000L, "원기", 4L,
                ServerTime.toOffset(LocalDateTime.of(2026, 7, 29, 21, 0)));
        when(dealService.getDeals(LOGIN_USER_ID)).thenReturn(List.of(deal));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("거래 내역 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data[0].role").value("SELLER"))
                .andExpect(jsonPath("$.data[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data[0].sellerProfileId").value(4))
                .andExpect(jsonPath("$.data[0].partnerNickname").value("원기"))
                // 거래 상세 화면이 이 코드로 물품 상세를 부른다. 숫자 PK로는 못 부른다.
                .andExpect(jsonPath("$.data[0].shareCode").value("aBcD1234aBcD1234"));

        verify(dealService).getDeals(LOGIN_USER_ID);
    }

    /** 연락처를 목록에 실으면 거래와 무관한 화면까지 개인 정보를 들고 다니게 된다. */
    @Test
    @DisplayName("거래 내역 응답에는 연락처가 없다")
    void getDealsOmitsContact() throws Exception {

        DealSummaryResponseDto deal = new DealSummaryResponseDto(
                2L, 1L, "aBcD1234aBcD1234", null, "포토카드",
                "https://upbid-bucket.s3.ap-northeast-2.amazonaws.com/products/1/photocard.png",
                "승민상점 경매방",
                DealRole.BUYER, DealItemStatus.COMPLETED, 13_000L, "승민", 4L,
                ServerTime.toOffset(LocalDateTime.of(2026, 7, 29, 21, 0)));
        when(dealService.getDeals(LOGIN_USER_ID)).thenReturn(List.of(deal));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.data[0].partnerPhone").doesNotExist())
                // 구매 건은 내 상품이 아니라 productId가 빠진다.
                .andExpect(jsonPath("$.data[0].productId").doesNotExist());
    }

    @Test
    @DisplayName("거래가 없으면 200과 빈 배열을 반환한다")
    void getDealsReturnsEmptyArray() throws Exception {

        when(dealService.getDeals(LOGIN_USER_ID)).thenReturn(List.of());

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
