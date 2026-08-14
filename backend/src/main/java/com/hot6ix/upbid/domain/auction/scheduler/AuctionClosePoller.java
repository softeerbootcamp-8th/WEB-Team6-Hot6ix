package com.hot6ix.upbid.domain.auction.scheduler;

import com.hot6ix.upbid.domain.auction.config.AuctionProperties;
import com.hot6ix.upbid.domain.auction.service.AuctionItemCloseService;
import com.hot6ix.upbid.global.redis.DueEntry;
import com.hot6ix.upbid.global.redis.RedisDelayQueue;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 마감 시각이 지난 예약을 Redis 에서 집어 실제로 마감시킨다.
 *
 * <p><b>집는 것과 실행하는 것을 다른 스레드가 한다.</b> 마감 한 건이 수백 ms 씩 걸리는데
 * 폴링 스레드가 직접 실행하면 한 바퀴가 그만큼 길어져 다음 폴링이 통째로 밀린다.
 *
 * <p>같은 물품을 두 서버가 집지 않는 것은 {@link RedisDelayQueue#claimDue}가 맡는다. 그것이
 * 뚫리더라도 마감 자체가 물품 행 락과 상태 검사로 걸러진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionClosePoller {

    private final RedisDelayQueue closeDelayQueue;
    private final AuctionItemCloseService auctionItemCloseService;
    /** 계측은 이 객체가 안다. 이 클래스는 Micrometer 를 모른다. */
    private final AuctionCloseMetrics auctionCloseMetrics;
    private final AuctionProperties auctionProperties;
    /**
     * {@code Executor} 가 아니라 구현 타입으로 받는다. <b>넘기기 전에 대기 줄에 자리가 몇 개
     * 남았는지 물어봐야 하기 때문</b>이다.
     */
    private final ThreadPoolTaskExecutor auctionCloseExecutor;

    /** 주기가 짧아서 실패할 때마다 찍으면 로그가 분당 수백 줄씩 쌓인다. */
    private final AtomicBoolean claimFailing = new AtomicBoolean();

    /**
     * 밀린 예약 수를 지표로 내보낸다. 게이지는 스크랩할 때 Redis 를 읽어 가는 방식이라
     * 폴링 경로에는 계측이 안 들어간다. {@code RoomSseManager} 가 연결 수를 내보내는 것과
     * 같은 방식이다.
     */
    @PostConstruct
    void bindMetrics() {
        auctionCloseMetrics.bindBacklog(this::backlogSize);
    }

    /**
     * Redis 를 못 읽으면 {@code -1} 을 준다. 여기서 예외를 올리면 그 스크랩이 통째로 실패해서
     * <b>다른 지표까지 같이 안 나간다.</b> 0 으로 눕히지 않는 것은 "밀린 게 없다" 와 "못 읽었다"
     * 를 그래프에서 갈라 보기 위해서다.
     */
    private long backlogSize() {
        try {
            return closeDelayQueue.backlogSize(LocalDateTime.now());
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 마감할 물품을 집어 실행 풀로 넘긴다.
     *
     * <p><b>대기 줄에 자리가 남은 만큼만 집는다.</b> 처리할 수 있는 것보다 많이 집으면 넘긴
     * 만큼이 거절당하는데, 거절된 물품은 집히면서 미뤄진 뒤라 <b>가시성 타임아웃이 지나야</b>
     * 다시 집힌다. 그건 죽은 서버를 되살리라고 둔 시간이라 "잠깐 바빴다"에 쓰기엔 너무 길다.
     * 애초에 못 넘길 것을 안 집으면 그 상황이 생기지 않는다.
     *
     * <p>예외를 삼키는 것은 스케줄러 스레드에서 예외가 올라가면 이 작업이 아예 멈추기
     * 때문이다. Redis 를 못 읽는 동안은 물품이 닫히지 않지만 예약은 그대로 남아 있어, 복구되면
     * 밀린 것부터 집힌다.
     */
    @Scheduled(fixedDelayString = "${upbid.auction.close.poll-interval-ms}")
    public void pollAndClose() {

        AuctionProperties.Close close = auctionProperties.close();
        int claimSize = Math.min(close.claimBatchSize(), freeSlots());

        if (claimSize <= 0) {
            return;
        }

        try {
            List<DueEntry> due = closeDelayQueue.claimDue(
                    LocalDateTime.now(), claimSize, close.visibilityTimeout());

            if (claimFailing.compareAndSet(true, false)) {
                log.info("마감 예약 조회가 복구됐다");
            }

            due.forEach(entry -> submit(entry, close.visibilityTimeout()));
        } catch (Exception e) {
            if (claimFailing.compareAndSet(false, true)) {
                log.error("마감 예약을 못 읽는다. 복구될 때까지 마감 시각이 지난 물품이 닫히지 않는다", e);
            }
        }
    }

    /**
     * 지금 실행 풀에 더 넣을 수 있는 개수. <b>대기 줄의 빈자리에 노는 일꾼 수를 더한 값</b>이다.
     * 일꾼이 놀고 있으면 넘긴 작업이 줄에 안 쌓이고 바로 실행되므로 그만큼 더 넣을 수 있다.
     *
     * <p>폴링이 스레드 하나로 도는 동안 자리가 줄어들 일은 없다. 일꾼은 처리를 끝내며 자리를
     * 비우기만 하므로, 여기서 센 값보다 실제 자리가 적어지지 않는다.
     */
    private int freeSlots() {

        ThreadPoolExecutor pool = auctionCloseExecutor.getThreadPoolExecutor();

        int idleWorkers = pool.getMaximumPoolSize() - pool.getActiveCount();

        return pool.getQueue().remainingCapacity() + idleWorkers;
    }

    /**
     * 마감 한 건을 실행 풀로 넘긴다.
     *
     * <p>자리를 보고 집기 때문에 거절은 원칙적으로 안 난다. 그래도 잡아 두는 것은 여기서
     * 예외가 올라가면 <b>같은 배치의 나머지 물품이 통째로 안 넘어가기</b> 때문이다.
     */
    private void submit(DueEntry entry, Duration visibilityTimeout) {
        try {
            auctionCloseExecutor.execute(() -> close(entry));
        } catch (RejectedExecutionException e) {
            log.warn("마감 실행 풀이 가득 차 건너뛴다. {} 뒤에 다시 집힌다: itemId={}",
                    visibilityTimeout, entry.id());
        }
    }

    /**
     * 물품 하나를 마감하고 결과에 따라 예약을 정리한다. 닫혔으면 지우고, 아직 때가 아니면
     * 돌려받은 시각으로 옮기고, <b>실패하면 그대로 둔다.</b>
     *
     * <p>그대로 두면 미뤄 둔 시각에 다시 집혀 재시도된다. 예약을 프로세스 메모리에 두던
     * 때는 실패한 물품이 다음 기동까지 닫히지 않았는데, 그게 이번에 없어졌다.
     *
     * <p>재예약을 조건 없이 덮어쓰는 것은 연장 폭이 미뤄 둔 시간보다 짧을 수 있어서다. 덮어써도
     * 안전한 것은 {@code closeIfDue}가 물품 행 락을 잡고 읽어 연장과 순서가 정해지기 때문이다.
     */
    private void close(DueEntry entry) {

        Long auctionItemId = entry.id();

        logDelay(entry);

        try {
            auctionCloseMetrics
                    .recordCloseDuration(
                            () -> auctionItemCloseService.closeIfDue(auctionItemId),
                            Optional::isPresent)
                    .ifPresentOrElse(
                            rescheduleAt -> closeDelayQueue.schedule(auctionItemId, rescheduleAt),
                            () -> closeDelayQueue.cancel(auctionItemId));
        } catch (Exception e) {
            auctionCloseMetrics.recordFailure(e);
            log.error("물품 마감 실패. 미뤄 둔 시각에 다시 집힌다: itemId={}", auctionItemId, e);
        }
    }

    /**
     * 예약한 시각보다 실제 실행이 얼마나 늦게 시작됐는지 남긴다.
     *
     * <p><b>이 값에는 이제 폴링 주기가 바닥으로 깔린다.</b> 예약이 스스로 깨어나는 것이 아니라
     * 주기마다 훑어서 집는 방식이라, 한가해도 최대 한 주기만큼은 늦는다. 스레드 수를 정할 때
     * 쓰던 값과 같은 기준으로 볼 수 없다(#198).
     */
    private void logDelay(DueEntry entry) {

        long delayMillis = Duration.between(entry.scheduledFor(), LocalDateTime.now()).toMillis();

        log.info("물품 마감 실행: itemId={}, 예정={}, 지연={}ms",
                entry.id(), entry.scheduledFor(), delayMillis);

        auctionCloseMetrics.recordCloseDelay(delayMillis);
    }
}
