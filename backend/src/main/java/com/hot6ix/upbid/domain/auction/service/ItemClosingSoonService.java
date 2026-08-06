package com.hot6ix.upbid.domain.auction.service;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.ClosingSoonItemProjection;
import com.hot6ix.upbid.global.event.payload.ItemClosingSoon;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마감 임박 알림의 시각을 계산하고 실제로 발행한다.
 *
 * <p>알림 시각은 <b>{@code end_at - soft_close_trigger_seconds}</b>, 곧 Soft Close 연장 구간이
 * 열리는 순간이다. "마감 몇 분 전"을 따로 정하지 않고 트리거에 맞추는 것은, 그래야 알림이
 * "지금부터 입찰하면 마감이 밀린다"는 뜻이 되어 사용자가 알림을 받고 할 수 있는 일이 생기기
 * 때문이다. 그래서 방마다 알림 시점이 다르고, 몇 초 전인지를 이벤트에 실어 화면이 문구를 만든다.
 *
 * <p>{@code AuctionItemCloseService}의 {@code close}/{@code closeIfDue}와 같은 짝을 이룬다.
 * 예약이 알던 값은 깨어난 시점에 이미 낡았을 수 있으므로, 발행 직전에 DB를 다시 읽어 판단한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemClosingSoonService {

    private final AuctionItemRepository auctionItemRepository;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 이 물품의 마감 임박 알림 시각을 계산한다. <b>발행하지 않는다.</b> 물품이 시작되거나 마감이
     * 연장됐을 때 예약을 걸 시각을 정하는 데 쓴다.
     *
     * <p>돌려준 시각이 이미 지났으면 알림 구간 안에 계속 머물러 있다는 뜻이라 다시 알릴 일이
     * 아니다. 그 판단은 부르는 쪽이 한다.
     *
     * @param auctionItemId 물품의 ID
     * @return 알림 시각. 진행 중이 아니거나 연장 설정이 없는 물품이면 빈 값
     */
    public Optional<LocalDateTime> resolveNotifyAt(Long auctionItemId) {

        ClosingSoonItemProjection item = findNotifiable(auctionItemId);

        if (item == null) {
            return Optional.empty();
        }

        return Optional.of(notifyAtOf(item));
    }

    /**
     * 알림 시각이 됐으면 {@code ItemClosingSoon}을 발행하고, <b>아직 안 됐으면 발행하지 않고 그
     * 시각을 돌려준다.</b> {@code ItemClosingSoonScheduler}의 예약이 부른다.
     *
     * <p>예약은 제 시각에 깨지만 그 사이에 <b>Soft Close 연장이 커밋될 수 있다.</b> 그러면 알림
     * 시각도 함께 밀렸으므로 그대로 발행하면 "곧 마감"이라고 해놓고 실제로는 더 남은 상황이 된다.
     * 그래서 다시 읽은 {@code end_at}으로 판단한다.
     *
     * <p>마감됐거나 제외된 물품은 여기서 걸러진다. 예약 취소가 커밋 뒤에 도는 탓에 이미 닫힌
     * 물품의 예약이 살아 있는 순간이 있는데, 그때도 알림이 나가지 않는다.
     *
     * @param auctionItemId 물품의 ID
     * @return 발행했거나 알릴 물품이 아니면 빈 값. <b>아직 알림 시각이 아니면 다시 예약할 시각</b>
     */
    public Optional<LocalDateTime> notifyIfDue(Long auctionItemId) {

        ClosingSoonItemProjection item = findNotifiable(auctionItemId);

        if (item == null) {
            return Optional.empty();
        }

        LocalDateTime notifyAt = notifyAtOf(item);
        LocalDateTime now = LocalDateTime.now();

        if (now.isBefore(notifyAt)) {
            log.info("마감이 연장돼 아직 알릴 때가 아니다: itemId={}, notifyAt={}", auctionItemId, notifyAt);
            return Optional.of(notifyAt);
        }

        domainEventPublisher.publish(ItemClosingSoon.of(item.auctionRoomId(), auctionItemId,
                item.productName(), item.softCloseTriggerSeconds(), now));

        return Optional.empty();
    }

    /**
     * 알림 대상인 물품을 읽는다. 아니면 {@code null}이다. 예약이 아니라 DB가 판단 기준이라,
     * 취소되지 못한 예약이 뒤늦게 실행돼도 여기서 걸린다.
     */
    private ClosingSoonItemProjection findNotifiable(Long auctionItemId) {

        ClosingSoonItemProjection item = auctionItemRepository.findClosingSoonView(auctionItemId)
                .orElse(null);

        if (item == null || item.status() != AuctionItemStatus.IN_PROGRESS) {
            log.info("마감 임박을 알릴 물품이 아니어서 건너뛴다: itemId={}", auctionItemId);
            return null;
        }

        if (item.endAt() == null || item.softCloseTriggerSeconds() == null) {
            log.info("연장 설정이 없어 마감 임박을 알리지 않는다: itemId={}", auctionItemId);
            return null;
        }

        return item;
    }

    /** 연장 구간이 열리는 순간. {@code AuctionItem.extendIfClosingSoon}이 쓰는 경계와 같은 식이다. */
    private LocalDateTime notifyAtOf(ClosingSoonItemProjection item) {
        return item.endAt().minusSeconds(item.softCloseTriggerSeconds());
    }
}
