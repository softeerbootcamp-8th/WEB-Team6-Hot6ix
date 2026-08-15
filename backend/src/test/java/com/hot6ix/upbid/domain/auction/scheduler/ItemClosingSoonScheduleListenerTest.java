package com.hot6ix.upbid.domain.auction.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.service.ItemClosingSoonService;
import com.hot6ix.upbid.global.event.payload.ItemCloseAdvanced;
import com.hot6ix.upbid.global.event.payload.ItemEnded;
import com.hot6ix.upbid.global.event.payload.ItemPassed;
import com.hot6ix.upbid.global.event.payload.ItemStarted;
import com.hot6ix.upbid.global.event.payload.SoftCloseExtended;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;

/**
 * 어떤 이벤트에 알림 예약을 걸고 취소하는지만 본다. 예약이 실제로 어디에 담기는지는
 * {@code RedisItemClosingSoonScheduler}가, 꺼내서 실행하는 것은
 * {@link ItemClosingSoonPollerTest}가 맡는다.
 *
 * <p>마감({@link AuctionCloseScheduleListenerTest})과 판단이 다르다. 알림 시각은 이벤트에
 * 실려 오지 않아 {@link ItemClosingSoonService}에 물어봐야 하고, 마감 앞당기기는 새 알림 시각이
 * 남아 있는지에 따라 재예약과 취소로 갈린다.
 */
@ExtendWith(MockitoExtension.class)
class ItemClosingSoonScheduleListenerTest {

    private static final Long ROOM_ID = 10L;
    private static final Long ITEM_ID = 30L;
    private static final String ITEM_NAME = "한정판 피규어";
    private static final int EXTEND_SECONDS = 60;

    /** 마감 10분 전 시작, 트리거 60초인 물품의 알림 시각. */
    private static final LocalDateTime NOTIFY_AT = LocalDateTime.now().plusMinutes(9);
    /** 연장이 붙어 뒤로 밀린 알림 시각. 연장 폭만큼만 밀린다. */
    private static final LocalDateTime EXTENDED_NOTIFY_AT = NOTIFY_AT.plusSeconds(EXTEND_SECONDS);

    @Mock
    private ItemClosingSoonScheduler itemClosingSoonScheduler;

    @Mock
    private ItemClosingSoonService itemClosingSoonService;

    @InjectMocks
    private ItemClosingSoonScheduleListener itemClosingSoonScheduleListener;

    @Test
    @DisplayName("시작 이벤트를 받으면 알림 시각에 예약한다")
    void schedulesAtNotifyAt() {

        givenNotifyAt(NOTIFY_AT);

        itemClosingSoonScheduleListener.on(itemStarted());

        // 마감 시각이 아니라 연장 구간이 열리는 시각이다.
        verify(itemClosingSoonScheduler).schedule(ITEM_ID, NOTIFY_AT);
    }

    @Test
    @DisplayName("알릴 물품이 아니면 예약하지 않는다")
    void doesNotScheduleWhenNotNotifiable() {

        when(itemClosingSoonService.resolveNotifyAt(ITEM_ID)).thenReturn(Optional.empty());

        itemClosingSoonScheduleListener.on(itemStarted());

        verify(itemClosingSoonScheduler, never()).schedule(any(), any());
    }

    @Test
    @DisplayName("알림 시각이 이미 지났으면 예약하지 않는다")
    void doesNotScheduleWhenNotifyAtIsPast() {

        givenNotifyAt(LocalDateTime.now().minusMinutes(1));

        itemClosingSoonScheduleListener.on(softCloseExtended());

        // 알림 구간 안에 계속 있다는 뜻이라 새로 알릴 사건이 없다.
        verify(itemClosingSoonScheduler, never()).schedule(any(), any());
    }

    @Test
    @DisplayName("연장을 받으면 밀린 알림 시각으로 예약을 갈아 끼운다")
    void reschedulesOnSoftCloseExtended() {

        givenNotifyAt(EXTENDED_NOTIFY_AT);

        itemClosingSoonScheduleListener.on(softCloseExtended());

        // 밀지 않으면 옛 시각에 깨어나 연장 전 기준으로 "곧 마감"을 알린다.
        verify(itemClosingSoonScheduler).schedule(ITEM_ID, EXTENDED_NOTIFY_AT);
    }

