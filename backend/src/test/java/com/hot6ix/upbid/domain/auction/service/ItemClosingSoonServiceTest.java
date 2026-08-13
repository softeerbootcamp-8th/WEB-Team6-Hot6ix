package com.hot6ix.upbid.domain.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
        givenClaimSucceeds();

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

    @Test
    @DisplayName("남이 먼저 알렸으면 발행하지 않는다")
    void doesNotPublishWhenAlreadyNotified() {

        LocalDateTime endAt = LocalDateTime.now().plusSeconds(30);
        givenItem(AuctionItemStatus.IN_PROGRESS, endAt, TRIGGER_SECONDS,
                endAt.minusSeconds(TRIGGER_SECONDS));
        givenClaimFails();

        Optional<LocalDateTime> rescheduleAt = itemClosingSoonService.notifyIfDue(ITEM_ID);

        // 두 서버가 같은 예약을 집었거나 미뤄 둔 예약이 다시 떠오른 경우다. 조건에 걸리는 것이
        // 하나뿐이라 진 쪽은 0 을 받고 돌아간다.
        verify(domainEventPublisher, never()).publish(any(DomainEvent.class));
        assertThat(rescheduleAt).as("다시 걸 일도 아니다. 이미 나간 알림이다").isEmpty();
    }

    @Test
    @DisplayName("자리를 잡는 사이에 연장이 커밋됐으면 발행하지 않고 새 시각으로 다시 예약한다")
    void reschedulesWhenEndAtChangedWhileClaiming() {

        LocalDateTime endAt = LocalDateTime.now().plusSeconds(30);
        LocalDateTime extendedEndAt = endAt.plusSeconds(30);

        // 읽은 뒤 UPDATE 가 행 락을 기다리는 사이에 입찰이 연장을 커밋한 상황이다.
        when(auctionItemRepository.findClosingSoonView(ITEM_ID))
                .thenReturn(Optional.of(projection(AuctionItemStatus.IN_PROGRESS, endAt, null)))
                .thenReturn(Optional.of(
                        projection(AuctionItemStatus.IN_PROGRESS, extendedEndAt, null)));
        givenClaimFails();

        Optional<LocalDateTime> rescheduleAt = itemClosingSoonService.notifyIfDue(ITEM_ID);

        // 낡은 시각으로 발행하면 아직 임박하지도 않은 물품에 "곧 마감"이 나간다.
        verify(domainEventPublisher, never()).publish(any(DomainEvent.class));
        assertThat(rescheduleAt)
                .as("예약을 지우면 다음 재동기화까지 이 알림이 사라진다")
                .contains(extendedEndAt.minusSeconds(TRIGGER_SECONDS));
    }

    @Test
    @DisplayName("자리를 잡는 사이에 마감됐으면 발행하지 않는다")
    void doesNotPublishWhenClosedWhileClaiming() {

        LocalDateTime endAt = LocalDateTime.now().plusSeconds(30);

        when(auctionItemRepository.findClosingSoonView(ITEM_ID))
                .thenReturn(Optional.of(projection(AuctionItemStatus.IN_PROGRESS, endAt, null)))
                .thenReturn(Optional.of(projection(AuctionItemStatus.SOLD, endAt, null)));
        givenClaimFails();

        Optional<LocalDateTime> rescheduleAt = itemClosingSoonService.notifyIfDue(ITEM_ID);

        // 이미 닫힌 물품에 "곧 마감"이 나가면 안 된다.
        verify(domainEventPublisher, never()).publish(any(DomainEvent.class));
        assertThat(rescheduleAt).isEmpty();
    }

    @Test
    @DisplayName("이번에 알리려는 시각을 기준으로 자리를 잡는다")
    void claimsWithCurrentNotifyAt() {

        LocalDateTime endAt = LocalDateTime.now().plusSeconds(30);
        givenItem(AuctionItemStatus.IN_PROGRESS, endAt, TRIGGER_SECONDS);
        givenClaimSucceeds();

        itemClosingSoonService.notifyIfDue(ITEM_ID);

        // 연장으로 알림 시각이 밀리면 이 값도 같이 밀려야 지난번에 알린 시각과 비교가 된다.
        verify(auctionItemRepository).markNotified(eq(ITEM_ID), eq(AuctionItemStatus.IN_PROGRESS),
                eq(endAt), eq(endAt.minusSeconds(TRIGGER_SECONDS)), any());
    }

    @Test
    @DisplayName("아직 알릴 때가 아니면 자리를 잡지 않는다")
    void doesNotClaimWhenNotDueYet() {

        givenItem(AuctionItemStatus.IN_PROGRESS, LocalDateTime.now().plusMinutes(10), TRIGGER_SECONDS);

        itemClosingSoonService.notifyIfDue(ITEM_ID);

        // 여기서 잡아버리면 정작 알릴 때가 됐을 때 자기가 남긴 표시에 막힌다.
        verify(auctionItemRepository, never()).markNotified(anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("알릴 물품이 아니면 자리를 잡지 않는다")
    void doesNotClaimWhenNotNotifiable() {

        givenItem(AuctionItemStatus.SOLD, LocalDateTime.now().plusSeconds(30), TRIGGER_SECONDS);

        itemClosingSoonService.notifyIfDue(ITEM_ID);

        verify(auctionItemRepository, never()).markNotified(anyLong(), any(), any(), any(), any());
    }

    private void givenItem(AuctionItemStatus status, LocalDateTime endAt, Integer triggerSeconds) {
        givenItem(status, endAt, triggerSeconds, null);
    }

    private void givenItem(AuctionItemStatus status, LocalDateTime endAt, Integer triggerSeconds,
                           LocalDateTime notifiedAt) {
        when(auctionItemRepository.findClosingSoonView(ITEM_ID)).thenReturn(Optional.of(
                new ClosingSoonItemProjection(
                        ROOM_ID, ITEM_NAME, status, endAt, triggerSeconds, notifiedAt)));
    }

    /** 트리거가 있는 정상 물품 한 줄. 조회가 두 번 도는 경우에 쓴다. */
    private ClosingSoonItemProjection projection(
            AuctionItemStatus status, LocalDateTime endAt, LocalDateTime notifiedAt) {
        return new ClosingSoonItemProjection(
                ROOM_ID, ITEM_NAME, status, endAt, TRIGGER_SECONDS, notifiedAt);
    }

    /** 자리를 잡는 데 성공한 쪽. 1 행이 바뀌었다는 뜻이다. */
    private void givenClaimSucceeds() {
        when(auctionItemRepository.markNotified(anyLong(), any(), any(), any(), any())).thenReturn(1);
    }

    /** 상태나 마감 시각이 어긋났거나 이미 알려서 0 행이 바뀐 경우. */
    private void givenClaimFails() {
        when(auctionItemRepository.markNotified(anyLong(), any(), any(), any(), any())).thenReturn(0);
    }
}
