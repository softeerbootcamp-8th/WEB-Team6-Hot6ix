package com.hot6ix.upbid.global.event.message;

import com.hot6ix.upbid.global.event.DomainEvent;
import com.hot6ix.upbid.global.event.payload.BidPlaced;
import com.hot6ix.upbid.global.event.payload.ItemClosingSoon;
import com.hot6ix.upbid.global.event.payload.ItemEnded;
import com.hot6ix.upbid.global.event.payload.ItemPassed;
import com.hot6ix.upbid.global.event.payload.ItemStarted;
import com.hot6ix.upbid.global.event.payload.RoomClosed;
import com.hot6ix.upbid.global.event.payload.RoomEntered;
import com.hot6ix.upbid.global.event.payload.SoftCloseExtended;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EventMessages {

    /**
     * 도메인 이벤트를 화면에 뿌릴 문구로 바꾼다. 모든 이벤트가 문구를 갖지는 않는다 —
     * {@code WinnerDecided}는 후보 스냅샷을 만들려고 발행하는 내부 신호라 빈 값을 돌려준다.
     *
     * <p>{@code ItemEvent}·{@code RoomEvent}가 {@code non-sealed}라 switch를 exhaustive하게
     * 만들 수 없어 {@code default}가 필요하다. 그래서 문구 넣기를 잊은 새 이벤트도 조용히
     * 빈 값이 된다 — 이벤트를 추가할 때 여기에 함께 넣어야 한다.
     *
     * @param event 문구로 바꿀 도메인 이벤트
     * @return 화면에 띄울 문구. 문구를 띄우지 않는 이벤트면 {@code Optional.empty()}
     */
    public static Optional<String> of(DomainEvent event) {
        return switch (event) {
            case RoomClosed e -> Optional.of(e.roomTitle() + " 경매방이 종료되었습니다");
            case ItemStarted e -> Optional.of(e.itemName() + " 경매가 시작되었습니다");
            case ItemEnded e -> Optional.of(e.itemName() + " 낙찰 확정 · " + won(e.finalPrice())
                    + " (" + e.winnerNickname() + "님)");
            case ItemPassed e -> Optional.of(e.itemName() + " 유찰 · 입찰자가 없습니다");
            case ItemClosingSoon e -> Optional.of(e.itemName() + " 마감 1분 전");
            case BidPlaced e -> Optional.of(e.bidderNickname() + "님이 " + won(e.bidPrice())
                    + " 입찰 · " + e.itemName());
            case SoftCloseExtended e -> Optional.of("Soft Close 발동 · " + e.itemName()
                    + " 마감 +" + e.extendSeconds() + "초 연장");
            default -> Optional.empty();
        };
    }

    private static String won(long value) {
        return String.format("%,d원", value);
    }
}
