package com.hot6ix.upbid.global.event.listener;

import com.hot6ix.upbid.domain.sse.dto.BidPlacedDto;
import com.hot6ix.upbid.domain.sse.dto.ItemAddedDto;
import com.hot6ix.upbid.domain.sse.dto.ItemCloseAdvancedDto;
import com.hot6ix.upbid.domain.sse.dto.ItemClosingSoonDto;
import com.hot6ix.upbid.domain.sse.dto.ItemEndedDto;
import com.hot6ix.upbid.domain.sse.dto.ItemRemovedDto;
import com.hot6ix.upbid.domain.sse.dto.ItemStartedDto;
import com.hot6ix.upbid.domain.sse.dto.RoomClosedDto;
import com.hot6ix.upbid.domain.sse.dto.RoomUpdatedDto;
import com.hot6ix.upbid.domain.sse.dto.SoftCloseExtendedDto;
import com.hot6ix.upbid.domain.sse.service.RoomSseManager;
import com.hot6ix.upbid.global.event.DomainEvent;
import com.hot6ix.upbid.global.event.EventType;
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

        roomSseManager.sendBroadCast(sseEventName(event), event.roomId(), dto);
    }

    /**
     * 화면으로 나갈 이벤트 이름. 대개 {@code EventType} 그대로지만 <b>유찰은 낙찰과 같은
     * {@code ITEM_ENDED}로 내보낸다.</b> 화면 입장에서 둘은 "물품이 닫혔다"는 같은 사건이고,
     * 낙찰자가 있었는지는 payload의 {@code winnerNickname}으로 갈리기 때문이다.
     *
     * <p>서버 안에서는 두 이벤트를 계속 나눠 둔다. 낙찰 후보 생성이 {@code ItemEnded}만
     * 구독하므로, 합치면 입찰자가 없던 물품에도 그 로직이 돈다.
     */
    private String sseEventName(DomainEvent event) {
        if (event instanceof ItemPassed) {
            return EventType.ITEM_ENDED.name();
        }
        return event.type().name();
    }

    private Object toDto(DomainEvent event) {
        return switch (event) {

            case RoomClosed e -> new RoomClosedDto(e.roomTitle(), e.occurredAt());
            // 편성·설정 변경은 "다시 읽어라"는 신호다. 이벤트 피드에는 쌓이지 않는다.
            case RoomUpdated e -> new RoomUpdatedDto();
            case ItemAdded e -> new ItemAddedDto(e.addedCount());
            case ItemRemoved e -> new ItemRemovedDto(e.itemId());
            case ItemStarted e -> new ItemStartedDto(e.itemId(), e.itemName(), e.endAt());
            // remainingSeconds는 방마다 다른 트리거 값이라 화면이 이 값으로 문구를 만든다.
            case ItemClosingSoon e ->
                    new ItemClosingSoonDto(e.itemId(), e.itemName(), e.remainingSeconds());
            case BidPlaced e -> new BidPlacedDto(e.itemId(), e.itemName(), e.bidPrice(), e.bidderNickname());
            // endAt은 연장이 반영된 마감 시각이라 화면이 이 값으로 카운트다운을 다시 맞춘다.
            case SoftCloseExtended e ->
                    new SoftCloseExtendedDto(e.itemId(), e.itemName(), e.extendSeconds(), e.endAt());
            // 앞당김도 화면이 endAt으로 카운트다운을 다시 맞춘다. 방향만 반대다.
            case ItemCloseAdvanced e ->
                    new ItemCloseAdvancedDto(e.itemId(), e.itemName(), e.remainingSeconds(), e.endAt());
            case ItemEnded e -> new ItemEndedDto(e.itemId(), e.itemName(), e.finalPrice(), e.winnerNickname());
            // 유찰도 낙찰과 같은 DTO로 내보낸다. 낙찰가·낙찰자가 없다는 뜻으로 null이 간다.
            case ItemPassed e -> new ItemEndedDto(e.itemId(), e.itemName(), null, null);
            default -> null;
        };
    }
}
