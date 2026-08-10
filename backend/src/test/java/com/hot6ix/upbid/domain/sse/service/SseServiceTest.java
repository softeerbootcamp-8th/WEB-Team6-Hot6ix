package com.hot6ix.upbid.domain.sse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.service.AuctionRoomShareService;
import com.hot6ix.upbid.global.exception.ApplicationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class SseServiceTest {

    @Mock
    private RoomSseManager roomSseManager;

    @Mock
    private AuctionRoomShareService auctionRoomShareService;

    @InjectMocks
    private SseService sseService;

    private static final String SHARE_CODE = "abcdefghij123456";

    @Test
    @DisplayName("구독하면 공유 코드로 방을 찾아 emitter를 돌려준다")
    void subscribe_returnsEmitter() {

        SseEmitter emitter = new SseEmitter();
        when(auctionRoomShareService.resolveOpenRoomId(SHARE_CODE)).thenReturn(7L);
        when(roomSseManager.subscribe(7L, null)).thenReturn(emitter);

        SseEmitter result = sseService.subscribe(3L, SHARE_CODE, null);

        verify(auctionRoomShareService).resolveOpenRoomId(SHARE_CODE);
        verify(roomSseManager).subscribe(7L, null);
        assertThat(result).isSameAs(emitter);
    }

    @Test
    @DisplayName("없는 공유 코드로는 구독하지 못한다")
    void subscribe_unknownShareCode() {

        when(auctionRoomShareService.resolveOpenRoomId("nope"))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));

        assertThatThrownBy(() -> sseService.subscribe(3L, "nope", null))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorType", AuctionErrorType.AUCTION_ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("종료된 경매방은 구독하지 못한다")
    void subscribe_closedRoom() {

        when(auctionRoomShareService.resolveOpenRoomId(SHARE_CODE))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ROOM_CLOSED));

        // 방을 닫을 때 서버가 연결을 끊지만, EventSource는 스트림 종료를 네트워크 단절과
        // 구분하지 못해 한 번 다시 붙는다. 그 재접속을 여기서 끊어내야 루프가 멈춘다.
        assertThatThrownBy(() -> sseService.subscribe(3L, SHARE_CODE, null))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorType", AuctionErrorType.AUCTION_ROOM_CLOSED);

        verify(roomSseManager, never()).subscribe(any(), any());
    }
}
