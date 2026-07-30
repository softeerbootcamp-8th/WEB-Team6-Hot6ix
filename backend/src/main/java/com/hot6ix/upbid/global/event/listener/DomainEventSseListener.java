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
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

//발행된 도메인 이벤트를 경매방 이벤트로 SSE로 화면에 뿌리는 리스너
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventSseListener {

    private final RoomSseManager roomSseManager;

    @EventListener
    public void on(DomainEvent event) {
        log.info("domain event published: roomId={}, message={}",
                event.roomId(), EventMessages.of(event));

        Object dto = toDto(event);

        if (dto == null) {
            return;
        }

        roomSseManager.sendBroadCast(event.type().name(), event.roomId(), dto);
    }

    private Object toDto(DomainEvent event) {
        return switch (event) {
            // endedTime 소스가 없어 현재 이벤트 payload로는 계산 불가
            case ItemStarted e -> new ItemStartedDto(e.itemName(), null);
            case ItemClosingSoon e -> new ItemClosingSoonDto(e.itemName());
            case BidPlaced e -> new BidPlacedDto(e.itemName(), e.bidPrice(), e.bidderNickname());
            // 연장 후 마감 시각 소스가 없어 현재 이벤트 payload로는 계산 불가
            case SoftCloseExtended e -> new SoftCloseExtendedDto(e.itemName(), e.extendSeconds(), null);
            default -> null;
        };
    }
}
