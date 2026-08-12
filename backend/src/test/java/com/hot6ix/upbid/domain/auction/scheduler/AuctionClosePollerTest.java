package com.hot6ix.upbid.domain.auction.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.config.AuctionProperties;
import com.hot6ix.upbid.domain.auction.service.AuctionItemCloseService;
import com.hot6ix.upbid.global.redis.DueEntry;
import com.hot6ix.upbid.global.redis.RedisDelayQueue;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 집어온 예약을 결과에 따라 어떻게 정리하는지 검증한다. <b>여기가 이 클래스의 알맹이다</b> —
 * 잘못 지우면 마감이 안 되고, 안 지우면 같은 물품이 계속 다시 집힌다.
 *
 * <p>실행 풀은 같은 스레드에서 바로 돌리는 것으로 바꿔 끼운다. 스레드를 실제로 띄우면 검증이
 * 타이밍에 걸린다.
 */
@ExtendWith(MockitoExtension.class)
class AuctionClosePollerTest {

    private static final Long ITEM_ID = 42L;
    private static final LocalDateTime SCHEDULED_FOR = LocalDateTime.of(2026, 8, 12, 10, 0);
    private static final Duration VISIBILITY = Duration.ofSeconds(30);
    private static final int BATCH = 50;

    @Mock
    private RedisDelayQueue closeDelayQueue;

    @Mock
    private AuctionItemCloseService auctionItemCloseService;

    private AuctionClosePoller auctionClosePoller;

    @BeforeEach
    void setUp() {
        AuctionProperties properties =
                new AuctionProperties(3, new AuctionProperties.Close(BATCH, VISIBILITY, 4));

        // 넘긴 작업을 부른 스레드에서 그대로 실행한다.
        Executor sameThreadExecutor = Runnable::run;

        auctionClosePoller = new AuctionClosePoller(
                closeDelayQueue,
                auctionItemCloseService,
                new AuctionCloseMetrics(new SimpleMeterRegistry()),
                properties,
                sameThreadExecutor);
    }

    @Test
    @DisplayName("마감된 물품의 예약은 지운다")
    void cancelsScheduleAfterClosing() {
        givenClaimed(ITEM_ID);
        when(auctionItemCloseService.closeIfDue(ITEM_ID)).thenReturn(Optional.empty());

        auctionClosePoller.pollAndClose();

        verify(closeDelayQueue).cancel(ITEM_ID);
        verify(closeDelayQueue, never()).schedule(anyLong(), any());
    }

    @Test
    @DisplayName("아직 마감 시각이 아니면 돌려받은 시각으로 예약을 옮긴다")
    void reschedulesWhenNotDueYet() {
        LocalDateTime extendedEndAt = SCHEDULED_FOR.plusSeconds(10);

        givenClaimed(ITEM_ID);
        when(auctionItemCloseService.closeIfDue(ITEM_ID)).thenReturn(Optional.of(extendedEndAt));

        auctionClosePoller.pollAndClose();

        verify(closeDelayQueue).schedule(ITEM_ID, extendedEndAt);
        verify(closeDelayQueue, never()).cancel(anyLong());
    }

    @Test
    @DisplayName("마감이 예외로 끝나면 예약을 건드리지 않아 미뤄 둔 시각에 다시 집힌다")
    void keepsScheduleWhenCloseFails() {
        givenClaimed(ITEM_ID);
        when(auctionItemCloseService.closeIfDue(ITEM_ID)).thenThrow(new IllegalStateException("의도된 실패"));

        auctionClosePoller.pollAndClose();

        // 지우지도 옮기지도 않아야 미뤄 둔 시각에 다시 떠오른다.
        verify(closeDelayQueue, never()).cancel(anyLong());
        verify(closeDelayQueue, never()).schedule(anyLong(), any());
    }

    @Test
    @DisplayName("설정한 개수와 미뤄 둘 시간으로 집어온다")
    void claimsWithConfiguredValues() {
        givenClaimed();

        auctionClosePoller.pollAndClose();

        verify(closeDelayQueue).claimDue(any(LocalDateTime.class), eq(BATCH), eq(VISIBILITY));
    }

    @Test
    @DisplayName("집어온 예약이 없으면 마감을 부르지 않는다")
    void doesNothingWhenNothingClaimed() {
        givenClaimed();

        auctionClosePoller.pollAndClose();

        verify(auctionItemCloseService, never()).closeIfDue(anyLong());
    }

    @Test
    @DisplayName("Redis 를 못 읽어도 예외가 스케줄러 스레드로 올라가지 않는다")
    void swallowsClaimFailure() {
        when(closeDelayQueue.claimDue(any(), anyInt(), any()))
                .thenThrow(new IllegalStateException("Redis 접속 실패"));

        // 여기서 던지면 @Scheduled 작업이 통째로 멈춘다.
        assertThatCode(() -> auctionClosePoller.pollAndClose()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("실행 풀이 가득 차면 건너뛰고 예약을 건드리지 않는다")
    void skipsWhenExecutorRejects() {
        AuctionProperties properties =
                new AuctionProperties(3, new AuctionProperties.Close(BATCH, VISIBILITY, 4));

        Executor rejectingExecutor = task -> {
            throw new RejectedExecutionException("풀이 가득 참");
        };

        AuctionClosePoller poller = new AuctionClosePoller(
                closeDelayQueue,
                auctionItemCloseService,
                new AuctionCloseMetrics(new SimpleMeterRegistry()),
                properties,
                rejectingExecutor);

        givenClaimed(ITEM_ID);

        assertThatCode(poller::pollAndClose).doesNotThrowAnyException();

        verify(auctionItemCloseService, never()).closeIfDue(anyLong());
        verify(closeDelayQueue, never()).cancel(anyLong());
    }

    private void givenClaimed(Long... itemIds) {
        List<DueEntry> claimed = List.of(itemIds).stream()
                .map(itemId -> new DueEntry(itemId, SCHEDULED_FOR))
                .toList();

        when(closeDelayQueue.claimDue(any(), anyInt(), any())).thenReturn(claimed);
    }
}
