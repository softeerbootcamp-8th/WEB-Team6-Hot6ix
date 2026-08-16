package com.hot6ix.upbid.domain.auction.scheduler;

import com.hot6ix.upbid.global.redis.RedisDelayQueue;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 마감 예약을 <b>Redis 에</b> 담는 {@link AuctionCloseScheduler} 구현.
 *
 * <p>예약이 프로세스 밖에 있어서 서버가 몇 대든 같은 목록을 본다. 서버가 내려가도 예약이
 * 남고, 어느 서버에서 연장을 받든 같은 줄이 고쳐진다.
 *
 * <p><b>여기서 마감이 실행되지는 않는다.</b> 예약을 꺼내 실제로 닫는 것은
 * {@link AuctionClosePoller}다.
 */
@Component
@RequiredArgsConstructor
public class RedisAuctionCloseScheduler implements AuctionCloseScheduler {

    private final RedisDelayQueue closeDelayQueue;

    @Override
    public void schedule(Long auctionItemId, LocalDateTime endAt) {
        closeDelayQueue.schedule(auctionItemId, endAt);
    }

    @Override
    public void cancel(Long auctionItemId) {
        closeDelayQueue.cancel(auctionItemId);
    }
}
