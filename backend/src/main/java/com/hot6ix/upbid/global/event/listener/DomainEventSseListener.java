package com.hot6ix.upbid.global.event.listener;

import com.hot6ix.upbid.global.event.DomainEvent;
import com.hot6ix.upbid.global.event.message.EventMessages;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

//발행된 도메인 이벤트를 경매방 이벤트로 SSE로 화면에 뿌리는 리스너
@Slf4j
@Component
public class DomainEventSseListener {

    /**
     * 커밋된 도메인 이벤트만 화면으로 내보낸다. 롤백된 트랜잭션의 이벤트는 도달하지 않는다.
     *
     * <p>{@code fallbackExecution = true}는 트랜잭션 밖에서 발행된 이벤트를 위한 것이다.
     * 기본값이면 그런 이벤트는 리스너에 도달하지 않고 조용히 사라진다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(DomainEvent event) {
        EventMessages.of(event).ifPresent(message ->
                log.info("domain event published: roomId={}, message={}", event.roomId(), message));
    }
}
