package com.hot6ix.upbid.domain.auction.scheduler;

import com.hot6ix.upbid.domain.auction.service.AuctionItemCloseService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * 마감 예약을 <b>프로세스 메모리에</b> 담는 {@link AuctionCloseScheduler} 구현.
 *
 * <p>메모리에만 있어서 프로세스가 죽으면 예약이 함께 사라진다. 그래서 기동 시 DB를 보고
 * 다시 거는 {@link AuctionRecoveryRunner}가 짝으로 붙어 있다. 서버가 여러 대면 각자
 * 예약을 걸어 같은 물품을 여러 번 마감하려 드는데, 그건 아직 해결하지 않았다. 마감 자체는
 * 물품 행 락과 상태 검사로 걸러지므로 중복 실행이 데이터를 깨지는 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InMemoryAuctionCloseScheduler implements AuctionCloseScheduler {

    private final TaskScheduler taskScheduler;
    private final AuctionItemCloseService auctionItemCloseService;

    /**
     * 아직 실행되지 않은 예약의 핸들. 마감된 물품의 예약을 취소하는 데 쓰고, Soft Close 연장이
     * 예약을 새 마감 시각으로 갈아 끼울 때 취소할 대상을 찾는 데도 쓴다.
     */
    private final Map<Long, ScheduledFuture<?>> schedules = new ConcurrentHashMap<>();

    /** 예약한 마감 시각. 실제 실행이 얼마나 늦었는지 재는 기준이며, 로그 외에는 쓰지 않는다. */
    private final Map<Long, LocalDateTime> scheduledEndAts = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     *
     * <p>{@code compute}로 넣는 것은 키 단위로 원자적이기 때문이다. 취소와 재등록을 두 번에
     * 나눠 부르면 그 사이에 다른 스레드가 끼어들 수 있고, 즉시 실행되는 경우에는 작업 쪽의
     * 삭제가 이 등록보다 먼저 일어나 핸들이 영영 남는다.
     */
    @Override
    public void schedule(Long auctionItemId, LocalDateTime endAt) {

        scheduledEndAts.put(auctionItemId, endAt);

        schedules.compute(auctionItemId, (itemId, previous) -> {
            if (previous != null) {
                previous.cancel(false);
            }
            return taskScheduler.schedule(
                    () -> close(itemId), endAt.atZone(ZoneId.systemDefault()).toInstant());
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code false}는 실행 중인 작업을 중단시키지 말라는 뜻이다. 마감 트랜잭션 도중에
     * 스레드를 인터럽트하면 그 물품이 어중간하게 닫힐 수 있다.
     */
    @Override
    public void cancel(Long auctionItemId) {
        ScheduledFuture<?> schedule = schedules.remove(auctionItemId);
        scheduledEndAts.remove(auctionItemId);

        if (schedule != null) {
            schedule.cancel(false);
        }
    }

    /**
     * 예약된 마감을 실행한다. 실행이 시작된 예약은 더 이상 취소할 수 없으므로 핸들을 먼저 버린다.
     *
     * <p>마감 시각이 아직 안 됐다는 답이 오면(Soft Close 연장이 락을 기다리는 사이에 커밋된
     * 경우) 닫지 않고 새 시각으로 다시 예약한다.
     *
     * <p>예외를 삼키는 것은 스케줄러 스레드에서 예외가 올라가면 그 스레드에 걸린 다른 예약까지
     * 함께 죽기 때문이다. <b>재시도는 하지 않는다</b> — 실패한 물품은 다음 서버 기동의 복구가
     * 맡고, 서버가 살아 있는 채로 실패하면 그대로 닫히지 않은 채 남는다.
     */
    private void close(Long auctionItemId) {

        schedules.remove(auctionItemId);
        LocalDateTime scheduledFor = scheduledEndAts.remove(auctionItemId);

        try {
            logDelay(auctionItemId, scheduledFor);

            auctionItemCloseService.closeIfDue(auctionItemId)
                    .ifPresent(rescheduleAt -> schedule(auctionItemId, rescheduleAt));
        } catch (Exception e) {
            log.error("물품 마감 실패: itemId={}", auctionItemId, e);
        }
    }

    /**
     * 예약한 시각보다 실제 실행이 얼마나 늦었는지 남긴다. 스케줄러 스레드가 모자라거나 마감이
     * 행 락을 기다리면 늦어지는데, 지금은 늦는지조차 알 수 없어 스레드 수를 정할 근거가 없다.
     *
     * <p>측정만 하고 아무것도 조정하지 않는다. 이 로그로 분포를 뽑아 스레드 수와 전용 스케줄러
     * 분리 여부를 정하는 것은 별도 작업이다(이슈 #198).
     */
    private void logDelay(Long auctionItemId, LocalDateTime scheduledFor) {

        if (scheduledFor == null) {
            return;
        }

        long delayMillis = Duration.between(scheduledFor, LocalDateTime.now()).toMillis();

        log.info("물품 마감 실행: itemId={}, 예정={}, 지연={}ms", auctionItemId, scheduledFor, delayMillis);
    }
}
