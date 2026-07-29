package com.hot6ix.upbid.global.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.global.event.message.EventChannels;
import com.hot6ix.upbid.global.event.payload.ItemEnded;
import com.hot6ix.upbid.global.event.payload.ItemPassed;
import com.hot6ix.upbid.global.event.payload.WinnerDecided;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EventChannelsTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 7, 28, 12, 0);

    @Test
    @DisplayName("ItemEnded는 물품 채널 문자열을 만든다")
    void itemEndedChannel() {
        String channel = EventChannels.of(ItemEnded.of(1L, 2L, "상품", 5000L, "철수", OCCURRED_AT));

        assertThat(channel).isEqualTo("room:1:item:2");
    }

    @Test
    @DisplayName("WinnerDecided는 물품 채널 문자열을 만든다")
    void winnerDecidedChannel() {
        String channel = EventChannels.of(WinnerDecided.of(1L, 2L, 99L, 7000L, OCCURRED_AT));

        assertThat(channel).isEqualTo("room:1:item:2");
    }

    @Test
    @DisplayName("ItemPassed는 물품 채널 문자열을 만든다")
    void itemPassedChannel() {
        String channel = EventChannels.of(ItemPassed.of(1L, 2L, OCCURRED_AT));

        assertThat(channel).isEqualTo("room:1:item:2");
    }
}
