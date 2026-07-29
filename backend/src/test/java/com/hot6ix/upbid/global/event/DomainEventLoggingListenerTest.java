package com.hot6ix.upbid.global.event;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DomainEventLoggingListenerTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 7, 28, 12, 0);

    @Test
    @DisplayName("on은 이벤트를 예외 없이 처리한다")
    void onHandlesEventWithoutException() {
        DomainEventLoggingListener listener = new DomainEventLoggingListener();
        ItemEnded event = ItemEnded.of(1L, 2L, 5000L, OCCURRED_AT);

        assertThatCode(() -> listener.on(event)).doesNotThrowAnyException();
    }
}
