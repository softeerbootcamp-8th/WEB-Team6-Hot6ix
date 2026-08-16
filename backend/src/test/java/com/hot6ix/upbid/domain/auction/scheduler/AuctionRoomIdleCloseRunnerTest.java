package com.hot6ix.upbid.domain.auction.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.config.AuctionProperties;
import com.hot6ix.upbid.domain.auction.repository.AuctionRoomRepository;
import com.hot6ix.upbid.domain.auction.service.AuctionRoomCloseService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

@ExtendWith(MockitoExtension.class)
class AuctionRoomIdleCloseRunnerTest {

    private static final Duration IDLE_AFTER = Duration.ofHours(12);
    private static final int BATCH_SIZE = 50;

    @Mock
    private AuctionRoomRepository auctionRoomRepository;

    @Mock
    private AuctionRoomCloseService auctionRoomCloseService;

    private final AuctionProperties auctionProperties =
            new AuctionProperties(3, null, null, new AuctionProperties.Room(IDLE_AFTER, BATCH_SIZE));

    private AuctionRoomIdleCloseRunner runner() {
        return new AuctionRoomIdleCloseRunner(
                auctionRoomRepository, auctionRoomCloseService, auctionProperties);
    }

    @Test
    @DisplayName("설정한 시간만큼 지난 방을 찾아 하나씩 닫는다")
    void closesEachTarget() {

        givenTargets(10L, 11L);

        LocalDateTime before = LocalDateTime.now();
        runner().closeIdleRooms();
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<LocalDateTime> idleBefore = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(auctionRoomCloseService).closeIfIdle(eq(10L), idleBefore.capture());
        verify(auctionRoomCloseService).closeIfIdle(eq(11L), any());

        assertThat(idleBefore.getValue())
                .as("기준 시각은 지금으로부터 설정한 시간만큼 앞이다")
                .isBetween(before.minus(IDLE_AFTER), after.minus(IDLE_AFTER));
    }

    @Test
    @DisplayName("조회와 종료가 같은 기준 시각을 쓴다")
    void usesSameThresholdForLookupAndClose() {

        givenTargets(10L);

        runner().closeIdleRooms();

        ArgumentCaptor<LocalDateTime> lookup = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> recheck = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(auctionRoomRepository).findIdleRoomIds(lookup.capture(), any());
        verify(auctionRoomCloseService).closeIfIdle(eq(10L), recheck.capture());

        assertThat(recheck.getValue())
                .as("기준이 방마다 흘러가면 목록에는 있는데 재검사에서 빠지는 방이 생긴다")
                .isEqualTo(lookup.getValue());
    }

    @Test
    @DisplayName("한 번에 가져올 방 수를 설정값으로 제한한다")
    void limitsBatchSize() {

        givenTargets();

        runner().closeIdleRooms();

        verify(auctionRoomRepository).findIdleRoomIds(any(), eq(Limit.of(BATCH_SIZE)));
    }

    @Test
    @DisplayName("대상이 없으면 아무것도 닫지 않는다")
    void doesNothingWithoutTarget() {

        givenTargets();

        runner().closeIdleRooms();

        verify(auctionRoomCloseService, never()).closeIfIdle(anyLong(), any());
    }

    @Test
    @DisplayName("방 하나가 실패해도 나머지는 계속 닫는다")
    void keepsGoingAfterFailure() {

        givenTargets(10L, 11L);
        when(auctionRoomCloseService.closeIfIdle(eq(10L), any()))
                .thenThrow(new IllegalStateException("행 락 대기 시간 초과"));

        assertThatCode(() -> runner().closeIdleRooms()).doesNotThrowAnyException();

        verify(auctionRoomCloseService)
                .closeIfIdle(eq(11L), any());
    }

    @Test
    @DisplayName("대상 조회가 실패해도 스케줄러를 멈추지 않는다")
    void swallowsLookupFailure() {

        when(auctionRoomRepository.findIdleRoomIds(any(), any()))
                .thenThrow(new IllegalStateException("DB 연결 끊김"));

        assertThatCode(() -> runner().closeIdleRooms())
                .as("스케줄러 스레드로 예외가 올라가면 이 작업이 다시는 안 돈다")
                .doesNotThrowAnyException();

        verify(auctionRoomCloseService, never()).closeIfIdle(anyLong(), any());
    }

    private void givenTargets(Long... auctionRoomIds) {
        when(auctionRoomRepository.findIdleRoomIds(any(), any())).thenReturn(List.of(auctionRoomIds));
    }
}
