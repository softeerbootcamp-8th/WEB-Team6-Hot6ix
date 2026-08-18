package com.hot6ix.upbid.domain.auction.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BidderKeyEncoderTest {

    @Test
    @DisplayName("같은 방과 회원은 회원 ID를 드러내지 않는 같은 bidderKey를 받는다")
    void encodesStableRoomScopedKey() {
        BidderKeyEncoder encoder = new BidderKeyEncoder(new BidderKeyProperties("test-secret"));

        String bidderKey = encoder.encode(10L, 42L);

        assertThat(bidderKey).isEqualTo("E1xv1ZcdTue0om_NcvVpQQ");
        assertThat(bidderKey).doesNotContain("10", "42");
        assertThat(encoder.encode(10L, 42L)).isEqualTo(bidderKey);
    }

    @Test
    @DisplayName("같은 회원도 방이 다르면 서로 다른 bidderKey를 받는다")
    void scopesKeyToRoom() {
        BidderKeyEncoder encoder = new BidderKeyEncoder(new BidderKeyProperties("test-secret"));

        assertThat(encoder.encode(10L, 42L))
                .isNotEqualTo(encoder.encode(11L, 42L));
    }

    @Test
    @DisplayName("비밀키가 비어 있으면 추측 가능한 식별값을 만들지 않고 시작을 거절한다")
    void rejectsBlankSecret() {
        assertThatThrownBy(() -> new BidderKeyEncoder(new BidderKeyProperties("  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bidder-key-secret");
    }
}
