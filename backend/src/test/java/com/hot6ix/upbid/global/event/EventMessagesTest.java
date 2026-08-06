package com.hot6ix.upbid.global.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.global.event.message.EventMessages;
import com.hot6ix.upbid.global.event.payload.BidPlaced;
import com.hot6ix.upbid.global.event.payload.DealRightAssigned;
import com.hot6ix.upbid.global.event.payload.ItemClosingSoon;
import com.hot6ix.upbid.global.event.payload.ItemEnded;
import com.hot6ix.upbid.global.event.payload.ItemPassed;
import com.hot6ix.upbid.global.event.payload.ItemStarted;
import com.hot6ix.upbid.global.event.payload.RoomClosed;
import com.hot6ix.upbid.global.event.payload.RoomEntered;
import com.hot6ix.upbid.global.event.payload.SoftCloseExtended;
import com.hot6ix.upbid.global.event.payload.WinnerDecided;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EventMessagesTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 7, 28, 12, 0);

    @Test
    @DisplayName("RoomClosed는 방 종료 문구를 만든다")
    void roomClosed() {
        assertThat(EventMessages.of(RoomClosed.of(1L, "여름 경매", OCCURRED_AT)))
                .contains("여름 경매 경매방이 종료되었습니다");
    }

    @Test
    @DisplayName("ItemStarted는 경매 시작 문구를 만든다")
    void itemStarted() {
        assertThat(EventMessages.of(
                ItemStarted.of(1L, 2L, "한정판 조던", OCCURRED_AT, OCCURRED_AT.plusMinutes(30))))
                .contains("한정판 조던 경매가 시작되었습니다");
    }

    @Test
    @DisplayName("ItemEnded는 1순위 확정 문구와 금액 포맷을 만든다")
    void itemEnded() {
        assertThat(EventMessages.of(ItemEnded.of(1L, 2L, "한정판 조던", 45000L, "철수", OCCURRED_AT)))
                .contains("한정판 조던 낙찰 1순위 확정 · 45,000원 (철수님)");
    }

    @Test
    @DisplayName("ItemPassed는 물품명을 담은 유찰 문구를 만든다")
    void itemPassed() {
        assertThat(EventMessages.of(ItemPassed.of(1L, 2L, "한정판 조던", OCCURRED_AT)))
                .contains("한정판 조던 유찰 · 입찰자가 없습니다");
    }

    @Test
    @DisplayName("ItemClosingSoon은 분으로 떨어지는 남은 시간을 분으로 적는다")
    void itemClosingSoon() {
        assertThat(EventMessages.of(ItemClosingSoon.of(1L, 2L, "한정판 조던", 60, OCCURRED_AT)))
                .contains("한정판 조던 마감 1분 전");

        assertThat(EventMessages.of(ItemClosingSoon.of(1L, 2L, "한정판 조던", 300, OCCURRED_AT)))
                .as("방마다 트리거가 달라 1분으로 고정된 문구가 아니다")
                .contains("한정판 조던 마감 5분 전");
    }

    @Test
    @DisplayName("ItemClosingSoon은 분으로 떨어지지 않는 남은 시간을 초로 적는다")
    void itemClosingSoonSeconds() {
        assertThat(EventMessages.of(ItemClosingSoon.of(1L, 2L, "한정판 조던", 90, OCCURRED_AT)))
                .contains("한정판 조던 마감 90초 전");
    }

    @Test
    @DisplayName("BidPlaced는 입찰 문구와 금액 포맷을 만든다")
    void bidPlaced() {
        assertThat(EventMessages.of(BidPlaced.of(1L, 2L, "한정판 조던", "영희", 45000L, OCCURRED_AT)))
                .contains("영희님이 45,000원 입찰 · 한정판 조던");
    }

    @Test
    @DisplayName("SoftCloseExtended는 연장 문구를 만든다")
    void softCloseExtended() {
        assertThat(EventMessages.of(
                SoftCloseExtended.of(1L, 2L, "한정판 조던", 30, OCCURRED_AT.plusSeconds(30), OCCURRED_AT)))
                .contains("Soft Close 발동 · 한정판 조던 마감 +30초 연장");
    }

    @Test
    @DisplayName("WinnerDecided는 화면에 띄우지 않으므로 예외 없이 빈 값을 돌려준다")
    void winnerDecidedHasNoMessage() {
        assertThat(EventMessages.of(WinnerDecided.of(1L, 2L, 99L, 45000L, OCCURRED_AT)))
                .isEmpty();
    }

    /**
     * 거래 진행 상황은 방 전체에 뿌리지 않는다. 누가 거래에 실패했는지 공개할 이유가 없다.
     */
    @Test
    @DisplayName("DealRightAssigned는 방에 뿌리지 않으므로 빈 값을 돌려준다")
    void dealRightAssignedHasNoMessage() {
        assertThat(EventMessages.of(DealRightAssigned.of(1L, 2L, 55L, 2, 99L, 45000L, OCCURRED_AT)))
                .isEmpty();
    }
}
