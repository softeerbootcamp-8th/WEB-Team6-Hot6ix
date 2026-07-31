package com.hot6ix.upbid.domain.deal.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hot6ix.upbid.domain.auction.exception.AuctionItemErrorType;
import com.hot6ix.upbid.domain.deal.exception.DealErrorType;
import com.hot6ix.upbid.domain.deal.service.DealCandidateService;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.exception.GlobalExceptionHandler;
import com.hot6ix.upbid.global.support.AbstractControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = DealCandidateController.class)
@Import(GlobalExceptionHandler.class)
class DealCandidateControllerTest extends AbstractControllerTest {

    private static final String FAIL_URL = "/api/v1/auction-items/2/deal-candidates/101/fail";
    private static final String COMPLETE_URL = "/api/v1/auction-items/2/deal-candidates/101/complete";

    @MockitoBean
    private DealCandidateService dealCandidateService;

    @Test
    @DisplayName("거래 실패를 요청하면 200과 성공 메시지를 반환하고 경로 변수를 그대로 넘긴다")
    void fail() throws Exception {

        doNothing().when(dealCandidateService).fail(anyLong(), anyLong(), anyLong());

        mockMvc.perform(post(FAIL_URL).header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("거래 실패를 처리했습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(dealCandidateService).fail(2L, 101L, 7L);
    }

    @Test
    @DisplayName("거래 성사를 요청하면 200과 성공 메시지를 반환하고 경로 변수를 그대로 넘긴다")
    void complete() throws Exception {

        doNothing().when(dealCandidateService).complete(anyLong(), anyLong(), anyLong());

        mockMvc.perform(post(COMPLETE_URL).header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("거래 성사를 확정했습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(dealCandidateService).complete(2L, 101L, 7L);
    }

    @Test
    @DisplayName("판매자가 아니면 403과 code 6001을 반환한다")
    void failReturnsForbiddenWhenNotOwner() throws Exception {

        doThrow(new ApplicationException(DealErrorType.NOT_DEAL_OWNER))
                .when(dealCandidateService).fail(anyLong(), anyLong(), anyLong());

        mockMvc.perform(post(FAIL_URL).header("X-User-Id", "8"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(6001));
    }

    @Test
    @DisplayName("현재 낙찰 권한자가 아니면 409와 code 6005를 반환한다")
    void failReturnsConflictWhenNotActive() throws Exception {

        doThrow(new ApplicationException(DealErrorType.DEAL_CANDIDATE_NOT_ACTIVE))
                .when(dealCandidateService).fail(anyLong(), anyLong(), anyLong());

        mockMvc.perform(post(FAIL_URL).header("X-User-Id", "7"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(6005));
    }

    @Test
    @DisplayName("물품이 없으면 404와 code 4001을 반환한다")
    void completeReturnsNotFoundWhenItemMissing() throws Exception {

        doThrow(new ApplicationException(AuctionItemErrorType.AUCTION_ITEM_NOT_FOUND))
                .when(dealCandidateService).complete(anyLong(), anyLong(), anyLong());

        mockMvc.perform(post(COMPLETE_URL).header("X-User-Id", "7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    @DisplayName("경로 변수가 숫자가 아니면 400과 code 2002를 반환한다")
    void failReturnsBadRequestWhenPathVariableIsNotNumber() throws Exception {

        mockMvc.perform(post("/api/v1/auction-items/abc/deal-candidates/101/fail").header("X-User-Id", "7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2002));
    }
}
