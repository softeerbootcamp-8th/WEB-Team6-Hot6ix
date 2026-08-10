package com.hot6ix.upbid.domain.sse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.service.AuctionRoomShareService;
import com.hot6ix.upbid.domain.sse.dto.RecentRoomEventDto;
import com.hot6ix.upbid.global.event.EventType;
import com.hot6ix.upbid.global.exception.ApplicationException;
import java.util.ArrayList;
import java.util.List;
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

    @Mock
    private SseEventBuffer sseEventBuffer;

    @InjectMocks
    private SseService sseService;

    private static final String SHARE_CODE = "abcdefghij123456";
    private static final Long ROOM_ID = 7L;

    @Test
    @DisplayName("구독하면 공유 코드로 방을 찾아 emitter를 돌려준다")
    void subscribe_returnsEmitter() {

        SseEmitter emitter = new SseEmitter();
        when(auctionRoomShareService.resolveRoomId(SHARE_CODE)).thenReturn(7L);
        when(roomSseManager.subscribe(any(), eq(7L), any(), any())).thenReturn(emitter);

        SseEmitter result = sseService.subscribe(3L, SHARE_CODE, null);

        verify(auctionRoomShareService).resolveRoomId(SHARE_CODE);
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

    @Test
    @DisplayName("getRecentEvents()는 알림 피드에 쌓이는 이벤트만 남기고 신호성 이벤트는 뺀다")
    void getRecentEvents_filtersOutSignalOnlyEvents() {
        when(auctionRoomShareService.resolveRoomId(SHARE_CODE)).thenReturn(ROOM_ID);
        when(sseEventBuffer.getAllEvents(ROOM_ID)).thenReturn(List.of(
                new BufferedEvent(1, EventType.BID_PLACED.name(), "bid"),
                new BufferedEvent(2, "PARTICIPANT_COUNT_UPDATED", "count"),
                new BufferedEvent(3, EventType.ROOM_UPDATED.name(), "updated"),
                new BufferedEvent(4, EventType.ITEM_ADDED.name(), "added"),
                new BufferedEvent(5, EventType.ITEM_ENDED.name(), "ended")
        ));

        List<RecentRoomEventDto> result = sseService.getRecentEvents(SHARE_CODE);

        assertThat(result).extracting(RecentRoomEventDto::id).containsExactly(1L, 5L);
    }

    @Test
    @DisplayName("getRecentEvents()는 필터 후 10개를 넘으면 가장 최근 10개만 반환한다")
    void getRecentEvents_returnsOnlyLastTenAfterFiltering() {
        when(auctionRoomShareService.resolveRoomId(SHARE_CODE)).thenReturn(ROOM_ID);

        List<BufferedEvent> events = new ArrayList<>();
        for (long id = 1; id <= 15; id++) {
            events.add(new BufferedEvent(id, EventType.BID_PLACED.name(), "bid-" + id));
        }
        when(sseEventBuffer.getAllEvents(ROOM_ID)).thenReturn(events);

        List<RecentRoomEventDto> result = sseService.getRecentEvents(SHARE_CODE);

        assertThat(result).hasSize(10);
        assertThat(result.get(0).id()).isEqualTo(6L);
        assertThat(result.get(9).id()).isEqualTo(15L);
    }

    @Test
    @DisplayName("getRecentEvents()는 10개 미만이면 있는 만큼만 반환한다")
    void getRecentEvents_returnsAllWhenFewerThanLimit() {
        when(auctionRoomShareService.resolveRoomId(SHARE_CODE)).thenReturn(ROOM_ID);
        when(sseEventBuffer.getAllEvents(ROOM_ID)).thenReturn(List.of(
                new BufferedEvent(1, EventType.ITEM_STARTED.name(), "started")
        ));

        List<RecentRoomEventDto> result = sseService.getRecentEvents(SHARE_CODE);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("없는 공유 코드로는 최근 이벤트를 조회하지 못한다")
    void getRecentEvents_unknownShareCode() {
        when(auctionRoomShareService.resolveRoomId("nope"))
                .thenThrow(new ApplicationException(AuctionErrorType.AUCTION_ROOM_NOT_FOUND));

        assertThatThrownBy(() -> sseService.getRecentEvents("nope"))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorType", AuctionErrorType.AUCTION_ROOM_NOT_FOUND);
    }
}
