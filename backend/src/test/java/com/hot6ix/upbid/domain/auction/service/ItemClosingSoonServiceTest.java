package com.hot6ix.upbid.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.ClosingSoonItemProjection;
import com.hot6ix.upbid.global.event.DomainEvent;
import com.hot6ix.upbid.global.event.EventType;
import com.hot6ix.upbid.global.event.payload.ItemClosingSoon;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ItemClosingSoonServiceTest {

    private static final Long ROOM_ID = 10L;
    private static final Long ITEM_ID = 30L;
    private static final String ITEM_NAME = "한정판 피규어";
    private static final int TRIGGER_SECONDS = 60;

    @Mock
    private AuctionItemRepository auctionItemRepository;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @InjectMocks
    private ItemClosingSoonService itemClosingSoonService;

    @Test
    @DisplayName("알림 시각은 연장 구간이 열리는 순간이다")
    void resolvesNotifyAtWhenExtensionWindowOpens() {

        LocalDateTime endAt = LocalDateTime.now().plusMinutes(10);
        givenItem(AuctionItemStatus.IN_PROGRESS, endAt, TRIGGER_SECONDS);

        Optional<LocalDateTime> notifyAt = itemClosingSoonService.resolveNotifyAt(ITEM_ID);

        assertThat(notifyAt)
                .as("이 시각부터 입찰하면 마감이 밀린다. 그래서 알림 시점을 여기에 맞춘다")
                .contains(endAt.minusSeconds(TRIGGER_SECONDS));
    }

    @Test
    @DisplayName("없는 물품은 알림 대상이 아니다")
    void ignoresMissingItem() {

        when(auctionItemRepository.findClosingSoonView(ITEM_ID)).thenReturn(Optional.empty());

        assertThat(itemClosingSoonService.resolveNotifyAt(ITEM_ID)).isEmpty();
    }

    @Test
    @DisplayName("진행 중이 아닌 물품에는 알림이 나가지 않는다")
    void ignoresClosedItem() {

        givenItem(AuctionItemStatus.SOLD, LocalDateTime.now().minusSeconds(30), TRIGGER_SECONDS);

        Optional<LocalDateTime> rescheduleAt = itemClosingSoonService.notifyIfDue(ITEM_ID);

        assertThat(rescheduleAt).isEmpty();
        verify(domainEventPublisher, never()).publish(any(DomainEvent.class));
    }

    @Test
    @DisplayName("연장 설정이 없는 방에는 알림이 나가지 않는다")
    void ignoresRoomWithoutSoftClose() {

        givenItem(AuctionItemStatus.IN_PROGRESS, LocalDateTime.now().plusMinutes(10), null);

        assertThat(itemClosingSoonService.resolveNotifyAt(ITEM_ID))
                .as("연장이 없는 방에서 '지금부터 넣으면 미뤄진다'는 알림은 사실이 아니다")
                .isEmpty();
    }

    @Test
    @DisplayName("마감 시각이 없는 물품에는 알림이 나가지 않는다")
    void ignoresItemWithoutEndAt() {

        givenItem(AuctionItemStatus.IN_PROGRESS, null, TRIGGER_SECONDS);

        assertThat(itemClosingSoonService.resolveNotifyAt(ITEM_ID)).isEmpty();
    }

    @Test
    @DisplayName("때가 되면 마감 임박 이벤트를 발행한다")
    void publishesWhenDue() {

        LocalDateTime endAt = LocalDateTime.now().plusSeconds(30);
        givenItem(AuctionItemStatus.IN_PROGRESS, endAt, TRIGGER_SECONDS);

        Optional<LocalDateTime> rescheduleAt = itemClosingSoonService.notifyIfDue(ITEM_ID);

        assertThat(rescheduleAt).as("발행했으면 다시 예약할 시각이 없다").isEmpty();

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(domainEventPublisher).publish(captor.capture());

        assertThat(captor.getValue())
                .isInstanceOfSatisfying(ItemClosingSoon.class, event -> {
                    assertThat(event.type()).isEqualTo(EventType.ITEM_CLOSING_SOON);
                    assertThat(event.roomId()).isEqualTo(ROOM_ID);
                    assertThat(event.itemId()).isEqualTo(ITEM_ID);
                    assertThat(event.itemName()).isEqualTo(ITEM_NAME);
                    assertThat(event.remainingSeconds())
                            .as("문구를 화면이 만들 수 있게 방의 트리거 값을 그대로 싣는다")
                            .isEqualTo(TRIGGER_SECONDS);
                });
    }

    @Test
    @DisplayName("예약이 깨는 사이 연장이 커밋됐으면 알리지 않고 밀린 시각을 돌려준다")
    void reschedulesWhenExtendedJustBefore() {

        LocalDateTime endAt = LocalDateTime.now().plusMinutes(10);
        givenItem(AuctionItemStatus.IN_PROGRESS, endAt, TRIGGER_SECONDS);

        Optional<LocalDateTime> rescheduleAt = itemClosingSoonService.notifyIfDue(ITEM_ID);

        assertThat(rescheduleAt)
                .as("예약이 알던 시각은 이미 옛것이다. 그대로 알리면 '곧 마감'이 거짓이 된다")
                .contains(endAt.minusSeconds(TRIGGER_SECONDS));
        verify(domainEventPublisher, never()).publish(any(DomainEvent.class));
    }

    private void givenItem(AuctionItemStatus status, LocalDateTime endAt, Integer triggerSeconds) {
        when(auctionItemRepository.findClosingSoonView(ITEM_ID)).thenReturn(Optional.of(
                new ClosingSoonItemProjection(ROOM_ID, ITEM_NAME, status, endAt, triggerSeconds)));
    }
}
