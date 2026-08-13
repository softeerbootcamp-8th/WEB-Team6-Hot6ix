package com.hot6ix.upbid.domain.auction.scheduler;

import com.hot6ix.upbid.global.redis.RedisDelayQueue;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 마감 임박 알림 예약을 <b>Redis 에</b> 담는 {@link ItemClosingSoonScheduler} 구현.
 * {@link RedisAuctionCloseScheduler}와 같은 클래스를 키만 달리해 쓴다.
 *
 * <p><b>여기서 알림이 발행되지는 않는다.</b> 예약을 꺼내 실제로 알리는 것은
 * {@link ItemClosingSoonPoller}다.
 */
@Component
@RequiredArgsConstructor
public class RedisItemClosingSoonScheduler implements ItemClosingSoonScheduler {

    /**
     * <b>필드 이름이 곧 빈 이름이다.</b> {@code RedisDelayQueue} 는 마감용까지 빈이 둘이라
     * 타입만으로는 갈리지 않고, 이 이름이 {@code AuctionSchedulingConfig} 의
     * {@code closingSoonDelayQueue} 와 맞아서 알림 큐가 들어온다. 바꾸면 애플리케이션이 뜨지
     * 않으므로 큐를 하나 더 늘릴 때도 같은 규칙을 지킨다.
     */
    private final RedisDelayQueue closingSoonDelayQueue;

    @Override
    public void schedule(Long auctionItemId, LocalDateTime notifyAt) {
        closingSoonDelayQueue.schedule(auctionItemId, notifyAt);
    }

    @Override
    public void scheduleIfAbsent(Long auctionItemId, LocalDateTime notifyAt) {
        closingSoonDelayQueue.scheduleIfAbsent(auctionItemId, notifyAt);
    }

    @Override
    public void cancel(Long auctionItemId) {
        closingSoonDelayQueue.cancel(auctionItemId);
    }
}
