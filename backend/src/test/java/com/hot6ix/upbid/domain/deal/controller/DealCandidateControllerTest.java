package com.hot6ix.upbid.domain.deal.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.deal.dto.response.DealCandidateListResponseDto;
import com.hot6ix.upbid.domain.deal.dto.response.DealCandidateResponseDto;
import com.hot6ix.upbid.domain.deal.entity.DealStatus;
import com.hot6ix.upbid.domain.deal.entity.DealRole;
import com.hot6ix.upbid.domain.deal.exception.DealErrorType;
import com.hot6ix.upbid.domain.deal.service.DealCandidateService;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.exception.GlobalExceptionHandler;
import com.hot6ix.upbid.global.response.PageResponse;
import com.hot6ix.upbid.global.support.AbstractControllerTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = DealCandidateController.class)
@Import(GlobalExceptionHandler.class)
class DealCandidateControllerTest extends AbstractControllerTest {

    private static final String LIST_URL = "/api/v1/auction-items/2/deal-candidates";
    private static final String FAIL_URL = "/api/v1/auction-items/2/deal-candidates/101/fail";
    private static final String COMPLETE_URL = "/api/v1/auction-items/2/deal-candidates/101/complete";

    @MockitoBean
    private DealCandidateService dealCandidateService;

    @Test
    @DisplayName("거래 실패를 요청하면 200과 성공 메시지를 반환하고 경로 변수를 그대로 넘긴다")
    void fail() throws Exception {

        doNothing().when(dealCandidateService).fail(anyLong(), anyLong(), anyLong());

        mockMvc.perform(post(FAIL_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("거래 실패를 처리했습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(dealCandidateService).fail(2L, 101L, LOGIN_USER_ID);
    }

    @Test
    @DisplayName("거래 성사를 요청하면 200과 성공 메시지를 반환하고 경로 변수를 그대로 넘긴다")
    void complete() throws Exception {

        doNothing().when(dealCandidateService).complete(anyLong(), anyLong(), anyLong());

        mockMvc.perform(post(COMPLETE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("거래 성사를 확정했습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(dealCandidateService).complete(2L, 101L, LOGIN_USER_ID);
    }

    @Test
    @DisplayName("판매자가 아니면 403과 code 6001을 반환한다")
    void failReturnsForbiddenWhenNotOwner() throws Exception {

        doThrow(new ApplicationException(DealErrorType.NOT_DEAL_OWNER))
                .when(dealCandidateService).fail(anyLong(), anyLong(), anyLong());

        mockMvc.perform(post(FAIL_URL))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(6001));
    }

    @Test
    @DisplayName("현재 낙찰 권한자가 아니면 409와 code 6005를 반환한다")
    void failReturnsConflictWhenNotActive() throws Exception {

        doThrow(new ApplicationException(DealErrorType.DEAL_CANDIDATE_NOT_ACTIVE))
                .when(dealCandidateService).fail(anyLong(), anyLong(), anyLong());

        mockMvc.perform(post(FAIL_URL))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(6005));
    }

    @Test
    @DisplayName("물품이 없으면 404와 code 4001을 반환한다")
    void completeReturnsNotFoundWhenItemMissing() throws Exception {

        doThrow(new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND))
                .when(dealCandidateService).complete(anyLong(), anyLong(), anyLong());

        mockMvc.perform(post(COMPLETE_URL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    @DisplayName("경로 변수가 숫자가 아니면 400과 code 2002를 반환한다")
    void failReturnsBadRequestWhenPathVariableIsNotNumber() throws Exception {

        mockMvc.perform(post("/api/v1/auction-items/abc/deal-candidates/101/fail"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2002));
    }

    @Test
    @DisplayName("후보 목록을 조회하면 200과 역할·순위·페이지 정보를 반환한다")
    void getCandidates() throws Exception {

        DealCandidateResponseDto candidate = new DealCandidateResponseDto(
                101L, 1, "원기", 15_000L, DealStatus.IN_PROGRESS, "010-1234-5678", false);
        when(dealCandidateService.getCandidates(anyLong(), anyInt(), anyLong()))
                .thenReturn(new DealCandidateListResponseDto(DealRole.SELLER, null,
                        new PageResponse<>(List.of(candidate), 0, 1, 12, 3)));

        mockMvc.perform(get(LIST_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("낙찰 후보 목록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data.viewerRole").value("SELLER"))
                .andExpect(jsonPath("$.data.candidates.totalElements").value(12))
                .andExpect(jsonPath("$.data.candidates.totalPages").value(3))
                .andExpect(jsonPath("$.data.candidates.content[0].dealStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.candidates.content[0].phoneNumber").value("010-1234-5678"));

        // page를 안 보내면 첫 페이지다.
        verify(dealCandidateService).getCandidates(2L, 0, LOGIN_USER_ID);
    }

    @Test
    @DisplayName("page를 주면 그대로 서비스에 넘긴다")
    void getCandidatesPassesPage() throws Exception {

        when(dealCandidateService.getCandidates(anyLong(), anyInt(), anyLong()))
                .thenReturn(new DealCandidateListResponseDto(DealRole.BUYER, 7,
                        new PageResponse<>(List.of(), 1, 0, 12, 3)));

        mockMvc.perform(get(LIST_URL).param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myRank").value(7));

        verify(dealCandidateService).getCandidates(2L, 1, LOGIN_USER_ID);
    }

    /** 검증이 없으면 PageRequest가 IllegalArgumentException을 던져 500이 나간다. */
    @Test
    @DisplayName("page가 음수면 400과 code 2002를 반환하고 서비스를 호출하지 않는다")
    void getCandidatesRejectsNegativePage() throws Exception {

        mockMvc.perform(get(LIST_URL).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(2002));

        verify(dealCandidateService, never()).getCandidates(anyLong(), anyInt(), anyLong());
    }

    /** null 필드는 응답에서 빠진다. 구매자 응답에 연락처 키가 남아 있으면 안 된다. */
    @Test
    @DisplayName("연락처가 없는 후보는 응답에 그 필드가 빠진다")
    void getCandidatesOmitsNullContact() throws Exception {

        DealCandidateResponseDto candidate = new DealCandidateResponseDto(
                101L, 1, "원기", 15_000L, DealStatus.WAITING, null, true);
        when(dealCandidateService.getCandidates(anyLong(), anyInt(), anyLong()))
                .thenReturn(new DealCandidateListResponseDto(DealRole.BUYER, 1,
                        new PageResponse<>(List.of(candidate), 0, 1, 1, 1)));

        mockMvc.perform(get(LIST_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates.content[0].isMe").value(true))
                .andExpect(jsonPath("$.data.candidates.content[0].phoneNumber").doesNotExist());
    }

    @Test
    @DisplayName("판매자도 후보도 아니면 403과 code 6007을 반환한다")
    void getCandidatesReturnsForbiddenWhenNotParticipant() throws Exception {

        when(dealCandidateService.getCandidates(anyLong(), anyInt(), anyLong()))
                .thenThrow(new ApplicationException(DealErrorType.NOT_DEAL_VIEWER));

        mockMvc.perform(get(LIST_URL))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(6007));
    }
}
