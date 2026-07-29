package com.hot6ix.upbid.global.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋 후 발행된 도메인 이벤트를 로그로 남기는 기본 리스너.
 * 각 도메인 패키지는 필요에 따라 자신의 @TransactionalEventListener를 추가로 둔다.
 */
@Slf4j
@Component
public class DomainEventLoggingListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DomainEvent event) {
        log.info("domain event published: channel={}, type={}, eventId={}",
                EventChannels.of(event), event.type(), event.eventId());
    }
}
