package com.hot6ix.upbid.domain.sse.test;

import com.hot6ix.upbid.global.event.payload.BidPlaced;
import com.hot6ix.upbid.global.event.payload.ItemClosingSoon;
import com.hot6ix.upbid.global.event.payload.ItemStarted;
import com.hot6ix.upbid.global.event.payload.SoftCloseExtended;
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
            @RequestParam(defaultValue = "한정판 조던 스니커즈") String itemName
    ) {
        eventPublisher.publishEvent(
                ItemClosingSoon.of(roomId, itemId, itemName, LocalDateTime.now())
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
        eventPublisher.publishEvent(
                BidPlaced.of(roomId, itemId, itemName, bidderNickname, bidPrice, LocalDateTime.now())
        );
    }

    @PostMapping("/soft-close-extended")
    public void fireSoftCloseExtended(
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "1") Long itemId,
            @RequestParam(defaultValue = "한정판 조던 스니커즈") String itemName,
            @RequestParam(defaultValue = "60") int extendSeconds
    ) {
        eventPublisher.publishEvent(
                SoftCloseExtended.of(roomId, itemId, itemName, extendSeconds, LocalDateTime.now())
        );
    }
}
