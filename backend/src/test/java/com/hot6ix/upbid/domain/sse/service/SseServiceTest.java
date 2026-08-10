package com.hot6ix.upbid.domain.sse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        when(auctionRoomShareService.resolveRoomId(SHARE_CODE)).thenReturn(7L);
        when(roomSseManager.subscribe(7L)).thenReturn(emitter);
        when(roomSseManager.subscribe(any(), eq(7L), any(), any())).thenReturn(emitter);

        SseEmitter result = sseService.subscribe(3L, SHARE_CODE, null);

        verify(auctionRoomShareService).resolveRoomId(SHARE_CODE);
        verify(roomSseManager).subscribe(7L);
        assertThat(result).isSameAs(emitter);
    }

    @Test
    @DisplayName("없는 공유 코드로는 구독하지 못한다")
    void subscribe_unknownShareCode() {

        when(auctionRoomShareService.resolveRoomId("nope"))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));

        assertThatThrownBy(() -> sseService.subscribe(3L, "nope", null))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorType", AuctionErrorType.AUCTION_ROOM_NOT_FOUND);
    }
}
