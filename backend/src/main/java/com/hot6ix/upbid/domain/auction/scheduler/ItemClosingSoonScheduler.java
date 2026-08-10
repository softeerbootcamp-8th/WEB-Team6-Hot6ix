package com.hot6ix.upbid.domain.auction.scheduler;

import com.hot6ix.upbid.domain.auction.service.ItemClosingSoonService;
import com.hot6ix.upbid.global.event.payload.ItemCloseAdvanced;
import com.hot6ix.upbid.global.event.payload.ItemEnded;
import com.hot6ix.upbid.global.event.payload.ItemPassed;
import com.hot6ix.upbid.global.event.payload.ItemStarted;
import com.hot6ix.upbid.global.event.payload.SoftCloseExtended;
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
 * 마감 임박 알림을 예약한다. {@code AuctionCloseScheduler}와 같은 사건에 반응하지만 물품 하나당
 * 예약을 따로 갖는다. 마감은 되돌릴 수 없는 상태 변경이고 알림은 그렇지 않아서, 실패했을 때
 * 해야 할 일과 재시도 사정이 서로 다르다.
 *
 * <p>알림 시각은 이벤트에 실려 오지 않는다. 방마다 다른 {@code soft_close_trigger_seconds}가
 * 필요해서 {@link ItemClosingSoonService}가 계산해 준다.
 *
 * <p>예약은 <b>메모리에만</b> 있다. 프로세스가 죽으면 마감 예약과 함께 사라진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItemClosingSoonScheduler {

    private final TaskScheduler taskScheduler;
    private final ItemClosingSoonService itemClosingSoonService;

    /** 아직 실행되지 않은 알림 예약의 핸들. 마감된 물품의 예약을 취소하고, 연장 시 갈아 끼운다. */
    private final Map<Long, ScheduledFuture<?>> schedules = new ConcurrentHashMap<>();

    /**
     * 물품 시작이 커밋된 뒤에 마감 임박 알림을 예약한다.
     *
     * <p>물품이 진행되는 시간이 트리거보다 짧으면(예: 10분짜리 물품에 트리거 60분) 시작 시점에
     * 이미 알림 시각이 지나 있어 <b>알림이 걸리지 않는다.</b> 그 물품은 전 구간이 연장 구간이라
     * "연장 구간에 들어왔다"고 알릴 순간 자체가 없다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(ItemStarted event) {
        reschedule(event.itemId());
    }

    /**
     * Soft Close로 마감이 밀리면 알림 예약도 함께 민다. 밀지 않으면 "곧 마감"이라고 알려놓고
     * 실제로는 연장된 만큼 더 남은 상태가 된다.
     *
     * <p>연장은 알림 시각을 <b>정확히 연장 폭만큼</b> 앞으로 민다({@code end_at}이 밀리면
     * {@code end_at - trigger}도 같이 밀리기 때문). 그래서 밀린 알림 시각이 아직 미래라면 연장
     * 구간을 벗어났다가 다시 들어온다는 뜻이라 다시 알리고, 이미 지났다면 여전히 같은 구간 안에
     * 있다는 뜻이라 알리지 않는다. 후자에서 알리면 트리거가 연장 폭보다 큰 방에서 연장되는
     * 입찰마다 알림이 나간다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(SoftCloseExtended event) {
        reschedule(event.itemId());
    }

    /**
     * 판매자가 마감을 앞당기면 알림 예약을 <b>취소한다.</b> 다시 걸지 않는다.
     *
     * <p>앞당겨진 마감 시각이 곧 연장 구간이 열리는 순간이라 새 알림 시각은 정확히 지금이고,
     * 이미 지난 시각으로는 예약을 걸 수 없다. 그 사실은 앞당김 이벤트가 남은 초와 함께 화면에
     * 직접 알리므로 알림이 따로 나갈 이유도 없다.
     *
     * <p>취소하지 않으면 <b>이전 마감 기준으로 걸려 있던 예약이 살아남는다.</b> 예를 들어 90초
     * 남은 물품을 트리거 60초인 방에서 앞당기면 알림 예약은 30초 뒤에 걸려 있는데, 그때 물품은
     * 아직 진행 중이라 걸러지지도 않아서 실제로는 30초 남은 물품에 "마감 60초 전"이 나간다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(ItemCloseAdvanced event) {
        cancel(event.itemId());
    }

    /** 낙찰로 마감된 물품의 남은 알림 예약을 정리한다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(ItemEnded event) {
        cancel(event.itemId());
    }

    /** 유찰로 마감된 물품의 알림 예약을 정리한다. 낙찰({@link #on(ItemEnded)})과 하는 일이 같다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(ItemPassed event) {
        cancel(event.itemId());
    }

    /**
     * 물품의 알림 예약을 취소한다. 예약이 없거나 이미 실행이 시작됐으면 아무 일도 하지 않는다.
     *
     * @param auctionItemId 예약을 취소할 물품의 ID
     */
    public void cancel(Long auctionItemId) {
        ScheduledFuture<?> schedule = schedules.remove(auctionItemId);

        if (schedule != null) {
            schedule.cancel(false);
        }
    }

    /**
     * 알림 시각을 다시 계산해 예약을 건다. <b>계산한 시각이 이미 지났으면 아무것도 하지 않는다</b>
     * — 그 물품은 알림 구간 안에 계속 있다는 뜻이라 새로 알릴 사건이 없다.
     *
     * <p>예외를 삼키는 것은 커밋이 끝난 뒤에 도는 리스너이기 때문이다. 여기서 던지면 물품은
     * 이미 시작·연장됐는데 요청은 실패로 보인다. 알림이 안 나가는 것은 그렇게까지 할 일이 아니다.
     */
    private void reschedule(Long auctionItemId) {
        try {
            itemClosingSoonService.resolveNotifyAt(auctionItemId)
                    .filter(notifyAt -> notifyAt.isAfter(LocalDateTime.now()))
                    .ifPresent(notifyAt -> schedule(auctionItemId, notifyAt));
        } catch (Exception e) {
            log.error("마감 임박 알림 예약 실패: itemId={}", auctionItemId, e);
        }
    }

    /**
     * 지정한 시각에 알림이 나가도록 예약한다. <b>이미 걸려 있던 예약은 취소하고 갈아 끼운다.</b>
     * {@code compute}로 넣는 것은 키 단위로 원자적이기 때문이며,
     * {@code AuctionCloseScheduler.schedule}과 같은 이유다.
     */
    private void schedule(Long auctionItemId, LocalDateTime notifyAt) {

        schedules.compute(auctionItemId, (itemId, previous) -> {
            if (previous != null) {
                previous.cancel(false);
            }
            return taskScheduler.schedule(
                    () -> notifyClosingSoon(itemId), notifyAt.atZone(ZoneId.systemDefault()).toInstant());
        });
    }

    /**
     * 예약된 알림을 실행한다. 실행이 시작된 예약은 더 이상 취소할 수 없으므로 핸들을 먼저 버린다.
     *
     * <p>아직 알릴 때가 아니라는 답이 오면(연장이 이 작업과 거의 동시에 커밋된 경우) 알리지 않고
     * 새 시각으로 다시 예약한다. 예외를 삼키는 이유는 {@code AuctionCloseScheduler.close}와 같다.
     */
    private void notifyClosingSoon(Long auctionItemId) {

        schedules.remove(auctionItemId);

        try {
            itemClosingSoonService.notifyIfDue(auctionItemId)
                    .ifPresent(rescheduleAt -> schedule(auctionItemId, rescheduleAt));
        } catch (Exception e) {
            log.error("마감 임박 알림 실패: itemId={}", auctionItemId, e);
        }
    }
}
