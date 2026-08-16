package com.hot6ix.upbid.domain.auction.store;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hot6ix.upbid.global.event.payload.ItemStarted;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuctionRedisSeedListenerTest {

    @Test
    @DisplayName("시작 커밋 후 Redis 준비가 실패해도 이미 성공한 시작 요청으로 예외를 되돌리지 않는다")
    void seedFailureDoesNotEscapeAfterCommitListener() {

        AuctionRedisInitializer initializer = mock(AuctionRedisInitializer.class);
        doThrow(new IllegalStateException("redis unavailable"))
                .when(initializer).initialize(101L);
        AuctionRedisSeedListener listener = new AuctionRedisSeedListener(initializer);
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 13, 20, 0);
        ItemStarted event = ItemStarted.of(202L, 101L, "테스트 물품", startedAt, startedAt.plusMinutes(10));

        assertThatCode(() -> listener.on(event)).doesNotThrowAnyException();
        verify(initializer).initialize(101L);
    }
}
