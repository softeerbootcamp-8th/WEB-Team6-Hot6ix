package com.hot6ix.upbid.global.event.publisher;

import com.hot6ix.upbid.global.event.DomainEvent;

public interface DomainEventPublisher {

    void publish(DomainEvent event);
}
