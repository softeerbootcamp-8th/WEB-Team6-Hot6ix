package com.hot6ix.upbid.domain.sse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.service.AuctionParticipantService;
import com.hot6ix.upbid.domain.auction.service.AuctionRoomShareService;
import com.hot6ix.upbid.global.exception.ApplicationException;
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

    @Mock
    private AuctionRoomShareService auctionRoomShareService;

    @InjectMocks
    private SseService sseService;

    private static final String SHARE_CODE = "abcdefghij123456";

    @Test
    @DisplayName("구독하면 공유 코드로 방을 찾아 참여 기록을 남기고 emitter를 돌려준다")
    void subscribe_recordsParticipant() {

        SseEmitter emitter = new SseEmitter();
        when(auctionRoomShareService.resolveRoomId(SHARE_CODE)).thenReturn(7L);
        when(roomSseManager.subscribe(any(), eq(7L), any())).thenReturn(emitter);

        // (userId=3, roomId=7). record는 (roomId, userId) 순서라 뒤바뀌면 이 검증이 깨진다.
        SseEmitter result = sseService.subscribe(3L, SHARE_CODE);

        verify(auctionParticipantService).record(7L, 3L);
        assertThat(result).isSameAs(emitter);
    }

    @Test
    @DisplayName("참여 기록이 실패해도 구독은 성공한다")
    void subscribe_succeedsWhenRecordFails() {

        SseEmitter emitter = new SseEmitter();
        when(auctionRoomShareService.resolveRoomId(SHARE_CODE)).thenReturn(7L);
        when(roomSseManager.subscribe(any(), eq(7L), any())).thenReturn(emitter);
        doThrow(new DataAccessResourceFailureException("DB 접속 실패"))
                .when(auctionParticipantService).record(7L, 3L);

        SseEmitter result = sseService.subscribe(3L, SHARE_CODE);

        assertThat(result).isSameAs(emitter);
    }

    @Test
    @DisplayName("없는 공유 코드로는 구독하지 못한다 — 참여 기록도 남기지 않는다")
    void subscribe_unknownShareCode() {

        when(auctionRoomShareService.resolveRoomId("nope"))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));

        assertThatThrownBy(() -> sseService.subscribe(3L, "nope"))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorType", AuctionErrorType.AUCTION_ROOM_NOT_FOUND);

        verifyNoInteractions(auctionParticipantService, roomSseManager);
    }
}
