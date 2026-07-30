package com.hot6ix.upbid.global.event.message;

import com.hot6ix.upbid.global.event.DomainEvent;
import com.hot6ix.upbid.global.event.payload.BidPlaced;
import com.hot6ix.upbid.global.event.payload.ItemClosingSoon;
import com.hot6ix.upbid.global.event.payload.ItemEnded;
import com.hot6ix.upbid.global.event.payload.ItemStarted;
import com.hot6ix.upbid.global.event.payload.RoomClosed;
import com.hot6ix.upbid.global.event.payload.RoomEntered;
import com.hot6ix.upbid.global.event.payload.SoftCloseExtended;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EventMessages {

    public static String of(DomainEvent event) {
        return switch (event) {
            case RoomEntered e -> "새 참여자 입장 · 누적 " + e.participantCount() + "명";
            case RoomClosed e -> e.roomTitle() + " 경매방이 종료되었습니다";
            case ItemStarted e -> e.itemName() + " 경매가 시작되었습니다";
            case ItemEnded e -> e.itemName() + " 낙찰 확정 · " + won(e.finalPrice())
                    + " (" + e.winnerNickname() + "님)";
            case ItemClosingSoon e -> e.itemName() + " 마감 1분 전";
            case BidPlaced e -> e.bidderNickname() + "님이 " + won(e.bidPrice())
                    + " 입찰 · " + e.itemName();
            case SoftCloseExtended e -> "Soft Close 발동 · " + e.itemName()
                    + " 마감 +" + e.extendSeconds() + "초 연장";
            default -> throw new IllegalArgumentException("지원하지 않는 이벤트: " + event.type());
        };
    }

    private static String won(long value) {
        return String.format("%,d원", value);
    }
}
