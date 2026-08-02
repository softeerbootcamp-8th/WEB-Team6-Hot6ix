package com.hot6ix.upbid.domain.auction.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.service.AuctionItemCloseService;
import com.hot6ix.upbid.global.event.payload.ItemStarted;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuctionCloseSchedulerTest {

    private static final Long ROOM_ID = 10L;
    private static final Long ITEM_ID = 30L;
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 8, 2, 21, 0);
    private static final LocalDateTime END_AT = LocalDateTime.of(2026, 8, 2, 21, 10);

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
    @DisplayName("예약된 작업이 실행되면 그 물품의 마감을 위임한다")
    void delegatesToCloseService() {

        givenScheduledFuture();
        auctionCloseScheduler.on(itemStarted());

        scheduledTask().run();

        verify(auctionItemCloseService).close(ITEM_ID);
    }

    @Test
    @DisplayName("마감이 실패해도 예외가 스케줄러 스레드로 전파되지 않는다")
    void swallowsFailure() {

        givenScheduledFuture();
        auctionCloseScheduler.on(itemStarted());
        doThrow(new IllegalStateException("DB 연결 끊김"))
                .when(auctionItemCloseService).close(ITEM_ID);

        Runnable task = scheduledTask();

        assertThatCode(task::run)
                .as("한 물품의 실패가 같은 스레드에 걸린 다른 예약까지 죽이면 안 된다")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("실행이 시작된 예약의 핸들은 보관하지 않는다")
    void discardsHandleOnRun() {

        givenScheduledFuture();
        auctionCloseScheduler.on(itemStarted());
        assertThat(schedules()).containsKey(ITEM_ID);

        scheduledTask().run();

        assertThat(schedules())
                .as("끝난 물품의 핸들이 쌓이면 취소할 수도 없는 채로 메모리에 남는다")
                .isEmpty();
    }

    private ItemStarted itemStarted() {
        return ItemStarted.of(ROOM_ID, ITEM_ID, "한정판 피규어", STARTED_AT, END_AT);
    }

    /**
     * 반환값을 비워 두면 {@code compute}가 항목을 넣지 않고 지워 버려 핸들 보관을 검증할 수 없다.
     */
    private void givenScheduledFuture() {
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class)))
                .thenReturn(mock(ScheduledFuture.class));
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
