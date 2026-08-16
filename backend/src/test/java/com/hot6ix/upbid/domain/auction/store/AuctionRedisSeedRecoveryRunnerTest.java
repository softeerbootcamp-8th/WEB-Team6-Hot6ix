package com.hot6ix.upbid.domain.auction.store;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuctionRedisSeedRecoveryRunnerTest {

    @Mock
    private AuctionItemRepository auctionItemRepository;

    @Mock
    private AuctionRedisInitializer auctionRedisInitializer;

    @InjectMocks
    private AuctionRedisSeedRecoveryRunner runner;

    @Test
    @DisplayName("애플리케이션 기동 시 진행 중인 모든 물품의 Redis Seed를 복구한다")
    void restoresEveryInProgressItemOnStartup() {

        when(auctionItemRepository.findIdsByStatus(AuctionItemStatus.IN_PROGRESS))
                .thenReturn(List.of(101L, 102L));

        runner.restoreSeeds();

        verify(auctionItemRepository).findIdsByStatus(AuctionItemStatus.IN_PROGRESS);
        verify(auctionRedisInitializer).initialize(101L);
        verify(auctionRedisInitializer).initialize(102L);
    }

    @Test
    @DisplayName("한 물품의 Seed 복구가 실패해도 다음 물품은 계속 복구한다")
    void continuesAfterOneItemFails() {

        when(auctionItemRepository.findIdsByStatus(AuctionItemStatus.IN_PROGRESS))
                .thenReturn(List.of(101L, 102L));
        doThrow(new IllegalStateException("redis unavailable"))
                .when(auctionRedisInitializer).initialize(101L);

        assertThatCode(() -> runner.resyncSeeds()).doesNotThrowAnyException();

        verify(auctionRedisInitializer).initialize(102L);
    }

    @Test
    @DisplayName("복구 대상 조회가 실패해도 앱 기동과 다음 주기 실행을 막지 않는다")
    void swallowsTargetLookupFailure() {

        when(auctionItemRepository.findIdsByStatus(AuctionItemStatus.IN_PROGRESS))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatCode(() -> runner.restoreSeeds()).doesNotThrowAnyException();
    }
}
