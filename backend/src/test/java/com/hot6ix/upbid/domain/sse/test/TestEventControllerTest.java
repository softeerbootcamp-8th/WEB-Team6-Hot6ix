package com.hot6ix.upbid.domain.sse.test;

import static org.mockito.Mockito.verify;

import com.hot6ix.upbid.domain.sse.dto.BidPlacedDto;
import com.hot6ix.upbid.domain.sse.event.SseEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class TestEventControllerTest {

    @Test
    void bidPlacedEndpointPublishesDirectlyToSseAfterDomainEventFiltering() {
        ApplicationEventPublisher applicationEventPublisher =
                org.mockito.Mockito.mock(ApplicationEventPublisher.class);
        SseEventPublisher sseEventPublisher = org.mockito.Mockito.mock(SseEventPublisher.class);
        TestEventController controller = new TestEventController(
                applicationEventPublisher, sseEventPublisher);

        controller.fireBidPlaced(10L, 20L, "한정판 피규어", "한기", 12_000L);

        verify(sseEventPublisher).publish(
                "BID_PLACED",
                10L,
                new BidPlacedDto(20L, "한정판 피규어", 12_000L, "한기"));
    }
}
