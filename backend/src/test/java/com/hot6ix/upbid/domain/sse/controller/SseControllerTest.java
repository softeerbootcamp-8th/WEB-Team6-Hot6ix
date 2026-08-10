package com.hot6ix.upbid.domain.sse.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.sse.dto.RecentRoomEventDto;
import com.hot6ix.upbid.domain.sse.service.SseService;
import com.hot6ix.upbid.global.event.EventType;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.exception.GlobalExceptionHandler;
import com.hot6ix.upbid.global.support.AbstractControllerTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = SseController.class)
@Import(GlobalExceptionHandler.class)
class SseControllerTest extends AbstractControllerTest {

    @MockitoBean
    private SseService sseService;

    private static final String SHARE_CODE = "aBcD1234aBcD1234";

    @Test
    @DisplayName("최근 이벤트를 조회하면 알림 목록을 반환한다")
    void getRecentEvents_returnsEvents() throws Exception {
        when(sseService.getRecentEvents(SHARE_CODE)).thenReturn(List.of(
                new RecentRoomEventDto(1L, EventType.BID_PLACED.name(), "data")
        ));

        mockMvc.perform(get("/api/v1/auction-rooms/share/{shareCode}/events", SHARE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].eventName").value("BID_PLACED"));
    }

    @Test
    @DisplayName("비로그인 사용자도 최근 이벤트를 조회할 수 있다")
    void getRecentEvents_allowsGuest() throws Exception {
        비로그인_상태로_바꾼다();
        when(sseService.getRecentEvents(SHARE_CODE)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/auction-rooms/share/{shareCode}/events", SHARE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("존재하지 않는 공유 코드로 조회하면 404를 반환한다")
    void getRecentEvents_notFound() throws Exception {
        when(sseService.getRecentEvents("nope"))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));

        mockMvc.perform(get("/api/v1/auction-rooms/share/{shareCode}/events", "nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4002));
    }
}
