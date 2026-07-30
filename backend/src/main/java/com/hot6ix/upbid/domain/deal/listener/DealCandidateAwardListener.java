package com.hot6ix.upbid.domain.deal.listener;

import com.hot6ix.upbid.domain.deal.service.DealCandidateService;
import com.hot6ix.upbid.global.event.payload.ItemEnded;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

//물품 마감 이벤트를 받아 낙찰 후보 스냅샷을 만드는 리스너
@Slf4j
@Component
@RequiredArgsConstructor
public class DealCandidateAwardListener {

    private final DealCandidateService dealCandidateService;

    /**
     * 마감이 커밋된 뒤에만 후보를 만든다. 롤백된 마감으로 낙찰자가 생기면 안 된다.
     * 예외를 삼키는 것은 이미 커밋된 마감을 되돌릴 수 없는데 예외가 전파되면 성공한 마감이
     * 실패로 보이기 때문이다. 재시도는 없으므로 실패하면 로그를 보고 수동 복구한다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(ItemEnded event) {
        try {
            dealCandidateService.award(event);
        } catch (Exception e) {
            log.error("낙찰 후보 생성 실패: itemId={}, eventId={}", event.itemId(), event.eventId(), e);
        }
    }
}
