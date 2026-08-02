package com.hot6ix.upbid.domain.auction.service;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.global.event.DomainEvent;
import com.hot6ix.upbid.global.event.payload.ItemEnded;
import com.hot6ix.upbid.global.event.payload.ItemPassed;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionItemCloseService {

    private final AuctionItemRepository auctionItemRepository;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 진행 중인 물품을 마감한다. 입찰이 있으면 낙찰({@code SOLD}), 없으면 유찰({@code FAILED})이며
     * 낙찰은 {@code ItemEnded}를, 유찰은 {@code ItemPassed}를 발행한다. 유찰에 {@code ItemEnded}를
     * 쓸 수 없는 것은 그 payload가 낙찰가와 낙찰자를 필수로 요구하기 때문이다.
     *
     * <p>사용자 요청이 아니라 {@code AuctionCloseScheduler}의 예약이 부른다. 부르는 쪽이 이미
     * 지난 예약을 들고 있을 수 있으므로 <b>물품이 없거나 진행 중이 아니면 조용히 끝낸다</b> —
     * 제외된 물품, 이미 마감된 물품, 같은 예약의 중복 실행이 여기서 함께 걸러진다. 예약이 아니라
     * DB가 판단 기준이다.
     *
     * <p>물품 행에 쓰기 락을 걸고 읽으므로 마감과 입찰이 한 줄로 직렬화된다. 입찰이 먼저 커밋되면
     * 그 입찰까지 반영해 닫히고, 마감이 먼저 커밋되면 뒤이은 입찰이 진행중이 아닌 물품으로 거절된다.
     * 마감 직전 입찰이 조용히 사라지는 구간은 없다.
     *
     * @param auctionItemId 마감할 물품의 ID
     */
    @Transactional
    public void close(Long auctionItemId) {

        AuctionItem auctionItem = auctionItemRepository.findByIdForUpdate(auctionItemId)
                .orElse(null);

        if (auctionItem == null || auctionItem.getStatus() != AuctionItemStatus.IN_PROGRESS) {
            log.info("마감할 물품이 아니어서 건너뛴다: itemId={}", auctionItemId);
            return;
        }

        auctionItem.close();

        domainEventPublisher.publish(toEvent(auctionItem, LocalDateTime.now()));
    }

    /**
     * 리스너가 커밋 후에만 받으므로(DomainEventSseListener) 마감이 롤백되면 이벤트도 나가지 않는다.
     * 낙찰 이벤트에는 D(낙찰 후보 생성)가 걸려 있고, 유찰 이벤트는 화면 알림에만 쓰인다.
     */
    private DomainEvent toEvent(AuctionItem auctionItem, LocalDateTime occurredAt) {

        Long roomId = auctionItem.getAuctionRoom().getAuctionRoomId();
        Long itemId = auctionItem.getAuctionItemId();
        String itemName = auctionItem.getProduct().getName();

        if (auctionItem.getStatus() == AuctionItemStatus.SOLD) {
            return ItemEnded.of(roomId, itemId, itemName, auctionItem.getCurrentPrice(),
                    auctionItem.getLeaderUser().getNickname(), occurredAt);
        }
        return ItemPassed.of(roomId, itemId, itemName, occurredAt);
    }
}
