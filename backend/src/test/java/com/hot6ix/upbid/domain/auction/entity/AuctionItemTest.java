package com.hot6ix.upbid.domain.auction.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.hot6ix.upbid.domain.product.entity.Product;
import com.hot6ix.upbid.domain.user.entity.SellerProfile;
import com.hot6ix.upbid.domain.user.entity.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AuctionItemTest {

    private static final LocalDateTime END_AT = LocalDateTime.of(2026, 8, 5, 12, 0);
    private static final int TRIGGER_SECONDS = 30;
    private static final int EXTEND_SECONDS = 30;

    @Nested
    @DisplayName("extendIfClosingSoon")
    class ExtendIfClosingSoon {

        @Test
        @DisplayName("마감이 임박했으면 마감 시각을 밀고 누적 연장에 더한다")
        void extendsWhenClosingSoon() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 0);

            boolean extended = auctionItem.extendIfClosingSoon(END_AT.minusSeconds(15));

            assertThat(extended).isTrue();
            assertThat(auctionItem.getEndAt()).isEqualTo(END_AT.plusSeconds(EXTEND_SECONDS));
            assertThat(auctionItem.getTotalExtensionSeconds()).isEqualTo(EXTEND_SECONDS);
        }

        @Test
        @DisplayName("원래 마감 시각은 연장해도 그대로 남는다")
        void keepsOriginalEndAt() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 0);
            ReflectionTestUtils.setField(auctionItem, "originalEndAt", END_AT);

            auctionItem.extendIfClosingSoon(END_AT.minusSeconds(15));

            assertThat(auctionItem.getOriginalEndAt())
                    .as("연장이 몇 번 붙었든 원래 언제 끝날 예정이었는지는 남아 있어야 한다")
                    .isEqualTo(END_AT);
        }

        @Test
        @DisplayName("아직 임박 구간에 들어오지 않았으면 아무것도 바꾸지 않는다")
        void doesNotExtendBeforeTrigger() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 0);

            boolean extended = auctionItem.extendIfClosingSoon(END_AT.minusSeconds(31));

            assertThat(extended).isFalse();
            assertThat(auctionItem.getEndAt()).isEqualTo(END_AT);
            assertThat(auctionItem.getTotalExtensionSeconds()).isZero();
        }

        @Test
        @DisplayName("임박 구간이 시작되는 순간의 입찰도 연장 대상이다")
        void extendsExactlyAtTrigger() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 0);

            boolean extended = auctionItem.extendIfClosingSoon(END_AT.minusSeconds(TRIGGER_SECONDS));

            assertThat(extended)
                    .as("경계를 열어두지 않으면 설정한 트리거 초가 실제보다 1초 짧게 동작한다")
                    .isTrue();
        }

        @Test
        @DisplayName("이번 연장까지 더해 누적 상한을 넘기면 연장하지 않는다")
        void doesNotExtendBeyondCap() {

            int almostFull = AuctionItem.MAX_TOTAL_EXTENSION_SECONDS - EXTEND_SECONDS + 1;
            AuctionItem auctionItem =
                    newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, almostFull);

            boolean extended = auctionItem.extendIfClosingSoon(END_AT.minusSeconds(15));

            assertThat(extended).isFalse();
            assertThat(auctionItem.getEndAt())
                    .as("남은 만큼만 밀지 않는다. 연장 폭은 언제나 방 설정값과 같아야 한다")
                    .isEqualTo(END_AT);
            assertThat(auctionItem.getTotalExtensionSeconds()).isEqualTo(almostFull);
        }

        @Test
        @DisplayName("누적이 상한에 딱 맞는 연장은 받아들인다")
        void extendsUpToCap() {

            int room = AuctionItem.MAX_TOTAL_EXTENSION_SECONDS - EXTEND_SECONDS;
            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, room);

            boolean extended = auctionItem.extendIfClosingSoon(END_AT.minusSeconds(15));

            assertThat(extended).isTrue();
            assertThat(auctionItem.getTotalExtensionSeconds())
                    .isEqualTo(AuctionItem.MAX_TOTAL_EXTENSION_SECONDS);
        }

        @Test
        @DisplayName("연장이 이어지면 마감 시각과 누적이 함께 쌓인다")
        void accumulatesAcrossExtensions() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 0);

            auctionItem.extendIfClosingSoon(END_AT.minusSeconds(15));
            auctionItem.extendIfClosingSoon(auctionItem.getEndAt().minusSeconds(15));

            assertThat(auctionItem.getEndAt()).isEqualTo(END_AT.plusSeconds(EXTEND_SECONDS * 2L));
            assertThat(auctionItem.getTotalExtensionSeconds()).isEqualTo(EXTEND_SECONDS * 2);
        }

        @Test
        @DisplayName("경매방에 Soft Close 설정이 없으면 연장하지 않는다")
        void doesNotExtendWithoutRoomSettings() {

            AuctionItem auctionItem = newItem(newRoom(null, null), END_AT, 0);

            boolean extended = auctionItem.extendIfClosingSoon(END_AT.minusSeconds(15));

            assertThat(extended).isFalse();
            assertThat(auctionItem.getEndAt()).isEqualTo(END_AT);
        }

        @Test
        @DisplayName("마감 시각이 없는 물품은 연장 판정을 하지 않는다")
        void doesNotExtendWithoutEndAt() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), null, 0);

            boolean extended = auctionItem.extendIfClosingSoon(END_AT);

            assertThat(extended).isFalse();
            assertThat(auctionItem.getEndAt()).isNull();
        }
    }

    @Nested
    @DisplayName("closeEarly")
    class CloseEarly {

        /** 아직 임박 구간에 들어오지 않은 시각. 여기서 눌러야 앞당길 자리가 있다. */
        private static final LocalDateTime NOW = END_AT.minusMinutes(10);

        @Test
        @DisplayName("마감을 지금부터 트리거 초 뒤로 앞당긴다")
        void advancesToTrigger() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 0);

            boolean advanced = auctionItem.closeEarly(NOW);

            assertThat(advanced).isTrue();
            assertThat(auctionItem.getEndAt()).isEqualTo(NOW.plusSeconds(TRIGGER_SECONDS));
        }

        @Test
        @DisplayName("앞당겨도 원래 마감 시각과 누적 연장은 그대로 남는다")
        void keepsOriginalEndAtAndExtensions() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 120);
            ReflectionTestUtils.setField(auctionItem, "originalEndAt", END_AT);

            auctionItem.closeEarly(NOW);

            assertThat(auctionItem.getOriginalEndAt()).isEqualTo(END_AT);
            assertThat(auctionItem.getTotalExtensionSeconds())
                    .as("앞당기기는 연장이 아니라 누적에 더할 것이 없다")
                    .isEqualTo(120);
        }

        @Test
        @DisplayName("경매방에 트리거 설정이 없으면 60초 뒤로 앞당긴다")
        void usesDefaultTriggerWithoutRoomSettings() {

            AuctionItem auctionItem = newItem(newRoom(null, null), END_AT, 0);

            boolean advanced = auctionItem.closeEarly(NOW);

            assertThat(advanced).isTrue();
            assertThat(auctionItem.getEndAt())
                    .isEqualTo(NOW.plusSeconds(AuctionItem.DEFAULT_SOFT_CLOSE_TRIGGER_SECONDS));
        }

        @Test
        @DisplayName("이미 임박 구간 안이면 마감을 뒤로 밀지 않는다")
        void doesNotPushBackWhenAlreadyClosingSoon() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 0);

            boolean advanced = auctionItem.closeEarly(END_AT.minusSeconds(15));

            assertThat(advanced).isFalse();
            assertThat(auctionItem.getEndAt())
                    .as("앞당기기가 마감을 늘리는 경우가 있으면 안 된다")
                    .isEqualTo(END_AT);
        }

        @Test
        @DisplayName("앞당긴 시각이 지금 마감과 같으면 앞당기지 않는다")
        void doesNotAdvanceToSameEndAt() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 0);

            boolean advanced = auctionItem.closeEarly(END_AT.minusSeconds(TRIGGER_SECONDS));

            assertThat(advanced)
                    .as("바뀌는 값이 없는데 이벤트와 재예약이 나가면 안 된다")
                    .isFalse();
            assertThat(auctionItem.getEndAt()).isEqualTo(END_AT);
        }

        @Test
        @DisplayName("마감 시각이 없는 물품은 앞당기지 않는다")
        void doesNotAdvanceWithoutEndAt() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), null, 0);

            boolean advanced = auctionItem.closeEarly(NOW);

            assertThat(advanced).isFalse();
            assertThat(auctionItem.getEndAt()).isNull();
        }

        @Test
        @DisplayName("앞당긴 뒤에 들어온 입찰은 Soft Close로 연장된다")
        void softCloseStillAppliesAfterAdvancing() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 0);
            auctionItem.closeEarly(NOW);

            boolean extended = auctionItem.extendIfClosingSoon(NOW.plusSeconds(5));

            assertThat(extended)
                    .as("앞당긴 순간부터가 연장 구간이라 그 뒤 입찰은 마감을 민다")
                    .isTrue();
            assertThat(auctionItem.getEndAt())
                    .isEqualTo(NOW.plusSeconds(TRIGGER_SECONDS + EXTEND_SECONDS));
        }
    }

    private static AuctionItem newItem(AuctionRoom auctionRoom, LocalDateTime endAt,
                                       int totalExtensionSeconds) {
        return AuctionItem.builder()
                .auctionRoom(auctionRoom)
                .product(newProduct(auctionRoom.getSellerProfile()))
                .startingPrice(10_000L)
                .bidIncrement(1_000L)
                .status(AuctionItemStatus.IN_PROGRESS)
                .endAt(endAt)
                .totalExtensionSeconds(totalExtensionSeconds)
                .build();
    }

    private static AuctionRoom newRoom(Integer triggerSeconds, Integer extendSeconds) {
        return AuctionRoom.builder()
                .sellerProfile(newSellerProfile())
                .name("승민의 경매방")
                .status(AuctionRoomStatus.OPEN)
                .bidIncrement(1_000L)
                .softCloseTriggerSeconds(triggerSeconds)
                .softCloseExtendSeconds(extendSeconds)
                .build();
    }

    private static Product newProduct(SellerProfile sellerProfile) {
        return Product.builder()
                .sellerProfile(sellerProfile)
                .name("한정판 피규어")
                .description("미개봉 정품")
                .imageUrl("https://cdn.hot6ix.com/item.png")
                .build();
    }

    private static SellerProfile newSellerProfile() {
        return SellerProfile.builder()
                .user(User.builder().email("seller@hot6ix.com").nickname("승민").build())
                .storeName("승민 스토어")
                .build();
    }
}
