package com.hot6ix.upbid.global.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hot6ix.upbid.global.event.payload.ItemEnded;
import com.hot6ix.upbid.global.event.publisher.SpringDomainEventPublisher;
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
        ItemEnded event = ItemEnded.of(1L, 2L, "상품", 5000L, "철수", OCCURRED_AT);

        publisher.publish(event);

        verify(delegate).publishEvent(event);
    }
}
