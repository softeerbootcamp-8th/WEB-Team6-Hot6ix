package com.hot6ix.upbid.global.event.listener;

import com.hot6ix.upbid.domain.sse.dto.ItemAddedDto;
import com.hot6ix.upbid.domain.sse.dto.ItemClosingSoonDto;
import com.hot6ix.upbid.domain.sse.dto.ItemRemovedDto;
import com.hot6ix.upbid.domain.sse.dto.ItemStartedDto;
import com.hot6ix.upbid.domain.sse.dto.RoomClosedDto;
import com.hot6ix.upbid.domain.sse.dto.RoomUpdatedDto;
import com.hot6ix.upbid.domain.sse.event.SseEventPublisher;
import com.hot6ix.upbid.global.event.DomainEvent;
import com.hot6ix.upbid.global.event.message.EventMessages;
import com.hot6ix.upbid.global.event.payload.BidPlaced;
import com.hot6ix.upbid.global.event.payload.ItemAdded;
import com.hot6ix.upbid.global.event.payload.ItemCloseAdvanced;
import com.hot6ix.upbid.global.event.payload.ItemClosingSoon;
import com.hot6ix.upbid.global.event.payload.ItemEnded;
import com.hot6ix.upbid.global.event.payload.ItemPassed;
import com.hot6ix.upbid.global.event.payload.ItemRemoved;
import com.hot6ix.upbid.global.event.payload.ItemStarted;
import com.hot6ix.upbid.global.event.payload.RoomClosed;
import com.hot6ix.upbid.global.event.payload.RoomUpdated;
import com.hot6ix.upbid.global.event.payload.SoftCloseExtended;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventSseListener {

    private final SseEventPublisher sseEventPublisher;

    /**
     * 커밋된 도메인 이벤트만 화면으로 내보낸다. Redis-first 이벤트는 이미 즉시 발행됐으므로
     * 여기서는 건너뛰고, 나머지는 롤백된 트랜잭션의 이벤트가 도달하지 않게 한다.
     *
     * <p>{@code fallbackExecution = true}는 트랜잭션 밖에서 발행된 이벤트를 위한 것이다.
     * 기본값이면 그런 이벤트는 리스너에 도달하지 않고 조용히 사라진다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(DomainEvent event) {
        EventMessages.of(event).ifPresent(message ->
                log.info("domain event published: roomId={}, message={}", event.roomId(), message));

        if (isRedisFirstRealtimeEvent(event)) {
            return;
        }

        Object dto = toDto(event);

        if (dto == null) {
            return;
        }

        sseEventPublisher.publish(sseEventName(event), event.roomId(), dto);
    }

    /**
     * 진행 중 경매에서 Redis가 이미 즉시 알린 이벤트다. DomainEvent는 낙찰 후보 생성 등 내부
     * 후속 작업을 위해 남기되, MySQL 커밋 뒤 같은 화면 이벤트를 다시 발행하지 않는다.
     */
    private boolean isRedisFirstRealtimeEvent(DomainEvent event) {
        return event instanceof BidPlaced
                || event instanceof SoftCloseExtended
                || event instanceof ItemCloseAdvanced
                || event instanceof ItemEnded
                || event instanceof ItemPassed;
    }

    private String sseEventName(DomainEvent event) {
        return event.type().name();
    }

    private Object toDto(DomainEvent event) {
        return switch (event) {

            case RoomClosed e -> new RoomClosedDto(e.roomTitle(), e.occurredAt());
            case RoomUpdated e -> new RoomUpdatedDto();
            case ItemAdded e -> new ItemAddedDto(e.addedCount());
            case ItemRemoved e -> new ItemRemovedDto(e.itemId());
            case ItemStarted e -> new ItemStartedDto(e.itemId(), e.itemName(), e.endAt());
            case ItemClosingSoon e ->
                    new ItemClosingSoonDto(e.itemId(), e.itemName(), e.remainingSeconds());
            default -> null;
        };
    }
}
