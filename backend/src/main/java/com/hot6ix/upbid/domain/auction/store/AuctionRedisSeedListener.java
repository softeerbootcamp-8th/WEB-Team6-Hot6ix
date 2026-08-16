package com.hot6ix.upbid.domain.auction.store;

import com.hot6ix.upbid.global.event.payload.ItemStarted;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 물품 시작 커밋 뒤 Redis 입찰 상태 준비를 시작한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionRedisSeedListener {

    private final AuctionRedisInitializer auctionRedisInitializer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(ItemStarted event) {
        try {
            auctionRedisInitializer.initialize(event.itemId());
        } catch (Exception e) {
            // 시작 DB 커밋은 이미 끝났다. 여기서 예외를 되돌리면 물품은 진행 중인데
            // 클라이언트만 시작 실패를 받아 같은 요청을 재시도하게 된다.
            log.error("Redis 입찰 상태 준비 실패: itemId={}", event.itemId(), e);
        }
    }
}