    @Test
    @DisplayName("예약 등록이 실패해도 시작 요청까지 실패시키지 않는다")
    void swallowsScheduleFailure() {

        givenNotifyAt(NOTIFY_AT);
        doThrow(new QueryTimeoutException("Redis 연결 끊김"))
                .when(itemClosingSoonScheduler).schedule(any(), any());

        assertThatCode(() -> itemClosingSoonScheduleListener.on(itemStarted()))
                .as("커밋 뒤에 도는 리스너라, 여기서 던지면 이미 시작된 물품인데 시작 API가 실패한다")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("알림 시각 조회가 실패해도 시작 요청까지 실패시키지 않는다")
    void swallowsResolveFailure() {

        when(itemClosingSoonService.resolveNotifyAt(ITEM_ID))
                .thenThrow(new IllegalStateException("DB 연결 끊김"));

        assertThatCode(() -> itemClosingSoonScheduleListener.on(itemStarted()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("낙찰로 마감되면 남은 알림 예약을 취소한다")
    void cancelsScheduleOnItemEnded() {

        itemClosingSoonScheduleListener.on(
                ItemEnded.of(ROOM_ID, ITEM_ID, ITEM_NAME, 12_000L, "한기", LocalDateTime.now()));

        // 방 종료로 먼저 닫힌 물품에 "곧 마감" 알림이 뒤늦게 나가면 안 된다.
        verify(itemClosingSoonScheduler).cancel(ITEM_ID);
    }

    @Test
    @DisplayName("유찰로 마감돼도 남은 알림 예약을 취소한다")
    void cancelsScheduleOnItemPassed() {

        itemClosingSoonScheduleListener.on(
                ItemPassed.of(ROOM_ID, ITEM_ID, ITEM_NAME, LocalDateTime.now()));

        verify(itemClosingSoonScheduler).cancel(ITEM_ID);
    }

    @Test
    @DisplayName("트리거만큼만 남기고 앞당기면 알림 예약을 취소하고 다시 걸지 않는다")
    void cancelsScheduleOnItemCloseAdvanced() {

        givenNotifyAt(LocalDateTime.now().minusSeconds(1));

        itemClosingSoonScheduleListener.on(itemCloseAdvanced());

        // 남겨두면 옛 마감 기준 예약이 깨어나 실제 남은 시간과 다른 "곧 마감"을 알린다.
        verify(itemClosingSoonScheduler).cancel(ITEM_ID);
        // 앞당긴 순간이 곧 알림 시각이라 새로 걸 예약이 없다.
        verify(itemClosingSoonScheduler, never()).schedule(any(), any());
    }

    @Test
    @DisplayName("트리거보다 길게 남기고 앞당기면 알림 예약을 새 시각으로 다시 건다")
    void reschedulesOnItemCloseAdvancedWhenNotifyAtIsStillAhead() {

        LocalDateTime notifyAt = LocalDateTime.now().plusMinutes(9);
        givenNotifyAt(notifyAt);

        itemClosingSoonScheduleListener.on(itemCloseAdvanced());

        // 앞당겼어도 알림 시각이 아직 미래라 "곧 마감"을 알릴 순간이 남아 있다.
        verify(itemClosingSoonScheduler).cancel(ITEM_ID);
        verify(itemClosingSoonScheduler).schedule(ITEM_ID, notifyAt);
    }

    @Test
    @DisplayName("예약 취소가 실패해도 마감 요청까지 실패시키지 않는다")
    void swallowsCancelFailure() {

        doThrow(new QueryTimeoutException("Redis 연결 끊김"))
                .when(itemClosingSoonScheduler).cancel(ITEM_ID);

        assertThatCode(() -> itemClosingSoonScheduleListener.on(itemCloseAdvanced()))
                .as("취소가 실패해 예약이 남아도 알림은 안 나간다. 폴러가 물어보면 서비스가 걸러낸다")
                .doesNotThrowAnyException();
    }

    private ItemCloseAdvanced itemCloseAdvanced() {
        LocalDateTime now = LocalDateTime.now();
        return ItemCloseAdvanced.of(ROOM_ID, ITEM_ID, ITEM_NAME, 60, now.plusSeconds(60), now);
    }

    private ItemStarted itemStarted() {
        LocalDateTime now = LocalDateTime.now();
        return ItemStarted.of(ROOM_ID, ITEM_ID, ITEM_NAME, now, now.plusMinutes(10));
    }

    private SoftCloseExtended softCloseExtended() {
        LocalDateTime now = LocalDateTime.now();
        return SoftCloseExtended.of(
                ROOM_ID, ITEM_ID, ITEM_NAME, EXTEND_SECONDS, now.plusMinutes(10), now);
    }

    private void givenNotifyAt(LocalDateTime notifyAt) {
        when(itemClosingSoonService.resolveNotifyAt(ITEM_ID)).thenReturn(Optional.of(notifyAt));
    }
}
