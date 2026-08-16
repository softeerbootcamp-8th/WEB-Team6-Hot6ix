package com.hot6ix.upbid.domain.sse.test;

import com.hot6ix.upbid.domain.sse.dto.BidPlacedDto;
import com.hot6ix.upbid.domain.sse.dto.ItemEndedDto;
import com.hot6ix.upbid.domain.sse.dto.SoftCloseExtendedDto;
import com.hot6ix.upbid.domain.sse.event.SseEventPublisher;
import com.hot6ix.upbid.global.event.EventType;
import com.hot6ix.upbid.global.event.payload.ItemClosingSoon;
import com.hot6ix.upbid.global.event.payload.ItemStarted;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Hidden
@RestController
@Profile("local")
@RequestMapping("/test/events/{roomId}")
@RequiredArgsConstructor
public class TestEventController {

    private final ApplicationEventPublisher eventPublisher;
    private final SseEventPublisher sseEventPublisher;

    @PostMapping("/item-started")
    public void fireItemStarted(
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "1") Long itemId,
            @RequestParam(defaultValue = "한정판 조던 스니커즈") String itemName,
            @RequestParam(defaultValue = "5") Integer durationMinutes
    ) {
        LocalDateTime now = LocalDateTime.now();
        eventPublisher.publishEvent(
                ItemStarted.of(roomId, itemId, itemName, now, now.plusMinutes(durationMinutes))
        );
    }

    @PostMapping("/item-closing-soon")
    public void fireItemClosingSoon(
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "1") Long itemId,
            @RequestParam(defaultValue = "한정판 조던 스니커즈") String itemName,
            @RequestParam(defaultValue = "60") int remainingSeconds
    ) {
        eventPublisher.publishEvent(
                ItemClosingSoon.of(roomId, itemId, itemName, remainingSeconds, LocalDateTime.now())
        );
    }

    @PostMapping("/bid-placed")
    public void fireBidPlaced(
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "1") Long itemId,
            @RequestParam(defaultValue = "한정판 조던 스니커즈") String itemName,
            @RequestParam(defaultValue = "테스터") String bidderNickname,
            @RequestParam(defaultValue = "10000") Long bidPrice
    ) {
        sseEventPublisher.publish(
                EventType.BID_PLACED.name(),
                roomId,
                new BidPlacedDto(itemId, itemName, bidPrice, bidderNickname));
    }

    @PostMapping("/item-ended")
    public void fireItemEnded(
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "1") Long itemId,
            @RequestParam(defaultValue = "한정판 조던 스니커즈") String itemName,
            @RequestParam(defaultValue = "85000") Long finalPrice,
            @RequestParam(defaultValue = "테스터") String winnerNickname
    ) {
        sseEventPublisher.publish(
                EventType.ITEM_ENDED.name(),
                roomId,
                new ItemEndedDto(itemId, itemName, finalPrice, winnerNickname));
    }

    @PostMapping("/soft-close-extended")
    public void fireSoftCloseExtended(
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "1") Long itemId,
            @RequestParam(defaultValue = "한정판 조던 스니커즈") String itemName,
            @RequestParam(defaultValue = "60") int extendSeconds
    ) {
        LocalDateTime now = LocalDateTime.now();

        sseEventPublisher.publish(
                EventType.SOFT_CLOSE_EXTENDED.name(),
                roomId,
                new SoftCloseExtendedDto(itemId, itemName, extendSeconds,
                        now.plusSeconds(extendSeconds)));
    }
}
