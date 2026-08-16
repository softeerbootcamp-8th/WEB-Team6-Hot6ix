package com.hot6ix.upbid.domain.auction.store;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 시작 커밋 뒤 누락된 진행 중 물품의 Redis 입찰 Seed를 다시 채운다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionRedisSeedRecoveryRunner {

    private final AuctionItemRepository auctionItemRepository;
    private final AuctionRedisInitializer auctionRedisInitializer;

    /** 앱 기동 중 놓친 Seed를 한 번 복구한다. 멱등 실행이라 서버별 기동에는 락을 두지 않는다. */
    @EventListener(ApplicationReadyEvent.class)
    public void restoreSeeds() {
        resync();
    }

    /** 주기적으로 진행 중 물품을 다시 Seed하며 다중 서버 중 하나만 전체 조회를 수행한다. */
    @Scheduled(fixedDelayString = "${upbid.auction.redis.seed-recovery-interval-ms}")
    @SchedulerLock(name = "auction-redis-seed-recovery", lockAtLeastFor = "2s", lockAtMostFor = "5m")
    public void resyncSeeds() {
        resync();
    }

    private void resync() {

        List<Long> itemIds;
        try {
            itemIds = auctionItemRepository.findIdsByStatus(AuctionItemStatus.IN_PROGRESS);
        } catch (Exception e) {
            log.error("Redis 입찰 Seed 복구 대상 조회 실패", e);
            return;
        }

        itemIds.forEach(this::restoreOne);
        log.debug("Redis 입찰 Seed 재동기화: 진행중 {}건", itemIds.size());
    }

    private void restoreOne(long itemId) {

        try {
            auctionRedisInitializer.initialize(itemId);
        } catch (Exception e) {
            log.error("Redis 입찰 Seed 복구 실패: itemId={}", itemId, e);
        }
    }
}
