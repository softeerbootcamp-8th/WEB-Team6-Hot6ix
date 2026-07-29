package com.hot6ix.upbid.global.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ItemEventTest {

    private static final Long ROOM_ID = 1L;
    private static final Long ITEM_ID = 2L;
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 7, 28, 12, 0);

    @Nested
    @DisplayName("ItemEnded")
    class ItemEndedTest {

        @Test
        @DisplayName("of는 전달한 식별자와 마감가를 그대로 담고 ITEM_ENDED 타입을 설정한다")
        void of_setsFieldsAndType() {
            ItemEnded event = ItemEnded.of(ROOM_ID, ITEM_ID, 5000L, OCCURRED_AT);

            assertThat(event.roomId()).isEqualTo(ROOM_ID);
            assertThat(event.itemId()).isEqualTo(ITEM_ID);
            assertThat(event.finalPrice()).isEqualTo(5000L);
            assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
            assertThat(event.type()).isEqualTo(EventType.ITEM_ENDED);
            assertThat(event).isInstanceOf(ItemEvent.class);
        }

        @Test
        @DisplayName("of는 호출마다 서로 다른 null 아닌 eventId를 만든다")
        void of_generatesUniqueEventId() {
            ItemEnded first = ItemEnded.of(ROOM_ID, ITEM_ID, 5000L, OCCURRED_AT);
            ItemEnded second = ItemEnded.of(ROOM_ID, ITEM_ID, 5000L, OCCURRED_AT);

            assertThat(first.eventId()).isNotNull();
            assertThat(first.eventId()).isNotEqualTo(second.eventId());
        }

        @Test
        @DisplayName("필수 필드가 null이면 NullPointerException이 발생한다")
        void rejectsNullRequiredFields() {
            assertThatNullPointerException()
                    .isThrownBy(() -> ItemEnded.of(null, ITEM_ID, 5000L, OCCURRED_AT));
            assertThatNullPointerException()
                    .isThrownBy(() -> ItemEnded.of(ROOM_ID, null, 5000L, OCCURRED_AT));
            assertThatNullPointerException()
                    .isThrownBy(() -> ItemEnded.of(ROOM_ID, ITEM_ID, null, OCCURRED_AT));
            assertThatNullPointerException()
                    .isThrownBy(() -> ItemEnded.of(ROOM_ID, ITEM_ID, 5000L, null));
        }
    }

    @Nested
    @DisplayName("WinnerDecided")
    class WinnerDecidedTest {

        @Test
        @DisplayName("of는 전달한 낙찰자와 낙찰가를 그대로 담고 WINNER_DECIDED 타입을 설정한다")
        void of_setsFieldsAndType() {
            WinnerDecided event = WinnerDecided.of(ROOM_ID, ITEM_ID, 99L, 7000L, OCCURRED_AT);

            assertThat(event.roomId()).isEqualTo(ROOM_ID);
            assertThat(event.itemId()).isEqualTo(ITEM_ID);
            assertThat(event.winnerUserId()).isEqualTo(99L);
            assertThat(event.winningPrice()).isEqualTo(7000L);
            assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
            assertThat(event.type()).isEqualTo(EventType.WINNER_DECIDED);
            assertThat(event).isInstanceOf(ItemEvent.class);
        }

        @Test
        @DisplayName("of는 호출마다 서로 다른 null 아닌 eventId를 만든다")
        void of_generatesUniqueEventId() {
            WinnerDecided first = WinnerDecided.of(ROOM_ID, ITEM_ID, 99L, 7000L, OCCURRED_AT);
            WinnerDecided second = WinnerDecided.of(ROOM_ID, ITEM_ID, 99L, 7000L, OCCURRED_AT);

            assertThat(first.eventId()).isNotNull();
            assertThat(first.eventId()).isNotEqualTo(second.eventId());
        }

        @Test
        @DisplayName("필수 필드가 null이면 NullPointerException이 발생한다")
        void rejectsNullRequiredFields() {
            assertThatNullPointerException()
                    .isThrownBy(() -> WinnerDecided.of(null, ITEM_ID, 99L, 7000L, OCCURRED_AT));
            assertThatNullPointerException()
                    .isThrownBy(() -> WinnerDecided.of(ROOM_ID, null, 99L, 7000L, OCCURRED_AT));
            assertThatNullPointerException()
                    .isThrownBy(() -> WinnerDecided.of(ROOM_ID, ITEM_ID, null, 7000L, OCCURRED_AT));
            assertThatNullPointerException()
                    .isThrownBy(() -> WinnerDecided.of(ROOM_ID, ITEM_ID, 99L, null, OCCURRED_AT));
            assertThatNullPointerException()
                    .isThrownBy(() -> WinnerDecided.of(ROOM_ID, ITEM_ID, 99L, 7000L, null));
        }
    }

    @Nested
    @DisplayName("ItemPassed")
    class ItemPassedTest {

        @Test
        @DisplayName("of는 전달한 식별자를 그대로 담고 ITEM_PASSED 타입을 설정한다")
        void of_setsFieldsAndType() {
            ItemPassed event = ItemPassed.of(ROOM_ID, ITEM_ID, OCCURRED_AT);

            assertThat(event.roomId()).isEqualTo(ROOM_ID);
            assertThat(event.itemId()).isEqualTo(ITEM_ID);
            assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
            assertThat(event.type()).isEqualTo(EventType.ITEM_PASSED);
            assertThat(event).isInstanceOf(ItemEvent.class);
        }

        @Test
        @DisplayName("of는 호출마다 서로 다른 null 아닌 eventId를 만든다")
        void of_generatesUniqueEventId() {
            ItemPassed first = ItemPassed.of(ROOM_ID, ITEM_ID, OCCURRED_AT);
            ItemPassed second = ItemPassed.of(ROOM_ID, ITEM_ID, OCCURRED_AT);

            assertThat(first.eventId()).isNotNull();
            assertThat(first.eventId()).isNotEqualTo(second.eventId());
        }

        @Test
        @DisplayName("필수 필드가 null이면 NullPointerException이 발생한다")
        void rejectsNullRequiredFields() {
            assertThatNullPointerException()
                    .isThrownBy(() -> ItemPassed.of(null, ITEM_ID, OCCURRED_AT));
            assertThatNullPointerException()
                    .isThrownBy(() -> ItemPassed.of(ROOM_ID, null, OCCURRED_AT));
            assertThatNullPointerException()
                    .isThrownBy(() -> ItemPassed.of(ROOM_ID, ITEM_ID, null));
        }
    }
}
