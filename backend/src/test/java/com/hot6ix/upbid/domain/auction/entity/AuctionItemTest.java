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

        /** 아직 임박 구간에 들어오지 않은 시각. 여기서 눌러야 앞당길 자리가 있다. 마감까지 10분 남았다. */
        private static final LocalDateTime NOW = END_AT.minusMinutes(10);

        /** 트리거보다는 길고 지금 마감까지 남은 10분보다는 짧은 값. */
        private static final int REMAINING_SECONDS = 300;

        @Test
        @DisplayName("요청이 없으면 마감을 지금부터 트리거 초 뒤로 앞당긴다")
        void advancesToTriggerWithoutRequest() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 0);

            CloseEarlyResult result = auctionItem.closeEarly(NOW, null);

            assertThat(result).isEqualTo(CloseEarlyResult.ADVANCED);
            assertThat(auctionItem.getEndAt()).isEqualTo(NOW.plusSeconds(TRIGGER_SECONDS));
        }

        @Test
        @DisplayName("요청한 남은 초만큼 뒤로 앞당긴다")
        void advancesToRequestedRemaining() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 0);

            CloseEarlyResult result = auctionItem.closeEarly(NOW, REMAINING_SECONDS);

            assertThat(result).isEqualTo(CloseEarlyResult.ADVANCED);
            assertThat(auctionItem.getEndAt()).isEqualTo(NOW.plusSeconds(REMAINING_SECONDS));
        }

        @Test
        @DisplayName("트리거만큼만 남기면 마감 임박 알림을 이미 알린 것으로 찍는다")
        void marksNotifiedSoThatRecoveryDoesNotRevive() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 0);

            auctionItem.closeEarly(NOW, null);

            // 앞당긴 뒤 알림 시각(end_at - 트리거)이 정확히 이 시각이라, 안 찍으면
            // AuctionRecoveryRunner 가 "예약이 빠졌다"고 보고 되살려 알림이 뒤늦게 나간다.
            assertThat(auctionItem.getNotifiedAt())
                    .isEqualTo(NOW)
                    .isEqualTo(auctionItem.getEndAt().minusSeconds(TRIGGER_SECONDS));
        }

        @Test
        @DisplayName("트리거보다 길게 남기면 알림이 아직 남아 있어 알림 시각을 찍지 않는다")
        void doesNotMarkNotifiedWhenRemainingIsLongerThanTrigger() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 0);

            auctionItem.closeEarly(NOW, REMAINING_SECONDS);

            assertThat(auctionItem.getNotifiedAt())
                    .as("알림 시각이 아직 미래인데 찍어버리면 마감 임박 알림이 통째로 사라진다")
                    .isNull();
            assertThat(auctionItem.getEndAt().minusSeconds(TRIGGER_SECONDS))
                    .as("알림 시각이 지금보다 뒤여야 알릴 사건이 남는다")
                    .isAfter(NOW);
        }

        @Test
        @DisplayName("앞당기지 못했으면 알림 시각도 건드리지 않는다")
        void doesNotMarkNotifiedWhenRejected() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 0);

            CloseEarlyResult result = auctionItem.closeEarly(END_AT.minusSeconds(15), null);

            assertThat(result).isEqualTo(CloseEarlyResult.ALREADY_CLOSING_SOON);
            assertThat(auctionItem.getNotifiedAt())
                    .as("거절된 요청이 알림을 막아버리면 정상 알림이 사라진다")
                    .isNull();
        }

        @Test
        @DisplayName("앞당겨도 원래 마감 시각과 누적 연장은 그대로 남는다")
        void keepsOriginalEndAtAndExtensions() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 120);
            ReflectionTestUtils.setField(auctionItem, "originalEndAt", END_AT);

            auctionItem.closeEarly(NOW, REMAINING_SECONDS);

            assertThat(auctionItem.getOriginalEndAt()).isEqualTo(END_AT);
            assertThat(auctionItem.getTotalExtensionSeconds())
                    .as("앞당기기는 연장이 아니라 누적에 더할 것이 없다")
                    .isEqualTo(120);
        }

        @Test
        @DisplayName("경매방에 트리거 설정이 없으면 60초 뒤로 앞당긴다")
        void usesDefaultTriggerWithoutRoomSettings() {

            AuctionItem auctionItem = newItem(newRoom(null, null), END_AT, 0);

            CloseEarlyResult result = auctionItem.closeEarly(NOW, null);

            assertThat(result).isEqualTo(CloseEarlyResult.ADVANCED);
            assertThat(auctionItem.getEndAt())
                    .isEqualTo(NOW.plusSeconds(AuctionItem.DEFAULT_SOFT_CLOSE_TRIGGER_SECONDS));
        }

        @Test
        @DisplayName("트리거보다 짧게 남기려 하면 거절한다")
        void rejectsRemainingShorterThanTrigger() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 0);

            CloseEarlyResult result = auctionItem.closeEarly(NOW, TRIGGER_SECONDS - 1);

            assertThat(result).isEqualTo(CloseEarlyResult.REMAINING_TOO_SHORT);
            assertThat(auctionItem.getEndAt())
                    .as("거절했으면 아무 값도 바뀌지 않아야 한다")
                    .isEqualTo(END_AT);
        }

        @Test
        @DisplayName("지금 마감보다 뒤로 남기려 하면 거절한다")
        void rejectsRemainingBeyondCurrentEndAt() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 0);

            CloseEarlyResult result = auctionItem.closeEarly(NOW, 20 * 60);

            assertThat(result)
                    .as("막지 않으면 앞당기기가 마감을 미루는 셈이 된다")
                    .isEqualTo(CloseEarlyResult.REMAINING_TOO_LONG);
            assertThat(auctionItem.getEndAt()).isEqualTo(END_AT);
        }

        @Test
        @DisplayName("지금 마감과 똑같은 시각으로 남기려 하면 거절한다")
        void rejectsRemainingEqualToCurrentEndAt() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 0);

            CloseEarlyResult result = auctionItem.closeEarly(NOW, 10 * 60);

            assertThat(result)
                    .as("바뀌는 값이 없는데 이벤트와 재예약이 나가면 안 된다")
                    .isEqualTo(CloseEarlyResult.REMAINING_TOO_LONG);
            assertThat(auctionItem.getEndAt()).isEqualTo(END_AT);
        }

        @Test
        @DisplayName("이미 임박 구간 안이면 마감을 뒤로 밀지 않는다")
        void doesNotPushBackWhenAlreadyClosingSoon() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 0);

            CloseEarlyResult result = auctionItem.closeEarly(END_AT.minusSeconds(15), null);

            assertThat(result).isEqualTo(CloseEarlyResult.ALREADY_CLOSING_SOON);
            assertThat(auctionItem.getEndAt())
                    .as("앞당기기가 마감을 늘리는 경우가 있으면 안 된다")
                    .isEqualTo(END_AT);
        }

        @Test
        @DisplayName("이미 임박 구간 안이면 요청 값과 무관하게 같은 이유로 거절한다")
        void rejectsWithSameReasonWhenAlreadyClosingSoonRegardlessOfRequest() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 0);

            CloseEarlyResult result = auctionItem.closeEarly(END_AT.minusSeconds(15), REMAINING_SECONDS);

            assertThat(result)
                    .as("앞당길 자리가 없는 것은 요청이 아니라 물품 상태의 문제다")
                    .isEqualTo(CloseEarlyResult.ALREADY_CLOSING_SOON);
        }

        @Test
        @DisplayName("앞당긴 시각이 지금 마감과 같으면 앞당기지 않는다")
        void doesNotAdvanceToSameEndAt() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 0);

            CloseEarlyResult result =
                    auctionItem.closeEarly(END_AT.minusSeconds(TRIGGER_SECONDS), null);

            assertThat(result)
                    .as("바뀌는 값이 없는데 이벤트와 재예약이 나가면 안 된다")
                    .isEqualTo(CloseEarlyResult.ALREADY_CLOSING_SOON);
            assertThat(auctionItem.getEndAt()).isEqualTo(END_AT);
        }

        @Test
        @DisplayName("마감 시각이 없는 물품은 앞당기지 않는다")
        void doesNotAdvanceWithoutEndAt() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), null, 0);

            CloseEarlyResult result = auctionItem.closeEarly(NOW, null);

            assertThat(result).isEqualTo(CloseEarlyResult.ALREADY_CLOSING_SOON);
            assertThat(auctionItem.getEndAt()).isNull();
        }

        @Test
        @DisplayName("앞당긴 뒤에 들어온 입찰은 Soft Close로 연장된다")
        void softCloseStillAppliesAfterAdvancing() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 0);
            auctionItem.closeEarly(NOW, null);

            boolean extended = auctionItem.extendIfClosingSoon(NOW.plusSeconds(5));

            assertThat(extended)
                    .as("앞당긴 순간부터가 연장 구간이라 그 뒤 입찰은 마감을 민다")
                    .isTrue();
            assertThat(auctionItem.getEndAt())
                    .isEqualTo(NOW.plusSeconds(TRIGGER_SECONDS + EXTEND_SECONDS));
        }

        @Test
        @DisplayName("트리거보다 길게 남기면 연장 구간에 들어오기 전 입찰은 마감을 밀지 않는다")
        void softCloseDoesNotApplyBeforeTriggerWindowAfterAdvancing() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 0);
            auctionItem.closeEarly(NOW, REMAINING_SECONDS);

            boolean extended = auctionItem.extendIfClosingSoon(NOW.plusSeconds(5));

            assertThat(extended)
                    .as("남긴 시간이 트리거보다 길면 그만큼은 연장 없는 구간이다")
                    .isFalse();
            assertThat(auctionItem.getEndAt()).isEqualTo(NOW.plusSeconds(REMAINING_SECONDS));
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
