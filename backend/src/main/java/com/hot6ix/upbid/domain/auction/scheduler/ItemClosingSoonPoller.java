package com.hot6ix.upbid.domain.auction.scheduler;

import com.hot6ix.upbid.domain.auction.config.AuctionProperties;
import com.hot6ix.upbid.domain.auction.service.ItemClosingSoonService;
import com.hot6ix.upbid.global.redis.DueEntry;
import com.hot6ix.upbid.global.redis.RedisDelayQueue;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 알림 시각이 지난 예약을 Redis 에서 집어 실제로 마감 임박 알림을 내보낸다.
 * {@link AuctionClosePoller}와 같은 자리이며, 다른 점은 <b>실행 풀이 없다</b>는 것 하나다.
 *
 * <p>마감은 한 건에 수백 ms 가 걸려서 집는 스레드와 실행하는 스레드를 갈라 뒀는데, 알림
 * 한 건은 조회 하나와 UPDATE 하나, 이벤트 발행이 전부라 폴링 스레드가 직접 처리한다. 한
 * 배치를 순서대로 처리하는 동안 다음 폴링이 밀리지만, 알림은 몇백 ms 늦어도 화면에서 티가
 * 나지 않아 폴링 주기(500ms)도 마감(100ms)보다 느슨하게 잡았다.
 *
 * <p>같은 물품을 두 서버가 집지 않는 것은 {@link RedisDelayQueue#claimDue}가 맡는다. 그것이
 * 뚫리더라도 <b>{@code notified_at} 이 걸러 준다</b> — 마감이 물품 행 락과 상태 검사로
 * 걸리는 것과 같은 자리다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItemClosingSoonPoller {

    /**
     * <b>필드 이름이 곧 빈 이름이다.</b> {@code RedisDelayQueue} 는 마감용까지 빈이 둘이라
     * 타입만으로는 갈리지 않고, 이 이름이 {@code AuctionSchedulingConfig} 의
     * {@code closingSoonDelayQueue} 와 맞아서 알림 큐가 들어온다.
     */
    private final RedisDelayQueue closingSoonDelayQueue;

    private final ItemClosingSoonService itemClosingSoonService;
    /** 계측은 이 객체가 안다. 이 클래스는 Micrometer 를 모른다. */
    private final ItemClosingSoonMetrics itemClosingSoonMetrics;
    private final AuctionProperties auctionProperties;

    /** 주기가 짧아서 실패할 때마다 찍으면 로그가 분당 수백 줄씩 쌓인다. */
    private final AtomicBoolean claimFailing = new AtomicBoolean();

    /**
     * 밀린 예약 수를 지표로 내보낸다. 게이지는 스크랩할 때 Redis 를 읽어 가는 방식이라
     * 폴링 경로에는 계측이 안 들어간다.
     */
    @PostConstruct
    void bindMetrics() {
        itemClosingSoonMetrics.bindBacklog(this::backlogSize);
    }

    /**
     * Redis 를 못 읽으면 {@code -1} 을 준다. 여기서 예외를 올리면 그 스크랩이 통째로 실패해서
     * <b>다른 지표까지 같이 안 나간다.</b> 0 으로 눕히지 않는 것은 "밀린 게 없다" 와 "못 읽었다"
     * 를 그래프에서 갈라 보기 위해서다.
     */
    private long backlogSize() {
        try {
            return closingSoonDelayQueue.backlogSize(LocalDateTime.now());
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 알릴 물품을 집어 차례로 내보낸다.
     *
     * <p><b>마감처럼 남은 자리를 세지 않는다.</b> 실행 풀이 없어 거절될 곳이 없고, 집은 만큼
     * 이 스레드가 그대로 처리하기 때문이다.
     *
     * <p>예외를 삼키는 것은 스케줄러 스레드에서 예외가 올라가면 이 작업이 아예 멈추기
     * 때문이다. Redis 를 못 읽는 동안은 알림이 안 나가지만 예약은 그대로 남아 있어, 복구되면
     * 밀린 것부터 집힌다.
     */
    @Scheduled(fixedDelayString = "${upbid.auction.closing-soon.poll-interval-ms}")
    public void pollAndNotify() {

        AuctionProperties.ClosingSoon closingSoon = auctionProperties.closingSoon();

        try {
            List<DueEntry> due = closingSoonDelayQueue.claimDue(
                    LocalDateTime.now(), closingSoon.claimBatchSize(), closingSoon.visibilityTimeout());

            if (claimFailing.compareAndSet(true, false)) {
                log.info("마감 임박 알림 예약 조회가 복구됐다");
            }

            due.forEach(this::notifyClosingSoon);
        } catch (Exception e) {
            if (claimFailing.compareAndSet(false, true)) {
                log.error("마감 임박 알림 예약을 못 읽는다. 복구될 때까지 알림이 나가지 않는다", e);
            }
        }
    }

    /**
     * 알림 한 건을 내보내고 결과에 따라 예약을 정리한다. 나갔거나 알릴 물품이 아니면 지우고,
     * 아직 때가 아니면 돌려받은 시각으로 옮기고, <b>실패하면 그대로 둔다.</b>
     *
     * <p>그대로 두면 미뤄 둔 시각에 다시 집혀 재시도된다. 예약을 프로세스 메모리에 두던 때는
     * 실패한 알림이 그냥 사라졌는데, 그게 이번에 없어졌다.
     *
     * <p>예외를 잡아 두는 것은 여기서 올라가면 <b>같은 배치의 나머지 물품이 통째로 안 나가기</b>
     * 때문이다.
     */
    private void notifyClosingSoon(DueEntry entry) {

        Long auctionItemId = entry.id();

        logDelay(entry);

        try {
            itemClosingSoonService.notifyIfDue(auctionItemId)
                    .ifPresentOrElse(
                            rescheduleAt -> closingSoonDelayQueue.schedule(auctionItemId, rescheduleAt),
                            () -> closingSoonDelayQueue.cancel(auctionItemId));
        } catch (Exception e) {
            log.error("마감 임박 알림 실패. 미뤄 둔 시각에 다시 집힌다: itemId={}", auctionItemId, e);
        }
    }

    /**
     * 예약한 시각보다 실제 실행이 얼마나 늦게 시작됐는지 남긴다. 지표로는 안 내보내지만,
     * <b>어느 서버가 이 물품을 집었는지</b>를 남겨야 여러 대로 띄웠을 때 알림이 한 번만
     * 나가는지 확인할 수 있다.
     *
     * <p>이 값에는 폴링 주기가 바닥으로 깔린다. 예약이 스스로 깨어나는 것이 아니라 주기마다
     * 훑어서 집는 방식이라, 한가해도 최대 한 주기만큼은 늦는다.
     */
    private void logDelay(DueEntry entry) {

        long delayMillis = Duration.between(entry.scheduledFor(), LocalDateTime.now()).toMillis();

        log.info("마감 임박 알림 실행: itemId={}, 예정={}, 지연={}ms",
                entry.id(), entry.scheduledFor(), delayMillis);
    }
}
