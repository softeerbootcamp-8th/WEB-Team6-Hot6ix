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
    @DisplayName("applyPersistedBid")
    class ApplyPersistedBid {

        @Test
        @DisplayName("늦게 저장된 낮은 입찰은 현재가와 마감 상태를 되돌리지 않는다")
        void doesNotRegressStateForOlderBid() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 0);
            User highBidder = User.builder().email("high@hot6ix.com").nickname("고액 입찰자").build();
            User lowBidder = User.builder().email("low@hot6ix.com").nickname("저액 입찰자").build();

            auctionItem.applyPersistedBid(
                    highBidder, 20_000L, END_AT.minusSeconds(5), END_AT.plusSeconds(60), 60);
            boolean endAtAdvanced = auctionItem.applyPersistedBid(
                    lowBidder, 10_000L, END_AT.minusSeconds(10), END_AT.plusSeconds(30), 30);

            assertThat(auctionItem.getCurrentPrice()).isEqualTo(20_000L);
            assertThat(auctionItem.getLeaderUser()).isSameAs(highBidder);
            assertThat(auctionItem.getEndAt()).isEqualTo(END_AT.plusSeconds(60));
            assertThat(auctionItem.getTotalExtensionSeconds()).isEqualTo(60);
            assertThat(endAtAdvanced).isFalse();
        }

        @Test
        @DisplayName("Redis가 확정한 더 높은 입찰과 연장 상태를 반영한다")
        void advancesStateForNewerBid() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 0);
            User bidder = User.builder().email("bidder@hot6ix.com").nickname("입찰자").build();

            boolean endAtAdvanced = auctionItem.applyPersistedBid(
                    bidder, 20_000L, END_AT.minusSeconds(5), END_AT.plusSeconds(60), 60);

            assertThat(auctionItem.getCurrentPrice()).isEqualTo(20_000L);
            assertThat(auctionItem.getLeaderUser()).isSameAs(bidder);
            assertThat(auctionItem.getEndAt()).isEqualTo(END_AT.plusSeconds(60));
            assertThat(auctionItem.getTotalExtensionSeconds()).isEqualTo(60);
            assertThat(endAtAdvanced).isTrue();
        }
    }

    /**
     * 앞당김 판정 자체는 {@code close-auction.lua}가 한다. 여기서는 그 결과를 DB에 받아 적을 때의
     * 두 가지, 곧 <b>재전달을 거르는 것</b>과 <b>알림 시각을 언제 찍는지</b>만 본다.
     */
    @Nested
    @DisplayName("applyCloseAdvanced")
    class ApplyCloseAdvanced {

        private static final LocalDateTime ADVANCED_AT = END_AT.minusMinutes(10);

        @Test
        @DisplayName("앞당겨진 마감 시각을 반영한다")
        void appliesAdvancedEndAt() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 0);
            LocalDateTime advancedEndAt = ADVANCED_AT.plusSeconds(300);

            boolean applied = auctionItem.applyCloseAdvanced(advancedEndAt, ADVANCED_AT, 300);

            assertThat(applied).isTrue();
            assertThat(auctionItem.getEndAt()).isEqualTo(advancedEndAt);
        }

        @Test
        @DisplayName("트리거만큼만 남긴 앞당김은 알림을 이미 보낸 것으로 찍는다")
        void marksNotifiedWhenRemainingEqualsTrigger() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 0);

            auctionItem.applyCloseAdvanced(
                    ADVANCED_AT.plusSeconds(TRIGGER_SECONDS), ADVANCED_AT, TRIGGER_SECONDS);

            // 안 찍으면 AuctionRecoveryRunner 가 "예약이 빠졌다"고 보고 되살려 알림이 뒤늦게 나간다.
            assertThat(auctionItem.getNotifiedAt()).isEqualTo(ADVANCED_AT);
        }

        @Test
        @DisplayName("트리거보다 길게 남긴 앞당김은 알림 시각을 찍지 않는다")
        void doesNotMarkNotifiedWhenRemainingIsLongerThanTrigger() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 0);

            auctionItem.applyCloseAdvanced(ADVANCED_AT.plusSeconds(300), ADVANCED_AT, 300);

            assertThat(auctionItem.getNotifiedAt())
                    .as("알림 시각이 아직 미래인데 찍어버리면 마감 임박 알림이 통째로 사라진다")
                    .isNull();
        }

        @Test
        @DisplayName("경매방에 트리거 설정이 없으면 60초를 기준으로 알림 기록 여부를 가른다")
        void usesDefaultTriggerWithoutRoomSettings() {

            AuctionItem auctionItem = newItem(newRoom(null, null), END_AT, 0);
            int defaultTrigger = AuctionItem.DEFAULT_SOFT_CLOSE_TRIGGER_SECONDS;

            auctionItem.applyCloseAdvanced(
                    ADVANCED_AT.plusSeconds(defaultTrigger), ADVANCED_AT, defaultTrigger);

            assertThat(auctionItem.getNotifiedAt()).isEqualTo(ADVANCED_AT);
        }

        @Test
        @DisplayName("같은 이벤트가 다시 오면 반영하지 않는다")
        void ignoresRedelivery() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 0);
            LocalDateTime advancedEndAt = ADVANCED_AT.plusSeconds(300);
            auctionItem.applyCloseAdvanced(advancedEndAt, ADVANCED_AT, 300);

            boolean applied = auctionItem.applyCloseAdvanced(advancedEndAt, ADVANCED_AT, 300);

            assertThat(applied)
                    .as("다시 반영하면 앞당김 이벤트와 재예약이 한 번 더 나간다")
                    .isFalse();
            assertThat(auctionItem.getEndAt()).isEqualTo(advancedEndAt);
        }

        @Test
        @DisplayName("더 나중 앞당김에 밀린 낡은 이벤트는 마감을 되돌리지 않는다")
        void ignoresStaleEvent() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 0);
            LocalDateTime later = ADVANCED_AT.plusSeconds(300);
            LocalDateTime earlier = ADVANCED_AT.plusSeconds(120);
            auctionItem.applyCloseAdvanced(earlier, ADVANCED_AT.plusSeconds(60), 60);

            boolean applied = auctionItem.applyCloseAdvanced(later, ADVANCED_AT, 300);

            assertThat(applied)
                    .as("앞당기기는 마감을 앞으로만 옮긴다. 뒤로 가는 값은 낡은 것이다")
                    .isFalse();
            assertThat(auctionItem.getEndAt()).isEqualTo(earlier);
        }

        @Test
        @DisplayName("거른 이벤트는 알림 시각도 건드리지 않는다")
        void keepsNotifiedAtWhenIgnored() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), END_AT, 0);
            LocalDateTime advancedEndAt = ADVANCED_AT.plusSeconds(300);
            auctionItem.applyCloseAdvanced(advancedEndAt, ADVANCED_AT, 300);

            auctionItem.applyCloseAdvanced(advancedEndAt, ADVANCED_AT, TRIGGER_SECONDS);

            assertThat(auctionItem.getNotifiedAt())
                    .as("재전달로 알림이 막히면 정상 알림이 사라진다")
                    .isNull();
        }

        @Test
        @DisplayName("마감 시각이 없는 물품은 반영하지 않는다")
        void ignoresItemWithoutEndAt() {

            AuctionItem auctionItem = newItem(newRoom(TRIGGER_SECONDS, EXTEND_SECONDS), null, 0);

            boolean applied =
                    auctionItem.applyCloseAdvanced(ADVANCED_AT.plusSeconds(300), ADVANCED_AT, 300);

            assertThat(applied).isFalse();
            assertThat(auctionItem.getEndAt()).isNull();
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
