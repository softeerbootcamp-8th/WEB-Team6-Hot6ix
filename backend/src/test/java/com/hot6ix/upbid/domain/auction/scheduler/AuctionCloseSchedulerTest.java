package com.hot6ix.upbid.domain.auction.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.service.AuctionItemCloseService;
import com.hot6ix.upbid.global.event.payload.ItemCloseAdvanced;
import com.hot6ix.upbid.global.event.payload.ItemEnded;
import com.hot6ix.upbid.global.event.payload.ItemPassed;
import com.hot6ix.upbid.global.event.payload.ItemStarted;
import com.hot6ix.upbid.global.event.payload.SoftCloseExtended;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuctionCloseSchedulerTest {

    private static final Long ROOM_ID = 10L;
    private static final Long ITEM_ID = 30L;
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 8, 2, 21, 0);
    private static final LocalDateTime END_AT = LocalDateTime.of(2026, 8, 2, 21, 10);
    /** Soft Close로 30초 밀린 마감 시각. */
    private static final LocalDateTime EXTENDED_END_AT = END_AT.plusSeconds(30);
    /** 판매자가 앞당겨 9분 앞으로 당겨진 마감 시각. */
    private static final LocalDateTime ADVANCED_END_AT = END_AT.minusMinutes(9);

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private AuctionItemCloseService auctionItemCloseService;

    @InjectMocks
    private AuctionCloseScheduler auctionCloseScheduler;

    @Test
    @DisplayName("시작 이벤트를 받으면 마감 시각에 예약한다")
    void schedulesAtEndAt() {

        givenScheduledFuture();

        auctionCloseScheduler.on(itemStarted());

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(taskScheduler).schedule(any(Runnable.class), captor.capture());

        assertThat(captor.getValue()).isEqualTo(END_AT.atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    @DisplayName("예약 등록이 실패해도 시작 요청까지 실패시키지 않는다")
    void swallowsScheduleFailure() {

        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class)))
                .thenThrow(new TaskRejectedException("스케줄러가 내려가는 중"));

        assertThatCode(() -> auctionCloseScheduler.on(itemStarted()))
                .as("커밋 뒤에 도는 리스너라, 여기서 던지면 이미 시작된 물품인데 시작 API가 실패한다")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("예약된 작업이 실행되면 그 물품의 마감을 위임한다")
    void delegatesToCloseService() {

        givenScheduledFuture();
        givenClosed();
        auctionCloseScheduler.on(itemStarted());

        scheduledTask().run();

        verify(auctionItemCloseService).closeIfDue(ITEM_ID);
    }

    @Test
    @DisplayName("마감이 실패해도 예외가 스케줄러 스레드로 전파되지 않는다")
    void swallowsFailure() {

        givenScheduledFuture();
        auctionCloseScheduler.on(itemStarted());
        doThrow(new IllegalStateException("DB 연결 끊김"))
                .when(auctionItemCloseService).closeIfDue(ITEM_ID);

        Runnable task = scheduledTask();

        assertThatCode(task::run)
                .as("한 물품의 실패가 같은 스레드에 걸린 다른 예약까지 죽이면 안 된다")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("실행이 시작된 예약의 핸들은 보관하지 않는다")
    void discardsHandleOnRun() {

        givenScheduledFuture();
        givenClosed();
        auctionCloseScheduler.on(itemStarted());
        assertThat(schedules()).containsKey(ITEM_ID);

        scheduledTask().run();

        assertThat(schedules())
                .as("끝난 물품의 핸들이 쌓이면 취소할 수도 없는 채로 메모리에 남는다")
                .isEmpty();
    }

    @Test
    @DisplayName("같은 물품에 다시 예약하면 이전 예약을 취소한다")
    void cancelsPreviousScheduleOnReschedule() {

        ScheduledFuture<?> first = mock(ScheduledFuture.class);
        ScheduledFuture<?> second = mock(ScheduledFuture.class);
        doReturn(first).doReturn(second)
                .when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));

        auctionCloseScheduler.schedule(ITEM_ID, END_AT);
        auctionCloseScheduler.schedule(ITEM_ID, EXTENDED_END_AT);

        // 취소하지 않으면 옛 예약이 살아남아 연장 전 시각에 물품을 닫는다.
        verify(first).cancel(false);
        assertThat(schedules()).containsEntry(ITEM_ID, second);
    }

    @Test
    @DisplayName("Soft Close 연장을 받으면 밀린 마감 시각으로 예약을 갈아 끼운다")
    void reschedulesOnSoftCloseExtended() {

        givenScheduledFuture();

        auctionCloseScheduler.on(SoftCloseExtended.of(
                ROOM_ID, ITEM_ID, "한정판 피규어", 30, EXTENDED_END_AT, END_AT));

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(taskScheduler).schedule(any(Runnable.class), captor.capture());

        assertThat(captor.getValue())
                .isEqualTo(EXTENDED_END_AT.atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    @DisplayName("판매자가 마감을 앞당기면 당겨진 시각으로 예약을 갈아 끼운다")
    void reschedulesOnItemCloseAdvanced() {

        ScheduledFuture<?> first = mock(ScheduledFuture.class);
        ScheduledFuture<?> second = mock(ScheduledFuture.class);
        doReturn(first).doReturn(second)
                .when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));

        auctionCloseScheduler.on(itemStarted());
        auctionCloseScheduler.on(ItemCloseAdvanced.of(
                ROOM_ID, ITEM_ID, "한정판 피규어", 60, ADVANCED_END_AT, STARTED_AT));

        // 취소하지 않으면 옛 예약이 살아남아 물품이 앞당기기 전 시각까지 열려 있게 된다.
        verify(first).cancel(false);
        assertThat(schedules()).containsEntry(ITEM_ID, second);

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), captor.capture());
        assertThat(captor.getAllValues())
                .last()
                .isEqualTo(ADVANCED_END_AT.atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    @DisplayName("아직 마감 시각이 아니라는 답을 받으면 그 시각으로 다시 예약한다")
    void reschedulesWhenNotDueYet() {

        givenScheduledFuture();
        auctionCloseScheduler.on(itemStarted());
        when(auctionItemCloseService.closeIfDue(ITEM_ID)).thenReturn(Optional.of(EXTENDED_END_AT));

        // 캡처를 먼저 해둔다. 실행하면 재예약이 일어나 schedule 호출이 두 번이 된다.
        Runnable task = scheduledTask();
        task.run();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), captor.capture());

        assertThat(captor.getAllValues())
                .as("락을 기다리는 사이 연장이 커밋된 경우다. 닫지 말고 새 시각에 다시 걸어야 한다")
                .last()
                .isEqualTo(EXTENDED_END_AT.atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    @DisplayName("낙찰로 마감되면 남은 예약을 취소한다")
    void cancelsScheduleOnItemEnded() {

        ScheduledFuture<?> schedule = givenScheduledFuture();
        auctionCloseScheduler.on(itemStarted());

        auctionCloseScheduler.on(ItemEnded.of(ROOM_ID, ITEM_ID, "한정판 피규어", 12_000L, "한기", END_AT));

        verify(schedule).cancel(false);
        assertThat(schedules())
                .as("방 종료로 먼저 닫힌 물품의 예약이 남으면 방 길이만큼 핸들이 메모리에 머문다")
                .isEmpty();
    }

    @Test
    @DisplayName("유찰로 마감돼도 남은 예약을 취소한다")
    void cancelsScheduleOnItemPassed() {

        ScheduledFuture<?> schedule = givenScheduledFuture();
        auctionCloseScheduler.on(itemStarted());

        auctionCloseScheduler.on(ItemPassed.of(ROOM_ID, ITEM_ID, "한정판 피규어", END_AT));

        verify(schedule).cancel(false);
        assertThat(schedules()).isEmpty();
    }

    @Test
    @DisplayName("예약이 스스로 실행돼 마감한 경우에는 취소할 대상이 없다")
    void cancelIsNoopAfterRun() {

        givenScheduledFuture();
        givenClosed();
        auctionCloseScheduler.on(itemStarted());
        scheduledTask().run();

        assertThatCode(() -> auctionCloseScheduler.on(
                ItemPassed.of(ROOM_ID, ITEM_ID, "한정판 피규어", END_AT)))
                .as("실행이 시작된 예약은 이미 핸들을 버린 뒤라 취소가 겉돌아야 한다")
                .doesNotThrowAnyException();
    }

    private ItemStarted itemStarted() {
        return ItemStarted.of(ROOM_ID, ITEM_ID, "한정판 피규어", STARTED_AT, END_AT);
    }

    /**
     * 반환값을 비워 두면 {@code compute}가 항목을 넣지 않고 지워 버려 핸들 보관을 검증할 수 없다.
     */
    private ScheduledFuture<?> givenScheduledFuture() {
        ScheduledFuture<?> schedule = mock(ScheduledFuture.class);
        // when().thenReturn()은 schedule()의 반환 타입이 ScheduledFuture<?>라 와일드카드 캡처가 어긋난다.
        doReturn(schedule).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
        return schedule;
    }

    /** 마감 시각이 지나 실제로 닫히는 경우. 재예약할 시각이 없다는 뜻으로 빈 값을 돌려준다. */
    private void givenClosed() {
        when(auctionItemCloseService.closeIfDue(ITEM_ID)).thenReturn(Optional.empty());
    }

    /** 실제 시각을 기다리지 않고 예약된 작업만 꺼내 직접 실행한다. */
    private Runnable scheduledTask() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(captor.capture(), any(Instant.class));
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private Map<Long, ScheduledFuture<?>> schedules() {
        return (Map<Long, ScheduledFuture<?>>)
                ReflectionTestUtils.getField(auctionCloseScheduler, "schedules");
    }
}
