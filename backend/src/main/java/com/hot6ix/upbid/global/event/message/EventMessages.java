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
     * 도메인 이벤트를 화면에 뿌릴 문구로 바꾼다.
     *
     * <p>모든 이벤트가 문구를 갖지는 않는다. {@code WinnerDecided}는 낙찰 후보 스냅샷을
     * 만들려고 발행하는 내부 신호이지 경매방에 띄울 알림이 아니다. 그래서 반환형이
     * {@code Optional}이고, 문구가 없는 이벤트는 <b>예외가 아니라 빈 값</b>이다.
     * 이전에는 {@code default}에서 예외를 던져서, 문구가 없는 이벤트를 발행하는 것만으로
     * 발행 측 트랜잭션이 깨졌다.
     *
     * <p>{@code ItemEvent}·{@code RoomEvent}가 {@code non-sealed}라 switch를 exhaustive하게
     * 만들 수 없어 {@code default}가 필요하다. 그래서 <b>카탈로그에 문구를 넣는 것을 잊은
     * 이벤트도 조용히 빈 값이 된다</b> — 새 이벤트를 추가할 때 여기에 함께 넣어야 한다.
     *
     * @param event 문구로 바꿀 도메인 이벤트
     * @return 화면에 띄울 문구. 문구를 띄우지 않는 이벤트면 {@code Optional.empty()}
     */
    public static Optional<String> of(DomainEvent event) {
        return switch (event) {
            case RoomEntered e -> Optional.of("새 참여자 입장 · 누적 " + e.participantCount() + "명");
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
