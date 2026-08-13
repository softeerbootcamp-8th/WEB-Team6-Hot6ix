package com.hot6ix.upbid.domain.auction.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.config.AuctionProperties;
import com.hot6ix.upbid.domain.auction.service.ItemClosingSoonService;
import com.hot6ix.upbid.global.redis.DueEntry;
import com.hot6ix.upbid.global.redis.RedisDelayQueue;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;

/**
 * 집어온 알림 예약을 결과에 따라 어떻게 정리하는지 검증한다. <b>여기가 이 클래스의 알맹이다</b> —
 * 잘못 지우면 알림이 안 나가고, 안 지우면 같은 물품이 계속 다시 집힌다.
 *
 * <p>{@link AuctionClosePollerTest}와 달리 실행 풀을 끼우지 않는다. 알림은 폴링 스레드가 직접
 * 처리해서 넘길 데가 없다.
 */
@ExtendWith(MockitoExtension.class)
class ItemClosingSoonPollerTest {

    private static final Long ITEM_ID = 42L;
    private static final LocalDateTime SCHEDULED_FOR = LocalDateTime.of(2026, 8, 12, 10, 0);
    private static final Duration VISIBILITY = Duration.ofSeconds(10);
    private static final int BATCH = 50;

    @Mock
    private RedisDelayQueue closingSoonDelayQueue;

    @Mock
    private ItemClosingSoonService itemClosingSoonService;

    private SimpleMeterRegistry registry;
    private ItemClosingSoonPoller itemClosingSoonPoller;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        itemClosingSoonPoller = new ItemClosingSoonPoller(
                closingSoonDelayQueue,
                itemClosingSoonService,
                new ItemClosingSoonMetrics(registry),
                new AuctionProperties(3, null, new AuctionProperties.ClosingSoon(BATCH, VISIBILITY)));
    }

    @Test
    @DisplayName("알림이 나간 물품의 예약은 지운다")
    void cancelsScheduleAfterNotifying() {

        givenClaimed(ITEM_ID);
        when(itemClosingSoonService.notifyIfDue(ITEM_ID)).thenReturn(Optional.empty());

        itemClosingSoonPoller.pollAndNotify();

        // 안 지우면 미뤄 둔 시각에 다시 떠올라 한 번 더 집힌다.
        verify(closingSoonDelayQueue).cancelIfDeferred(eq(ITEM_ID), any(), eq(VISIBILITY));
        verify(closingSoonDelayQueue, never()).schedule(anyLong(), any());
    }

    @Test
    @DisplayName("아직 알릴 때가 아니면 돌려받은 시각으로 예약을 옮긴다")
    void reschedulesWhenNotDueYet() {

        LocalDateTime extendedNotifyAt = SCHEDULED_FOR.plusSeconds(30);

        givenClaimed(ITEM_ID);
        when(itemClosingSoonService.notifyIfDue(ITEM_ID)).thenReturn(Optional.of(extendedNotifyAt));

        itemClosingSoonPoller.pollAndNotify();

        // 집는 것과 거의 동시에 연장이 커밋된 경우다. 알리지 말고 새 시각에 다시 걸어야 한다.
        verify(closingSoonDelayQueue).schedule(ITEM_ID, extendedNotifyAt);
        verify(closingSoonDelayQueue, never()).cancelIfDeferred(anyLong(), any(), any());
    }

    @Test
    @DisplayName("알림이 실패하면 예약을 그대로 둔다")
    void keepsScheduleOnFailure() {

        givenClaimed(ITEM_ID);
        when(itemClosingSoonService.notifyIfDue(ITEM_ID))
                .thenThrow(new IllegalStateException("DB 연결 끊김"));

        itemClosingSoonPoller.pollAndNotify();

        // 그대로 두면 미뤄 둔 시각에 다시 집혀 재시도된다.
        verify(closingSoonDelayQueue, never()).cancelIfDeferred(anyLong(), any(), any());
        verify(closingSoonDelayQueue, never()).schedule(anyLong(), any());
    }

    @Test
    @DisplayName("한 물품이 실패해도 같은 배치의 나머지를 계속 처리한다")
    void keepsGoingAfterOneFailure() {

        givenClaimed(ITEM_ID, 43L);
        when(itemClosingSoonService.notifyIfDue(ITEM_ID))
                .thenThrow(new IllegalStateException("DB 연결 끊김"));
        when(itemClosingSoonService.notifyIfDue(43L)).thenReturn(Optional.empty());

        itemClosingSoonPoller.pollAndNotify();

        verify(closingSoonDelayQueue).cancelIfDeferred(eq(43L), any(), eq(VISIBILITY));
    }

    @Test
    @DisplayName("지울 때 집을 때 넘긴 시각을 그대로 준다")
    void cancelsWithTheSameInstantItClaimedWith() {

        givenClaimed(ITEM_ID);
        when(itemClosingSoonService.notifyIfDue(ITEM_ID)).thenReturn(Optional.empty());

        itemClosingSoonPoller.pollAndNotify();

        // 미뤄 둔 시각을 되짚어야 "그사이 아무도 안 건드렸다"를 판정할 수 있다. 다른 값을
        // 주면 판정이 언제나 어긋나 예약이 안 지워지고 같은 알림이 계속 다시 집힌다.
        ArgumentCaptor<LocalDateTime> claimedAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(closingSoonDelayQueue).claimDue(claimedAt.capture(), anyInt(), any());
        verify(closingSoonDelayQueue)
                .cancelIfDeferred(ITEM_ID, claimedAt.getValue(), VISIBILITY);
    }

    @Test
    @DisplayName("설정한 개수와 가시성 타임아웃으로 집는다")
    void claimsWithConfiguredValues() {

        givenClaimed();

        itemClosingSoonPoller.pollAndNotify();

        verify(closingSoonDelayQueue).claimDue(any(), eq(BATCH), eq(VISIBILITY));
    }

    @Test
    @DisplayName("예약을 못 읽어도 폴링이 멈추지 않는다")
    void swallowsClaimFailure() {

        when(closingSoonDelayQueue.claimDue(any(), anyInt(), any()))
                .thenThrow(new QueryTimeoutException("Redis 연결 끊김"));

        assertThatCode(() -> itemClosingSoonPoller.pollAndNotify())
                .as("스케줄러 스레드로 예외가 올라가면 이 작업이 아예 안 돈다")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("밀린 예약 수를 지표로 내보낸다")
    void exposesBacklogGauge() {

        itemClosingSoonPoller.bindMetrics();
        when(closingSoonDelayQueue.backlogSize(any())).thenReturn(3L);

        assertThat(backlogGauge()).isEqualTo(3);
    }

    @Test
    @DisplayName("Redis 를 못 읽으면 밀린 예약 수를 -1 로 내보낸다")
    void reportsMinusOneWhenRedisIsDown() {

        itemClosingSoonPoller.bindMetrics();
        when(closingSoonDelayQueue.backlogSize(any()))
                .thenThrow(new QueryTimeoutException("Redis 연결 끊김"));

        // 0 으로 눕히면 "밀린 게 없다" 와 "못 읽었다" 를 그래프에서 못 가른다.
        assertThat(backlogGauge()).isEqualTo(-1);
    }

    private double backlogGauge() {
        return registry.get("upbid.auction.closing-soon.backlog").gauge().value();
    }

    private void givenClaimed(Long... itemIds) {
        when(closingSoonDelayQueue.claimDue(any(), anyInt(), any()))
                .thenReturn(List.of(itemIds).stream()
                        .map(id -> new DueEntry(id, SCHEDULED_FOR))
                        .toList());
    }
}
