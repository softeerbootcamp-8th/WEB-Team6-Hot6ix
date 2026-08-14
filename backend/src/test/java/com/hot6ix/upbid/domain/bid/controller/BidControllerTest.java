package com.hot6ix.upbid.domain.bid.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hot6ix.upbid.domain.bid.dto.response.BidCreateResponseDto;
import com.hot6ix.upbid.domain.bid.exception.BidErrorType;
import com.hot6ix.upbid.domain.bid.service.BidService;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.exception.GlobalExceptionHandler;
import com.hot6ix.upbid.global.support.AbstractControllerTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = BidController.class)
@Import(GlobalExceptionHandler.class)
class BidControllerTest extends AbstractControllerTest {

    private static final String URL = "/api/v1/auction-items/2/bids";
    private static final String IDEMPOTENCY_KEY = "bid-request-1";

    @MockitoBean
    private BidService bidService;

    private BidCreateResponseDto response() {
        return new BidCreateResponseDto(
                IDEMPOTENCY_KEY,
                2L,
                15_000L,
                LocalDateTime.of(2026, 7, 30, 21, 0),
                LocalDateTime.of(2026, 7, 30, 21, 5),
                30);
    }

    @Test
    @DisplayName("멱등 키가 없으면 400을 반환하고 Service를 부르지 않는다")
    void rejectsMissingIdempotencyKey() throws Exception {

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 15000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002));

        verify(bidService, never()).place(anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("멱등 키가 DB 저장 길이 제한을 넘으면 400을 반환하고 Service를 부르지 않는다")
    void rejectsIdempotencyKeyOver64Characters() throws Exception {

        mockMvc.perform(post(URL)
                        .header("Idempotency-Key", "a".repeat(65))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 15000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002));

        verify(bidService, never()).place(anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("입찰에 성공하면 201과 접수된 입찰을 반환하고 경로 변수·헤더를 그대로 넘긴다")
    void place() throws Exception {

        when(bidService.place(anyLong(), anyLong(), anyLong(), anyString())).thenReturn(response());

        mockMvc.perform(post(URL)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 15000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("입찰이 접수되었습니다."))
                .andExpect(jsonPath("$.data.requestId").value(IDEMPOTENCY_KEY))
                .andExpect(jsonPath("$.data.auctionItemId").value(2))
                .andExpect(jsonPath("$.data.amount").value(15000))
                .andExpect(jsonPath("$.data.acceptedAt").value("2026-07-30T21:00:00"))
                .andExpect(jsonPath("$.data.endAt").value("2026-07-30T21:05:00"))
                .andExpect(jsonPath("$.data.extendedSeconds").value(30));

        verify(bidService).place(2L, LOGIN_USER_ID, 15_000L, IDEMPOTENCY_KEY);
    }

    @Test
    @DisplayName("입찰 금액이 없으면 400과 검증 오류를 반환하고 Service를 부르지 않는다")
    void rejectsMissingAmount() throws Exception {

        mockMvc.perform(post(URL)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.errors[0].field").value("amount"));

        verify(bidService, never()).place(anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("입찰 금액이 0 이하면 400과 검증 오류를 반환한다")
    void rejectsNonPositiveAmount() throws Exception {

        mockMvc.perform(post(URL)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.errors[0].field").value("amount"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("입찰 금액은 0보다 커야 합니다."));

        verify(bidService, never()).place(anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("입찰 금액이 상한을 넘으면 400과 구분된 검증 오류를 반환한다")
    void rejectsAmountOverLimit() throws Exception {

        mockMvc.perform(post(URL)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 5000000000001}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.errors[0].field").value("amount"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("입찰 가능한 금액 범위를 초과했어요"));

        verify(bidService, never()).place(anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("거절된 입찰은 409와 해당 코드를 반환한다")
    void returnsConflictWhenRejected() throws Exception {

        when(bidService.place(anyLong(), anyLong(), anyLong(), anyString()))
                .thenThrow(new ApplicationException(BidErrorType.BID_AMOUNT_TOO_LOW));

        mockMvc.perform(post(URL)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 11000}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(7004));
    }

    @Test
    @DisplayName("판매자 본인의 입찰은 403과 코드 7007을 반환한다")
    void returnsForbiddenWhenSellerBids() throws Exception {

        when(bidService.place(anyLong(), anyLong(), anyLong(), anyString()))
                .thenThrow(new ApplicationException(BidErrorType.SELLER_CANNOT_BID));

        mockMvc.perform(post(URL)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 15000}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(7007));
    }

    @Test
    @DisplayName("약관에 동의하지 않은 회원의 입찰은 403과 코드 7008을 반환한다")
    void returnsForbiddenWhenTermsNotAgreed() throws Exception {

        when(bidService.place(anyLong(), anyLong(), anyLong(), anyString()))
                .thenThrow(new ApplicationException(BidErrorType.TERMS_NOT_AGREED));

        mockMvc.perform(post(URL)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 15000}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(7008));
    }

    @Test
    @DisplayName("경로 변수가 숫자가 아니면 400과 코드 2002를 반환한다")
    void returnsBadRequestWhenPathVariableNotNumeric() throws Exception {

        mockMvc.perform(post("/api/v1/auction-items/abc/bids")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 15000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2002));
    }
}
