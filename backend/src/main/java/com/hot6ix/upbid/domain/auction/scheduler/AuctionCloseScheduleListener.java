package com.hot6ix.upbid.domain.auction.scheduler;

import com.hot6ix.upbid.global.event.payload.ItemEnded;
import com.hot6ix.upbid.global.event.payload.ItemPassed;
import com.hot6ix.upbid.global.event.payload.ItemStarted;
import com.hot6ix.upbid.global.event.payload.SoftCloseExtended;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 마감 예약을 <b>언제</b> 걸고 취소할지 정한다. 도메인 이벤트를 듣고
 * {@link AuctionCloseScheduler}에 넘기기만 하며, 예약이 어디에 담기는지는 알지 못한다.
 * 이 판단은 예약 저장소가 바뀌어도 그대로라서 구현과 분리해 두었다.
 *
 * <p>네 이벤트를 모두 <b>커밋 후에</b> 받는다. 커밋 전에 예약을 건드리면 트랜잭션이 롤백됐을
 * 때 실제 상태와 예약이 어긋난다 — 시작이 롤백됐는데 마감 예약만 남거나, 입찰이 롤백됐는데
 * 마감 시각은 그대로인 채 예약만 뒤로 밀린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionCloseScheduleListener {

    private final AuctionCloseScheduler auctionCloseScheduler;

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
            auctionCloseScheduler.schedule(event.itemId(), event.endAt());
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
     * <p>예약이 스스로 실행돼 마감한 경우에도 이 리스너가 돌지만, 그때는 스케줄러가 이미
     * 핸들을 버린 뒤라 취소할 대상이 없어 아무 일도 하지 않는다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(ItemEnded event) {
        auctionCloseScheduler.cancel(event.itemId());
    }

    /** 유찰로 마감된 물품의 예약을 정리한다. 낙찰({@link #on(ItemEnded)})과 하는 일이 같다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(ItemPassed event) {
        auctionCloseScheduler.cancel(event.itemId());
    }

    /**
     * Soft Close로 밀린 마감 시각에 맞춰 예약을 다시 건다. 스케줄러가 이전 예약을 취소하고
     * 새로 걸어 주므로 여기서 따로 취소하지 않는다.
     *
     * <p>예외를 삼키는 것은 {@link #on(ItemStarted)}과 같은 이유다. 여기서 던지면 입찰은 이미
     * 저장됐는데 요청은 실패로 보인다. 예약을 못 바꾸면 옛 시각에 마감이 돌지만, 그때
     * {@code closeIfDue}가 밀린 {@code end_at}을 보고 닫지 않고 새 시각을 돌려주므로 거기서
     * 다시 걸린다. 물품이 연장 전 시각에 닫히지는 않는다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(SoftCloseExtended event) {
        try {
            auctionCloseScheduler.schedule(event.itemId(), event.endAt());
        } catch (Exception e) {
            log.error("Soft Close 마감 재예약 실패: itemId={}, endAt={}", event.itemId(), event.endAt(), e);
        }
    }
}
