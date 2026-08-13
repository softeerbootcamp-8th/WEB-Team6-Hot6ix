package com.hot6ix.upbid.domain.bid.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BidStreamEventTest {

    @Test
    @DisplayName("BID_ACCEPTED Stream 필드를 타입이 있는 이벤트로 변환한다")
    void parsesBidAccepted() {

        BidStreamEvent event = BidStreamEvent.from(validFields());

        assertThat(event).isEqualTo(new BidStreamEvent.BidAccepted(
                "request-1", 101L, 202L, 11L, 10_000L,
                1_786_636_800_000L, 1_786_637_100_000L, 60, 180));
    }

    @Test
    @DisplayName("필수 필드가 없으면 처리할 수 없는 이벤트로 거절한다")
    void rejectsMissingRequiredField() {

        Map<String, String> fields = validFields();
        fields.remove("requestId");

        assertThatThrownBy(() -> BidStreamEvent.from(fields))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requestId");
    }

    @Test
    @DisplayName("숫자 필드가 숫자가 아니면 처리할 수 없는 이벤트로 거절한다")
    void rejectsMalformedNumber() {

        Map<String, String> fields = validFields();
        fields.put("amount", "만원");

        assertThatThrownBy(() -> BidStreamEvent.from(fields))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    @DisplayName("알 수 없는 이벤트 type은 다른 이벤트로 추측하지 않고 거절한다")
    void rejectsUnknownType() {

        Map<String, String> fields = validFields();
        fields.put("type", "UNKNOWN");

        assertThatThrownBy(() -> BidStreamEvent.from(fields))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    @DisplayName("ITEM_CLOSING 스냅샷은 최고 입찰자가 없는 유찰도 파싱한다")
    void parsesItemClosingWithoutLeader() {
        BidStreamEvent event = BidStreamEvent.from(Map.of(
                "type", "ITEM_CLOSING", "itemId", "101", "roomId", "202",
                "currentPrice", "10000", "leaderUserId", "",
                "endAt", "1786637100000", "totalExtensionSeconds", "60",
                "closeReason", "NATURAL", "closedAt", "1786637100100"));

        assertThat(event).isEqualTo(new BidStreamEvent.ItemClosing(
                101L, 202L, 10_000L, null, 1_786_637_100_000L,
                60, "NATURAL", 1_786_637_100_100L));
    }

    @Test
    @DisplayName("ITEM_CLOSE_ADVANCED의 절대 마감 시각을 파싱한다")
    void parsesItemCloseAdvanced() {
        BidStreamEvent event = BidStreamEvent.from(Map.of(
                "type", "ITEM_CLOSE_ADVANCED", "itemId", "101", "roomId", "202",
                "endAt", "1786637100000", "remainingSeconds", "60",
                "advancedAt", "1786637040000"));

        assertThat(event).isEqualTo(new BidStreamEvent.ItemCloseAdvanced(
                101L, 202L, 1_786_637_100_000L, 60, 1_786_637_040_000L));
    }

    private static Map<String, String> validFields() {
        return new HashMap<>(Map.of(
                "type", "BID_ACCEPTED",
                "requestId", "request-1",
                "itemId", "101",
                "roomId", "202",
                "bidderUserId", "11",
                "amount", "10000",
                "acceptedAt", "1786636800000",
                "endAt", "1786637100000",
                "extendedSeconds", "60",
                "totalExtensionSeconds", "180"));
    }
}
