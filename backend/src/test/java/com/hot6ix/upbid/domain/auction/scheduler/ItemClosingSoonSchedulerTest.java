package com.hot6ix.upbid.domain.auction.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.service.ItemClosingSoonService;
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
class ItemClosingSoonSchedulerTest {

    private static final Long ROOM_ID = 10L;
    private static final Long ITEM_ID = 30L;
    private static final String ITEM_NAME = "한정판 피규어";
    private static final int EXTEND_SECONDS = 60;

    /** 마감 10분 전 시작, 트리거 60초인 물품의 알림 시각. */
    private static final LocalDateTime NOTIFY_AT = LocalDateTime.now().plusMinutes(9);
    /** 연장이 붙어 뒤로 밀린 알림 시각. 연장 폭만큼만 밀린다. */
    private static final LocalDateTime EXTENDED_NOTIFY_AT = NOTIFY_AT.plusSeconds(EXTEND_SECONDS);

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private ItemClosingSoonService itemClosingSoonService;

    @InjectMocks
    private ItemClosingSoonScheduler itemClosingSoonScheduler;

    @Test
    @DisplayName("시작 이벤트를 받으면 알림 시각에 예약한다")
    void schedulesAtNotifyAt() {

        givenScheduledFuture();
        givenNotifyAt(NOTIFY_AT);

        itemClosingSoonScheduler.on(itemStarted());

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(taskScheduler).schedule(any(Runnable.class), captor.capture());

        assertThat(captor.getValue())
                .as("마감 시각이 아니라 연장 구간이 열리는 시각이다")
                .isEqualTo(NOTIFY_AT.atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    @DisplayName("알릴 물품이 아니면 예약하지 않는다")
    void doesNotScheduleWhenNotNotifiable() {

        when(itemClosingSoonService.resolveNotifyAt(ITEM_ID)).thenReturn(Optional.empty());

        itemClosingSoonScheduler.on(itemStarted());

        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    @DisplayName("알림 시각이 이미 지났으면 예약하지 않는다")
    void doesNotScheduleWhenNotifyAtIsPast() {

        givenNotifyAt(LocalDateTime.now().minusMinutes(1));

        itemClosingSoonScheduler.on(softCloseExtended());

        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    @DisplayName("예약 등록이 실패해도 시작 요청까지 실패시키지 않는다")
    void swallowsScheduleFailure() {

        givenNotifyAt(NOTIFY_AT);
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class)))
                .thenThrow(new TaskRejectedException("스케줄러가 내려가는 중"));

        assertThatCode(() -> itemClosingSoonScheduler.on(itemStarted()))
                .as("커밋 뒤에 도는 리스너라, 여기서 던지면 이미 시작된 물품인데 시작 API가 실패한다")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("연장을 받으면 밀린 알림 시각으로 예약을 갈아 끼운다")
    void reschedulesOnSoftCloseExtended() {

        ScheduledFuture<?> first = mock(ScheduledFuture.class);
        ScheduledFuture<?> second = mock(ScheduledFuture.class);
        doReturn(first).doReturn(second)
                .when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
        when(itemClosingSoonService.resolveNotifyAt(ITEM_ID))
                .thenReturn(Optional.of(NOTIFY_AT))
                .thenReturn(Optional.of(EXTENDED_NOTIFY_AT));

        itemClosingSoonScheduler.on(itemStarted());
        itemClosingSoonScheduler.on(softCloseExtended());

        // 취소하지 않으면 옛 예약이 살아남아 연장 전 시각에 "곧 마감"을 알린다.
        verify(first).cancel(false);
        assertThat(schedules()).containsEntry(ITEM_ID, second);

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), captor.capture());
        assertThat(captor.getAllValues())
                .last()
                .isEqualTo(EXTENDED_NOTIFY_AT.atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    @DisplayName("예약된 작업이 실행되면 발행을 위임한다")
    void delegatesToClosingSoonService() {

        givenScheduledFuture();
        givenNotifyAt(NOTIFY_AT);
        when(itemClosingSoonService.notifyIfDue(ITEM_ID)).thenReturn(Optional.empty());
        itemClosingSoonScheduler.on(itemStarted());

        scheduledTask().run();

        verify(itemClosingSoonService).notifyIfDue(ITEM_ID);
    }

    @Test
    @DisplayName("아직 알릴 때가 아니라는 답을 받으면 그 시각으로 다시 예약한다")
    void reschedulesWhenNotDueYet() {

        givenScheduledFuture();
        givenNotifyAt(NOTIFY_AT);
        itemClosingSoonScheduler.on(itemStarted());
        when(itemClosingSoonService.notifyIfDue(ITEM_ID)).thenReturn(Optional.of(EXTENDED_NOTIFY_AT));

        // 캡처를 먼저 해둔다. 실행하면 재예약이 일어나 schedule 호출이 두 번이 된다.
        Runnable task = scheduledTask();
        task.run();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), captor.capture());

        assertThat(captor.getAllValues())
                .as("작업이 깨는 것과 거의 동시에 연장이 커밋된 경우다. 알리지 말고 새 시각에 다시 걸어야 한다")
                .last()
                .isEqualTo(EXTENDED_NOTIFY_AT.atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    @DisplayName("발행이 실패해도 예외가 스케줄러 스레드로 전파되지 않는다")
    void swallowsPublishFailure() {

        givenScheduledFuture();
        givenNotifyAt(NOTIFY_AT);
        itemClosingSoonScheduler.on(itemStarted());
        doThrow(new IllegalStateException("DB 연결 끊김"))
                .when(itemClosingSoonService).notifyIfDue(ITEM_ID);

        Runnable task = scheduledTask();

        assertThatCode(task::run)
                .as("한 물품의 실패가 같은 스레드에 걸린 다른 예약까지 죽이면 안 된다")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("실행이 시작된 예약의 핸들은 보관하지 않는다")
    void discardsHandleOnRun() {

        givenScheduledFuture();
        givenNotifyAt(NOTIFY_AT);
        when(itemClosingSoonService.notifyIfDue(ITEM_ID)).thenReturn(Optional.empty());
        itemClosingSoonScheduler.on(itemStarted());
        assertThat(schedules()).containsKey(ITEM_ID);

        scheduledTask().run();

        assertThat(schedules()).isEmpty();
    }

    @Test
    @DisplayName("낙찰로 마감되면 남은 알림 예약을 취소한다")
    void cancelsScheduleOnItemEnded() {

        ScheduledFuture<?> schedule = givenScheduledFuture();
        givenNotifyAt(NOTIFY_AT);
        itemClosingSoonScheduler.on(itemStarted());

        itemClosingSoonScheduler.on(
                ItemEnded.of(ROOM_ID, ITEM_ID, ITEM_NAME, 12_000L, "한기", LocalDateTime.now()));

        verify(schedule).cancel(false);
        assertThat(schedules())
                .as("방 종료로 먼저 닫힌 물품에 '곧 마감' 알림이 뒤늦게 나가면 안 된다")
                .isEmpty();
    }

    @Test
    @DisplayName("유찰로 마감돼도 남은 알림 예약을 취소한다")
    void cancelsScheduleOnItemPassed() {

        ScheduledFuture<?> schedule = givenScheduledFuture();
        givenNotifyAt(NOTIFY_AT);
        itemClosingSoonScheduler.on(itemStarted());

        itemClosingSoonScheduler.on(ItemPassed.of(ROOM_ID, ITEM_ID, ITEM_NAME, LocalDateTime.now()));

        verify(schedule).cancel(false);
        assertThat(schedules()).isEmpty();
    }

    @Test
    @DisplayName("판매자가 마감을 앞당기면 알림 예약을 취소하고 다시 걸지 않는다")
    void cancelsScheduleOnItemCloseAdvanced() {

        ScheduledFuture<?> schedule = givenScheduledFuture();
        givenNotifyAt(NOTIFY_AT);
        itemClosingSoonScheduler.on(itemStarted());

        itemClosingSoonScheduler.on(itemCloseAdvanced());

        verify(schedule).cancel(false);
        assertThat(schedules())
                .as("남겨두면 옛 마감 기준 예약이 깨어나 실제 남은 시간과 다른 '곧 마감'을 알린다")
                .isEmpty();
        // 앞당긴 순간이 곧 알림 시각이라 새로 걸 예약이 없다.
        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
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

    /**
     * 반환값을 비워 두면 {@code compute}가 항목을 넣지 않고 지워 버려 핸들 보관을 검증할 수 없다.
     */
    private ScheduledFuture<?> givenScheduledFuture() {
        ScheduledFuture<?> schedule = mock(ScheduledFuture.class);
        // when().thenReturn()은 schedule()의 반환 타입이 ScheduledFuture<?>라 와일드카드 캡처가 어긋난다.
        doReturn(schedule).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
        return schedule;
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
                ReflectionTestUtils.getField(itemClosingSoonScheduler, "schedules");
    }
}
