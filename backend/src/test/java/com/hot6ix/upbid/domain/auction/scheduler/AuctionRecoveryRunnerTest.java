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
    /** 알림 시각보다 앞선 시작 시각. 대부분의 물품이 이 관계다. */
    private static final LocalDateTime STARTED_AT = PAST_END_AT.minusMinutes(10);

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

        verify(auctionCloseScheduler).schedule(30L, PAST_END_AT);
        verify(auctionCloseScheduler).schedule(31L, FUTURE_END_AT);
    }

    @Test
    @DisplayName("이미 예약이 걸려 있어도 마감 시각을 DB 값으로 덮어쓴다")
    void overwritesExistingCloseSchedule() {

        // 앞당기기 재예약이 실패하면 큐에는 원래 마감이 남고 DB 에는 앞당긴 시각이 남는다.
        // 없을 때만 넣으면 그 줄을 못 고쳐서 물품이 원래 마감까지 열려 있었다 (#327).
        LocalDateTime advancedEndAt = PAST_END_AT.plusSeconds(TRIGGER_SECONDS);

        givenTargets(new InProgressAuctionItemProjection(
                30L, advancedEndAt, STARTED_AT, TRIGGER_SECONDS, PAST_END_AT));

        auctionRecoveryRunner.restoreCloseSchedules();

        verify(auctionCloseScheduler).schedule(30L, advancedEndAt);
    }

    @Test
    @DisplayName("이미 지난 마감 시각도 그대로 넘긴다")
    void passesPastEndAtAsIs() {

        givenTargets(notNotified(30L, PAST_END_AT));

        auctionRecoveryRunner.restoreCloseSchedules();

        // 여기서 유예를 두면 서버가 꺼져 있던 동안의 입찰이라는 있을 수 없는 상태를 다뤄야 한다.
        verify(auctionCloseScheduler).schedule(30L, PAST_END_AT);
    }

    @Test
    @DisplayName("진행 중인 물품만 복구 대상으로 조회한다")
    void looksUpInProgressOnly() {

        givenTargets();

        auctionRecoveryRunner.restoreCloseSchedules();

        // 대기 중인 물품에 예약을 걸면 시작하지도 않은 경매가 유찰로 닫힌다.
        verify(auctionItemRepository).findScheduleTargets(AuctionItemStatus.IN_PROGRESS);
        verify(auctionCloseScheduler, never()).schedule(any(), any());
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
                30L, PAST_END_AT, STARTED_AT, TRIGGER_SECONDS, PAST_NOTIFY_AT));

        auctionRecoveryRunner.restoreCloseSchedules();

        // 넣으면 폴러가 집어서 DB 를 읽고 notified_at 에 막혀 버리는 한 바퀴가 주기마다 돈다.
        verify(itemClosingSoonScheduler, never()).scheduleIfAbsent(any(), any());
        verify(auctionCloseScheduler).schedule(30L, PAST_END_AT);
    }

    @Test
    @DisplayName("연장으로 알림 시각이 밀렸으면 이미 알린 물품도 다시 넣는다")
    void reschedulesAfterSoftCloseExtension() {

        LocalDateTime extendedEndAt = PAST_END_AT.plusSeconds(30);

        givenTargets(new InProgressAuctionItemProjection(
                30L, extendedEndAt, STARTED_AT, TRIGGER_SECONDS, PAST_NOTIFY_AT));

        auctionRecoveryRunner.restoreCloseSchedules();

        // 연장 구간을 벗어났다 다시 들어오는 경우라 한 번 더 알려야 한다.
        verify(itemClosingSoonScheduler)
                .scheduleIfAbsent(30L, extendedEndAt.minusSeconds(TRIGGER_SECONDS));
    }

    @Test
    @DisplayName("연장 설정이 없는 방의 물품은 알림 예약을 넣지 않는다")
    void skipsItemWithoutSoftCloseTrigger() {

        givenTargets(new InProgressAuctionItemProjection(30L, FUTURE_END_AT, STARTED_AT, null, null));

        auctionRecoveryRunner.restoreCloseSchedules();

        // 알림 시각을 계산할 기준이 없다. 마감은 트리거와 무관하므로 그대로 건다.
        verify(itemClosingSoonScheduler, never()).scheduleIfAbsent(any(), any());
        verify(auctionCloseScheduler).schedule(30L, FUTURE_END_AT);
    }

    @Test
    @DisplayName("판매자가 앞당겨 취소된 알림 예약을 되살리지 않는다")
    void doesNotReviveScheduleCancelledByCloseEarly() {

        // 트리거 60초인 방에서 앞당기면 end_at 은 앞당긴 순간 + 60초가 되고, 알림 시각
        // (end_at - 60초)은 곧 앞당긴 순간이다. closeEarly 가 그 시각을 notifiedAt 에 찍는다.
        LocalDateTime advancedAt = PAST_END_AT;
        LocalDateTime advancedEndAt = advancedAt.plusSeconds(TRIGGER_SECONDS);

        givenTargets(new InProgressAuctionItemProjection(
                30L, advancedEndAt, STARTED_AT, TRIGGER_SECONDS, advancedAt));

        auctionRecoveryRunner.restoreCloseSchedules();

        // 되살리면 리스너가 ItemCloseAdvanced 에서 일부러 취소한 것이 무효가 되고, 트리거만큼
        // 남은 물품에 "마감 60초 전"이 뒤늦게 나간다. 마감 예약은 그대로 걸어야 한다.
        verify(itemClosingSoonScheduler, never()).scheduleIfAbsent(any(), any());
        verify(auctionCloseScheduler).schedule(30L, advancedEndAt);
    }

    @Test
    @DisplayName("앞당긴 뒤 연장으로 알림 시각이 밀리면 다시 넣는다")
    void reschedulesWhenExtendedAfterCloseEarly() {

        // 앞당긴 물품에 입찰이 들어와 마감이 30초 밀린 상태다. 알림 시각도 함께 밀려서
        // closeEarly 가 찍어둔 시각보다 뒤가 된다.
        LocalDateTime advancedAt = PAST_END_AT;
        LocalDateTime extendedEndAt = advancedAt.plusSeconds(TRIGGER_SECONDS).plusSeconds(30);

        givenTargets(new InProgressAuctionItemProjection(
                30L, extendedEndAt, STARTED_AT, TRIGGER_SECONDS, advancedAt));

        auctionRecoveryRunner.restoreCloseSchedules();

        verify(itemClosingSoonScheduler)
                .scheduleIfAbsent(30L, extendedEndAt.minusSeconds(TRIGGER_SECONDS));
    }

    @Test
    @DisplayName("진행 시간이 트리거보다 짧은 물품에는 알림 예약을 넣지 않는다")
    void skipsItemWhoseNotifyAtPrecedesStart() {

        // 10분짜리 물품인데 방 트리거가 60분이면 시작 시점에 이미 알림 시각이 지나 있다.
        // 전 구간이 연장 구간이라 리스너도 예약을 걸지 않는 물품이다.
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 2, 21, 0);
        LocalDateTime endAt = startedAt.plusMinutes(10);

        givenTargets(new InProgressAuctionItemProjection(30L, endAt, startedAt, 3600, null));

        auctionRecoveryRunner.restoreCloseSchedules();

        // 알리면 "마감 60분 전"이 나가는데 실제로는 10분도 안 남았다.
        verify(itemClosingSoonScheduler, never()).scheduleIfAbsent(any(), any());
        verify(auctionCloseScheduler).schedule(30L, endAt);
    }

    private InProgressAuctionItemProjection notNotified(Long itemId, LocalDateTime endAt) {
        return new InProgressAuctionItemProjection(itemId, endAt, STARTED_AT, TRIGGER_SECONDS, null);
    }

    private void givenTargets(InProgressAuctionItemProjection... targets) {
        when(auctionItemRepository.findScheduleTargets(AuctionItemStatus.IN_PROGRESS))
                .thenReturn(List.of(targets));
    }
}
