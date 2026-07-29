package com.hot6ix.upbid.global.event.listener;

import com.hot6ix.upbid.global.event.DomainEvent;
import com.hot6ix.upbid.global.event.message.EventMessages;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

//발행된 도메인 이벤트를 경매방 이벤트로 SSE로 화면에 뿌리는 리스너
@Slf4j
@Component
public class DomainEventSseListener {

    @EventListener
    public void on(DomainEvent event) {
        log.info("domain event published: roomId={}, message={}",
                event.roomId(), EventMessages.of(event));
    }
}
