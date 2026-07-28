package com.hot6ix.upbid.global.event;

public interface DomainEventPublisher {

    void publish(DomainEvent event);
}
