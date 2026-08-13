package com.hot6ix.upbid.domain.auction.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.InProgressAuctionItemProjection;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuctionRecoveryRunnerTest {

    private static final LocalDateTime PAST_END_AT = LocalDateTime.of(2026, 8, 2, 21, 10);
    private static final LocalDateTime FUTURE_END_AT = LocalDateTime.of(2099, 8, 2, 21, 10);

    private static final int TRIGGER_SECONDS = 60;
    /** {@link #PAST_END_AT} 물품의 알림 시각. 마감 60초 전이다. */
    private static final LocalDateTime PAST_NOTIFY_AT = PAST_END_AT.minusSeconds(TRIGGER_SECONDS);
    private static final LocalDateTime FUTURE_NOTIFY_AT = FUTURE_END_AT.minusSeconds(TRIGGER_SECONDS);

    @Mock
    private AuctionItemRepository auctionItemRepository;

    @Mock
    private AuctionCloseScheduler auctionCloseScheduler;

    @Mock
    private ItemClosingSoonScheduler itemClosingSoonScheduler;

    @InjectMocks
    private AuctionRecoveryRunner auctionRecoveryRunner;

    @Test
    @DisplayName("진행 중인 물품의 마감 예약을 마감 시각 그대로 다시 건다")
    void reschedulesInProgressItems() {

        givenTargets(notNotified(30L, PAST_END_AT), notNotified(31L, FUTURE_END_AT));

        auctionRecoveryRunner.restoreCloseSchedules();

        verify(auctionCloseScheduler).scheduleIfAbsent(30L, PAST_END_AT);
        verify(auctionCloseScheduler).scheduleIfAbsent(31L, FUTURE_END_AT);
    }

    @Test
    @DisplayName("이미 지난 마감 시각도 그대로 넘긴다")
    void passesPastEndAtAsIs() {

        givenTargets(notNotified(30L, PAST_END_AT));

        auctionRecoveryRunner.restoreCloseSchedules();

        // 여기서 유예를 두면 서버가 꺼져 있던 동안의 입찰이라는 있을 수 없는 상태를 다뤄야 한다.
        verify(auctionCloseScheduler).scheduleIfAbsent(30L, PAST_END_AT);
    }

    @Test
    @DisplayName("진행 중인 물품만 복구 대상으로 조회한다")
    void looksUpInProgressOnly() {

        givenTargets();

        auctionRecoveryRunner.restoreCloseSchedules();

        // 대기 중인 물품에 예약을 걸면 시작하지도 않은 경매가 유찰로 닫힌다.
        verify(auctionItemRepository).findScheduleTargets(AuctionItemStatus.IN_PROGRESS);
        verify(auctionCloseScheduler, never()).scheduleIfAbsent(any(), any());
        verify(itemClosingSoonScheduler, never()).scheduleIfAbsent(any(), any());
    }

    @Test
    @DisplayName("복구가 실패해도 기동을 막지 않는다")
    void swallowsFailure() {

        when(auctionItemRepository.findScheduleTargets(AuctionItemStatus.IN_PROGRESS))
                .thenThrow(new IllegalStateException("DB 연결 끊김"));

        assertThatCode(() -> auctionRecoveryRunner.restoreCloseSchedules())
                .as("물품 몇 개가 안 닫히는 것보다 서버가 아예 경매를 못 받는 쪽이 나쁘다")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("같은 조회로 마감 임박 알림 예약도 함께 채운다")
    void restoresClosingSoonSchedules() {

        givenTargets(notNotified(31L, FUTURE_END_AT));

        auctionRecoveryRunner.restoreCloseSchedules();

        verify(auctionItemRepository).findScheduleTargets(AuctionItemStatus.IN_PROGRESS);
        verify(itemClosingSoonScheduler)
                .scheduleIfAbsent(31L, FUTURE_NOTIFY_AT);
    }

    @Test
    @DisplayName("알림 시각이 이미 지났어도 넣는다")
    void schedulesPastNotifyAt() {

        givenTargets(notNotified(30L, PAST_END_AT));

        auctionRecoveryRunner.restoreCloseSchedules();

        // 재배포하는 동안 알림 시각이 지나간 경우다. 마감까지 트리거 초 안쪽이라 알림 내용이
        // 여전히 맞으므로 늦게라도 내보낸다.
        verify(itemClosingSoonScheduler).scheduleIfAbsent(30L, PAST_NOTIFY_AT);
    }

    @Test
    @DisplayName("이미 알린 물품은 알림 예약을 넣지 않는다")
    void skipsAlreadyNotifiedItem() {

        givenTargets(new InProgressAuctionItemProjection(
                30L, PAST_END_AT, TRIGGER_SECONDS, PAST_NOTIFY_AT));

        auctionRecoveryRunner.restoreCloseSchedules();

        // 넣으면 폴러가 집어서 DB 를 읽고 notified_at 에 막혀 버리는 한 바퀴가 주기마다 돈다.
        verify(itemClosingSoonScheduler, never()).scheduleIfAbsent(any(), any());
        verify(auctionCloseScheduler).scheduleIfAbsent(30L, PAST_END_AT);
    }

    @Test
    @DisplayName("연장으로 알림 시각이 밀렸으면 이미 알린 물품도 다시 넣는다")
    void reschedulesAfterSoftCloseExtension() {

        LocalDateTime extendedEndAt = PAST_END_AT.plusSeconds(30);

        givenTargets(new InProgressAuctionItemProjection(
                30L, extendedEndAt, TRIGGER_SECONDS, PAST_NOTIFY_AT));

        auctionRecoveryRunner.restoreCloseSchedules();

        // 연장 구간을 벗어났다 다시 들어오는 경우라 한 번 더 알려야 한다.
        verify(itemClosingSoonScheduler)
                .scheduleIfAbsent(30L, extendedEndAt.minusSeconds(TRIGGER_SECONDS));
    }

    @Test
    @DisplayName("연장 설정이 없는 방의 물품은 알림 예약을 넣지 않는다")
    void skipsItemWithoutSoftCloseTrigger() {

        givenTargets(new InProgressAuctionItemProjection(30L, FUTURE_END_AT, null, null));

        auctionRecoveryRunner.restoreCloseSchedules();

        // 알림 시각을 계산할 기준이 없다. 마감은 트리거와 무관하므로 그대로 건다.
        verify(itemClosingSoonScheduler, never()).scheduleIfAbsent(any(), any());
        verify(auctionCloseScheduler).scheduleIfAbsent(30L, FUTURE_END_AT);
    }

    private InProgressAuctionItemProjection notNotified(Long itemId, LocalDateTime endAt) {
        return new InProgressAuctionItemProjection(itemId, endAt, TRIGGER_SECONDS, null);
    }

    private void givenTargets(InProgressAuctionItemProjection... targets) {
        when(auctionItemRepository.findScheduleTargets(AuctionItemStatus.IN_PROGRESS))
                .thenReturn(List.of(targets));
    }
}
