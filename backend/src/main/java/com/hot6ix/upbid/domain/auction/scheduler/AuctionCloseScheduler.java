package com.hot6ix.upbid.domain.auction.scheduler;

import com.hot6ix.upbid.domain.auction.service.AuctionItemCloseService;
import com.hot6ix.upbid.global.event.payload.ItemEnded;
import com.hot6ix.upbid.global.event.payload.ItemPassed;
import com.hot6ix.upbid.global.event.payload.ItemStarted;
import com.hot6ix.upbid.global.event.payload.SoftCloseExtended;
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
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 시작된 물품을 마감 시각에 닫는 예약을 관리한다. 물품 하나당 예약 하나이며, 마감할 물품을
 * 주기적으로 찾아다니지 않는다.
 *
 * <p>예약은 <b>메모리에만</b> 있다. 프로세스가 죽으면 함께 사라지므로 기동 시 복구가 따로
 * 필요하고, 서버가 여러 대면 같은 물품을 여러 번 마감하려 든다. 둘 다 이번 주 범위 밖이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionCloseScheduler {

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
     * 물품 시작이 커밋된 뒤에 마감을 예약한다. 시작 트랜잭션이 롤백되면 이 메서드가 아예
     * 호출되지 않아, 대기 중인 물품에 마감 작업이 도는 일이 없다.
     *
     * <p>{@code endAt}이 이벤트에 실려 있어 물품을 다시 조회하지 않는다.
     *
     * <p>예외를 삼키는 것은 <b>커밋이 끝난 뒤에 도는 리스너</b>이기 때문이다. 여기서 던지면
     * 예외가 커밋한 쪽으로 전파돼, 물품이 이미 진행중으로 저장됐는데 시작 요청은 실패로 보인다.
     * 컨텍스트가 내려가는 중이면 {@code TaskRejectedException}으로 실제로 그렇게 된다.
     * 예약이 없는 물품은 닫히지 않을 뿐이고 그건 시작을 실패시켜 될 일이 아니다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(ItemStarted event) {
        try {
            schedule(event.itemId(), event.endAt());
        } catch (Exception e) {
            log.error("물품 마감 예약 실패: itemId={}, endAt={}", event.itemId(), event.endAt(), e);
        }
    }

    /**
     * 낙찰로 마감된 물품의 남은 예약을 정리한다. 예약 시각이 오기 전에 닫히는 경우가 있다 —
     * 지금은 판매자의 <b>경매방 종료</b>가 그렇다.
     *
     * <p>취소를 이벤트로 받는 이유는 두 가지다. 마감시키는 쪽이 스케줄러를 직접 부르면
     * 그쪽이 스케줄러를 의존하게 되고, 무엇보다 <b>커밋 전에 취소</b>해버려서 마감이 롤백되면
     * 예약만 사라진 물품이 영영 닫히지 않는다. {@code AFTER_COMMIT}이면 실제로 닫힌 물품만
     * 취소된다.
     *
     * <p>예약이 스스로 실행돼 마감한 경우에도 이 리스너가 돌지만, 그때는 {@link #close}가
     * 이미 핸들을 버린 뒤라 취소할 대상이 없어 아무 일도 하지 않는다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(ItemEnded event) {
        cancel(event.itemId());
    }

    /** 유찰로 마감된 물품의 예약을 정리한다. 낙찰({@link #on(ItemEnded)})과 하는 일이 같다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(ItemPassed event) {
        cancel(event.itemId());
    }

    /**
     * Soft Close로 밀린 마감 시각에 맞춰 예약을 다시 건다. {@link #schedule}이 이전 예약을
     * 취소하고 새로 걸어 주므로 여기서 따로 취소하지 않는다.
     *
     * <p>커밋 후에 받는 이유는 {@link #on(ItemEnded)}과 같다. 커밋 전에 예약을 밀어두면 입찰이
     * 롤백됐을 때 마감 시각은 그대로인데 예약만 뒤로 가 있게 된다.
     *
     * <p>예외를 삼키는 것도 같은 이유다. 여기서 던지면 입찰은 이미 저장됐는데 요청은 실패로
     * 보인다. 예약을 못 바꾸면 옛 시각에 마감이 돌지만, 그때 {@code closeIfDue}가 밀린
     * {@code end_at}을 보고 닫지 않고 다시 예약하므로 물품이 일찍 닫히지는 않는다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(SoftCloseExtended event) {
        try {
            schedule(event.itemId(), event.endAt());
        } catch (Exception e) {
            log.error("Soft Close 마감 재예약 실패: itemId={}, endAt={}", event.itemId(), event.endAt(), e);
        }
    }

    /**
     * 물품의 마감 예약을 취소한다. 예약이 없거나 이미 실행이 시작됐으면 아무 일도 하지 않는다.
     *
     * <p>{@code false}는 실행 중인 작업을 중단시키지 말라는 뜻이다. 마감 트랜잭션 도중에
     * 스레드를 인터럽트하면 그 물품이 어중간하게 닫힐 수 있다.
     *
     * @param auctionItemId 예약을 취소할 물품의 ID
     */
    public void cancel(Long auctionItemId) {
        ScheduledFuture<?> schedule = schedules.remove(auctionItemId);
        scheduledEndAts.remove(auctionItemId);

        if (schedule != null) {
            schedule.cancel(false);
        }
    }

    /**
     * 지정한 시각에 물품을 마감하도록 예약한다. 이미 지난 시각을 주면 곧바로 실행된다.
     * <b>이미 걸려 있던 예약은 취소하고 갈아 끼운다</b> — Soft Close 연장과 재예약이 이 동작에
     * 기댄다. 취소하지 않으면 옛 예약이 살아남아 원래 시각에 물품을 닫아버린다.
     *
     * <p>{@code compute}로 넣는 것은 키 단위로 원자적이기 때문이다. 취소와 재등록을 두 번에
     * 나눠 부르면 그 사이에 다른 스레드가 끼어들 수 있고, 즉시 실행되는 경우에는 작업 쪽의
     * 삭제가 이 등록보다 먼저 일어나 핸들이 영영 남는다.
     *
     * @param auctionItemId 마감할 물품의 ID
     * @param endAt         마감 시각. 서버 시간 기준 절대값
     */
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
                    .ifPresent(endAt -> schedule(auctionItemId, endAt));
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
