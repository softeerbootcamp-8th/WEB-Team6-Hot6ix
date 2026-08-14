package com.hot6ix.upbid.global.event.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hot6ix.upbid.domain.sse.dto.ItemClosingSoonDto;
import com.hot6ix.upbid.domain.sse.dto.ItemEndedDto;
import com.hot6ix.upbid.domain.sse.dto.RoomClosedDto;
import com.hot6ix.upbid.domain.sse.event.SseEventPublisher;
import com.hot6ix.upbid.global.event.payload.DealRightAssigned;
import com.hot6ix.upbid.global.event.payload.ItemClosingSoon;
import com.hot6ix.upbid.global.event.payload.ItemEnded;
import com.hot6ix.upbid.global.event.payload.ItemPassed;
import com.hot6ix.upbid.global.event.payload.RoomClosed;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 이 리스너는 도메인 이벤트를 SSE 이벤트로 바꿔 Redis 채널로 내보내는 데까지만 책임진다.
 * 방 종료 시 연결을 끊는 일은 채널을 받는 쪽으로 옮겨 갔고, {@code SseEventSubscriberTest}가 본다.
 */
@ExtendWith(MockitoExtension.class)
class DomainEventSseListenerTest {

    private static final Long ROOM_ID = 10L;
    private static final Long ITEM_ID = 30L;
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 8, 4, 21, 0);

    @Mock
    private SseEventPublisher sseEventPublisher;

    @InjectMocks
    private DomainEventSseListener domainEventSseListener;

    @Test
    @DisplayName("낙찰은 ITEM_ENDED로 낙찰가와 낙찰자를 함께 내보낸다")
    void sendsItemEnded() {

        domainEventSseListener.on(
                ItemEnded.of(ROOM_ID, ITEM_ID, "한정판 피규어", 12_000L, "한기", OCCURRED_AT));

        assertThat(sentDto("ITEM_ENDED")).isEqualTo(
                new ItemEndedDto(ITEM_ID, "한정판 피규어", 12_000L, "한기"));
    }

    @Test
    @DisplayName("유찰도 ITEM_ENDED로 내보내며 낙찰가와 낙찰자는 비운다")
    void sendsItemPassedAsItemEnded() {

        domainEventSseListener.on(ItemPassed.of(ROOM_ID, ITEM_ID, "한정판 피규어", OCCURRED_AT));

        assertThat(sentDto("ITEM_ENDED"))
                .as("화면에서 낙찰과 유찰은 '물품이 닫혔다'는 같은 사건이고, "
                        + "구분은 winnerNickname으로 한다. ITEM_PASSED로 내보내면 화면이 듣지 않는다")
                .isEqualTo(new ItemEndedDto(ITEM_ID, "한정판 피규어", null, null));
    }

    @Test
    @DisplayName("마감 임박은 ITEM_CLOSING_SOON으로 남은 초까지 함께 내보낸다")
    void sendsItemClosingSoon() {

        domainEventSseListener.on(
                ItemClosingSoon.of(ROOM_ID, ITEM_ID, "한정판 피규어", 300, OCCURRED_AT));

        assertThat(sentDto("ITEM_CLOSING_SOON"))
                .as("알림 시점이 방의 Soft Close 트리거라 방마다 다르다. "
                        + "이 값이 빠지면 화면이 '마감 1분 전'으로 고정된 문구밖에 못 만든다")
                .isEqualTo(new ItemClosingSoonDto(ITEM_ID, "한정판 피규어", 300));
    }

    @Test
    @DisplayName("경매방 종료는 ROOM_CLOSED로 방 이름과 종료 시각을 내보낸다")
    void sendsRoomClosed() {

        domainEventSseListener.on(RoomClosed.of(ROOM_ID, "승민의 경매방", OCCURRED_AT));

        assertThat(sentDto("ROOM_CLOSED"))
                .isEqualTo(new RoomClosedDto("승민의 경매방", OCCURRED_AT));
    }

    @Test
    @DisplayName("화면에 뿌릴 DTO가 없는 이벤트는 내보내지 않는다")
    void skipsEventWithoutDto() {

        domainEventSseListener.on(
                DealRightAssigned.of(ROOM_ID, ITEM_ID, 1L, 1, 40L, 12_000L, OCCURRED_AT));

        verify(sseEventPublisher, never()).publish(any(), any(), any());
    }

    /** 지정한 이름으로 나간 payload를 꺼낸다. 이름이 다르면 그 자리에서 검증이 실패한다. */
    private Object sentDto(String eventName) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(sseEventPublisher).publish(eq(eventName), eq(ROOM_ID), captor.capture());
        return captor.getValue();
    }
}
