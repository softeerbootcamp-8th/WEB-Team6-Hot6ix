package com.hot6ix.upbid.domain.auction.scheduler;

import com.hot6ix.upbid.domain.auction.service.AuctionItemCloseService;
import com.hot6ix.upbid.global.event.payload.ItemStarted;
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
     * 아직 실행되지 않은 예약의 핸들. 지금은 아무도 읽지 않는다. 2주차 Soft Close 연장이
     * "기존 예약을 취소하고 새 마감 시각으로 다시 건다"라서 그때 취소할 대상을 여기서 찾는다.
     */
    private final Map<Long, ScheduledFuture<?>> schedules = new ConcurrentHashMap<>();

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
     * 지정한 시각에 물품을 마감하도록 예약한다. 이미 지난 시각을 주면 곧바로 실행된다.
     *
     * <p>{@code compute}로 넣는 것은 키 단위로 원자적이기 때문이다. 즉시 실행되는 경우
     * 작업 쪽의 삭제가 이 등록보다 먼저 일어나 핸들이 영영 남을 수 있다.
     *
     * @param auctionItemId 마감할 물품의 ID
     * @param endAt         마감 시각. 서버 시간 기준 절대값
     */
    public void schedule(Long auctionItemId, LocalDateTime endAt) {
        schedules.compute(auctionItemId, (itemId, previous) -> taskScheduler.schedule(
                () -> close(itemId), endAt.atZone(ZoneId.systemDefault()).toInstant()));
    }

    /**
     * 예약된 마감을 실행한다. 실행이 시작된 예약은 더 이상 취소할 수 없으므로 핸들을 먼저 버린다.
     *
     * <p>예외를 삼키는 것은 스케줄러 스레드에서 예외가 올라가면 그 스레드에 걸린 다른 예약까지
     * 함께 죽기 때문이다. <b>재시도는 하지 않는다</b> — 실패한 물품은 다음 서버 기동의 복구가
     * 맡고, 서버가 살아 있는 채로 실패하면 그대로 닫히지 않은 채 남는다.
     */
    private void close(Long auctionItemId) {

        schedules.remove(auctionItemId);

        try {
            auctionItemCloseService.close(auctionItemId);
        } catch (Exception e) {
            log.error("물품 마감 실패: itemId={}", auctionItemId, e);
        }
    }
}
