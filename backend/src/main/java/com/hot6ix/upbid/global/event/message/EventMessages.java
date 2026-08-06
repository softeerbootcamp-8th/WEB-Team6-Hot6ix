package com.hot6ix.upbid.global.event.message;

import com.hot6ix.upbid.global.event.DomainEvent;
import com.hot6ix.upbid.global.event.payload.BidPlaced;
import com.hot6ix.upbid.global.event.payload.ItemCloseAdvanced;
import com.hot6ix.upbid.global.event.payload.ItemClosingSoon;
import com.hot6ix.upbid.global.event.payload.ItemEnded;
import com.hot6ix.upbid.global.event.payload.ItemPassed;
import com.hot6ix.upbid.global.event.payload.ItemStarted;
import com.hot6ix.upbid.global.event.payload.RoomClosed;
import com.hot6ix.upbid.global.event.payload.SoftCloseExtended;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EventMessages {

    /**
     * 도메인 이벤트를 화면 문구로 바꾼다. 화면에 뿌리지 않는 내부 신호는 빈 값이다.
     * {@code ItemEvent}·{@code RoomEvent}가 {@code non-sealed}라 switch를 exhaustive하게
     * 만들 수 없어 {@code default}가 필요하다. 그래서 문구를 빠뜨린 새 이벤트도 조용히
     * 빈 값이 되므로, 이벤트를 추가할 때 여기에 함께 넣어야 한다.
     */
    public static Optional<String> of(DomainEvent event) {
        return switch (event) {
            case RoomClosed e -> Optional.of(e.roomTitle() + " 경매방이 종료되었습니다");
            case ItemStarted e -> Optional.of(e.itemName() + " 경매가 시작되었습니다");
            case ItemEnded e -> Optional.of(e.itemName() + " 낙찰 1순위 확정 · " + won(e.finalPrice())
                    + " (" + e.winnerNickname() + "님)");
            case ItemPassed e -> Optional.of(e.itemName() + " 유찰 · 입찰자가 없습니다");
            case ItemClosingSoon e -> Optional.of(e.itemName() + " 마감 " + remaining(e.remainingSeconds()) + " 전");
            case BidPlaced e -> Optional.of(e.bidderNickname() + "님이 " + won(e.bidPrice())
                    + " 입찰 · " + e.itemName());
            case SoftCloseExtended e -> Optional.of("Soft Close 발동 · " + e.itemName()
                    + " 마감 +" + e.extendSeconds() + "초 연장");
            case ItemCloseAdvanced e -> Optional.of(e.itemName() + " 마감 앞당김 · "
                    + remaining(e.remainingSeconds()) + " 뒤 마감");
            /*
             * ITEM_ADDED·ITEM_REMOVED는 빠뜨린 게 아니라 일부러 비워 둔다. 둘은 화면에
             * "목록을 다시 읽어라"라고 알리는 신호이지 사람이 읽을 사건이 아니다. 문구를
             * 주면 이벤트 피드에 편성 변경이 쌓여, 벌크로 20개를 넣는 순간 입찰·마감 같은
             * 실제 사건이 밀려난다.
             */
            default -> Optional.empty();
        };
    }

    private static String won(long value) {
        return String.format("%,d원", value);
    }

    /**
     * 남은 시간을 사람이 읽는 단위로 바꾼다. 분으로 떨어지면 분으로, 아니면 초로 적는다.
     * 트리거가 60~3600초라 대개 분으로 떨어지고, 90초 같은 값만 초로 남는다.
     *
     * <p>화면에도 같은 규칙이 한 번 더 있다. 이 클래스는 로그 문구만 만들고 화면으로 나가지
     * 않아서 공유할 수 없다.
     */
    private static String remaining(int seconds) {
        return seconds % 60 == 0 ? (seconds / 60) + "분" : seconds + "초";
    }
}
