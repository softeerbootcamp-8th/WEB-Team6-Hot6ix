package com.hot6ix.upbid.global.event.listener;

import com.hot6ix.upbid.domain.sse.dto.BidPlacedDto;
import com.hot6ix.upbid.domain.sse.dto.ItemClosingSoonDto;
import com.hot6ix.upbid.domain.sse.dto.ItemStartedDto;
import com.hot6ix.upbid.domain.sse.dto.SoftCloseExtendedDto;
import com.hot6ix.upbid.domain.sse.service.RoomSseManager;
import com.hot6ix.upbid.global.event.DomainEvent;
import com.hot6ix.upbid.global.event.message.EventMessages;
import com.hot6ix.upbid.global.event.payload.BidPlaced;
import com.hot6ix.upbid.global.event.payload.ItemClosingSoon;
import com.hot6ix.upbid.global.event.payload.ItemStarted;
import com.hot6ix.upbid.global.event.payload.SoftCloseExtended;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

//발행된 도메인 이벤트를 경매방 이벤트로 SSE로 화면에 뿌리는 리스너
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventSseListener {

    private final RoomSseManager roomSseManager;

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

        Object dto = toDto(event);

        if (dto == null) {
            return;
        }

        roomSseManager.sendBroadCast(event.type().name(), event.roomId(), dto);
    }

    private Object toDto(DomainEvent event) {
        return switch (event) {
            // endedTime 소스가 없어 임시로 현재 시각 + 5분으로 대체
            case ItemStarted e -> new ItemStartedDto(e.itemId(), e.itemName(), e.occurredAt().plusMinutes(5));
            case ItemClosingSoon e -> new ItemClosingSoonDto(e.itemId(), e.itemName());
            case BidPlaced e -> new BidPlacedDto(e.itemId(), e.itemName(), e.bidPrice(), e.bidderNickname());
            // endedTime 소스가 없어 현재 이벤트 payload로는 계산 불가 — payload에 endedTime 추가 필요
            case SoftCloseExtended e -> new SoftCloseExtendedDto(e.itemId(), e.itemName(), e.extendSeconds(), null);
            default -> null;
        };
    }
}
