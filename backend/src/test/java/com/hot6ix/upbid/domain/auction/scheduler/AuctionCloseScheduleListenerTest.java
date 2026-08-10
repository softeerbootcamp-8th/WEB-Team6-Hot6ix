package com.hot6ix.upbid.domain.auction.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.hot6ix.upbid.global.event.payload.ItemCloseAdvanced;
import com.hot6ix.upbid.global.event.payload.ItemEnded;
import com.hot6ix.upbid.global.event.payload.ItemPassed;
import com.hot6ix.upbid.global.event.payload.ItemStarted;
import com.hot6ix.upbid.global.event.payload.SoftCloseExtended;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

/**
 * 어떤 이벤트에 예약을 걸고 취소하는지만 본다. 예약이 실제로 메모리에 어떻게 담기고
 * 실행되는지는 {@code InMemoryAuctionCloseSchedulerTest}가 맡는다.
 */
@ExtendWith(MockitoExtension.class)
class AuctionCloseScheduleListenerTest {

    private static final Long ROOM_ID = 10L;
    private static final Long ITEM_ID = 30L;
    private static final String ITEM_NAME = "한정판 피규어";
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 8, 2, 21, 0);
    private static final LocalDateTime END_AT = LocalDateTime.of(2026, 8, 2, 21, 10);
    /** Soft Close로 30초 밀린 마감 시각. */
    private static final LocalDateTime EXTENDED_END_AT = END_AT.plusSeconds(30);
    /** 판매자가 앞당겨 9분 앞으로 당겨진 마감 시각. */
    private static final LocalDateTime ADVANCED_END_AT = END_AT.minusMinutes(9);

    @Mock
    private AuctionCloseScheduler auctionCloseScheduler;

    @InjectMocks
    private AuctionCloseScheduleListener auctionCloseScheduleListener;

    @Test
    @DisplayName("시작 이벤트를 받으면 마감 시각에 예약한다")
    void schedulesOnItemStarted() {

        auctionCloseScheduleListener.on(itemStarted());

        verify(auctionCloseScheduler).schedule(ITEM_ID, END_AT);
    }

    @Test
    @DisplayName("예약 등록이 실패해도 시작 요청까지 실패시키지 않는다")
    void swallowsScheduleFailure() {

        doThrow(new TaskRejectedException("스케줄러가 내려가는 중"))
                .when(auctionCloseScheduler).schedule(any(), any());

        assertThatCode(() -> auctionCloseScheduleListener.on(itemStarted()))
                .as("커밋 뒤에 도는 리스너라, 여기서 던지면 이미 시작된 물품인데 시작 API가 실패한다")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Soft Close 연장을 받으면 밀린 마감 시각으로 예약을 갈아 끼운다")
    void reschedulesOnSoftCloseExtended() {

        auctionCloseScheduleListener.on(SoftCloseExtended.of(
                ROOM_ID, ITEM_ID, ITEM_NAME, 30, EXTENDED_END_AT, END_AT));

        verify(auctionCloseScheduler).schedule(ITEM_ID, EXTENDED_END_AT);
    }

    @Test
    @DisplayName("재예약이 실패해도 입찰 요청까지 실패시키지 않는다")
    void swallowsRescheduleFailure() {

        doThrow(new TaskRejectedException("스케줄러가 내려가는 중"))
                .when(auctionCloseScheduler).schedule(any(), any());

        assertThatCode(() -> auctionCloseScheduleListener.on(SoftCloseExtended.of(
                ROOM_ID, ITEM_ID, ITEM_NAME, 30, EXTENDED_END_AT, END_AT)))
                .as("입찰은 이미 저장됐는데 요청만 실패로 보이면 안 된다")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("판매자가 마감을 앞당기면 당겨진 시각으로 예약을 갈아 끼운다")
    void reschedulesOnItemCloseAdvanced() {

        auctionCloseScheduleListener.on(itemCloseAdvanced());

        verify(auctionCloseScheduler).schedule(ITEM_ID, ADVANCED_END_AT);
    }

    @Test
    @DisplayName("앞당김 재예약이 실패해도 요청까지 실패시키지 않는다")
    void swallowsAdvancedRescheduleFailure() {

        doThrow(new TaskRejectedException("스케줄러가 내려가는 중"))
                .when(auctionCloseScheduler).schedule(any(), any());

        assertThatCode(() -> auctionCloseScheduleListener.on(itemCloseAdvanced()))
                .as("마감 시각은 이미 앞당겨졌는데 요청만 실패로 보이면 안 된다")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("낙찰로 마감되면 남은 예약을 취소한다")
    void cancelsOnItemEnded() {

        auctionCloseScheduleListener.on(
                ItemEnded.of(ROOM_ID, ITEM_ID, ITEM_NAME, 12_000L, "한기", END_AT));

        verify(auctionCloseScheduler).cancel(ITEM_ID);
    }

    @Test
    @DisplayName("유찰로 마감돼도 남은 예약을 취소한다")
    void cancelsOnItemPassed() {

        auctionCloseScheduleListener.on(ItemPassed.of(ROOM_ID, ITEM_ID, ITEM_NAME, END_AT));

        verify(auctionCloseScheduler).cancel(ITEM_ID);
    }

    private ItemStarted itemStarted() {
        return ItemStarted.of(ROOM_ID, ITEM_ID, ITEM_NAME, STARTED_AT, END_AT);
    }

    private ItemCloseAdvanced itemCloseAdvanced() {
        return ItemCloseAdvanced.of(ROOM_ID, ITEM_ID, ITEM_NAME, 60, ADVANCED_END_AT, STARTED_AT);
    }
}
