package com.hot6ix.upbid.global.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class SpringDomainEventPublisherTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 7, 28, 12, 0);

    @Test
    @DisplayName("publish는 이벤트를 ApplicationEventPublisher로 위임한다")
    void publishDelegatesToApplicationEventPublisher() {
        ApplicationEventPublisher delegate = mock(ApplicationEventPublisher.class);
        SpringDomainEventPublisher publisher = new SpringDomainEventPublisher(delegate);
        ItemEnded event = ItemEnded.of(1L, 2L, 5000L, OCCURRED_AT);

        publisher.publish(event);

        verify(delegate).publishEvent(event);
    }
}
