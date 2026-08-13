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

    @Mock
    private AuctionItemRepository auctionItemRepository;

    @Mock
    private AuctionCloseScheduler auctionCloseScheduler;

    @InjectMocks
    private AuctionRecoveryRunner auctionRecoveryRunner;

    @Test
    @DisplayName("진행 중인 물품의 마감 예약을 마감 시각 그대로 다시 건다")
    void reschedulesInProgressItems() {

        givenTargets(
                new InProgressAuctionItemProjection(30L, PAST_END_AT),
                new InProgressAuctionItemProjection(31L, FUTURE_END_AT));

        auctionRecoveryRunner.restoreCloseSchedules();

        verify(auctionCloseScheduler).scheduleIfAbsent(30L, PAST_END_AT);
        verify(auctionCloseScheduler).scheduleIfAbsent(31L, FUTURE_END_AT);
    }

    @Test
    @DisplayName("이미 지난 마감 시각도 그대로 넘긴다")
    void passesPastEndAtAsIs() {

        givenTargets(new InProgressAuctionItemProjection(30L, PAST_END_AT));

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

    private void givenTargets(InProgressAuctionItemProjection... targets) {
        when(auctionItemRepository.findScheduleTargets(AuctionItemStatus.IN_PROGRESS))
                .thenReturn(List.of(targets));
    }
}
