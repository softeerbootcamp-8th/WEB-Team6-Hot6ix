package com.hot6ix.upbid.domain.sse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.service.AuctionParticipantService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class SseServiceTest {

    @Mock
    private RoomSseManager roomSseManager;

    @Mock
    private AuctionParticipantService auctionParticipantService;

    @InjectMocks
    private SseService sseService;

    @Test
    @DisplayName("구독하면 참여 기록을 남기고 emitter를 돌려준다")
    void subscribe_recordsParticipant() {

        SseEmitter emitter = new SseEmitter();
        when(roomSseManager.subscribe(any(), eq(7L), any())).thenReturn(emitter);

        // (userId=3, roomId=7). record는 (roomId, userId) 순서라 뒤바뀌면 이 검증이 깨진다.
        SseEmitter result = sseService.subscribe(3L, 7L);

        verify(auctionParticipantService).record(7L, 3L);
        assertThat(result).isSameAs(emitter);
    }

    @Test
    @DisplayName("참여 기록이 실패해도 구독은 성공한다")
    void subscribe_succeedsWhenRecordFails() {

        SseEmitter emitter = new SseEmitter();
        when(roomSseManager.subscribe(any(), eq(7L), any())).thenReturn(emitter);
        doThrow(new DataAccessResourceFailureException("DB 접속 실패"))
                .when(auctionParticipantService).record(7L, 3L);

        SseEmitter result = sseService.subscribe(3L, 7L);

        assertThat(result).isSameAs(emitter);
    }
}
