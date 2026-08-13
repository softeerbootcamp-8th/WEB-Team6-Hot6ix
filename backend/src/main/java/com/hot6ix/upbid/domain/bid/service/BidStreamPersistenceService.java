package com.hot6ix.upbid.domain.bid.service;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.bid.entity.Bid;
import com.hot6ix.upbid.domain.bid.repository.BidRepository;
import com.hot6ix.upbid.domain.bid.stream.BidStreamEvent;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.domain.user.repository.UserRepository;
import com.hot6ix.upbid.global.event.payload.BidPlaced;
import com.hot6ix.upbid.global.event.payload.ItemCloseAdvanced;
import com.hot6ix.upbid.global.event.payload.ItemEnded;
import com.hot6ix.upbid.global.event.payload.ItemPassed;
import com.hot6ix.upbid.global.event.payload.SoftCloseExtended;
import com.hot6ix.upbid.global.event.publisher.DomainEventPublisher;
import com.hot6ix.upbid.global.exception.ApplicationException;
import com.hot6ix.upbid.global.exception.CommonErrorType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Redis가 승인한 입찰 한 건을 MySQL의 이력과 조회용 상태에 반영한다. */
@Service
@RequiredArgsConstructor
public class BidStreamPersistenceService {

    private final BidRepository bidRepository;
    private final AuctionItemRepository auctionItemRepository;
    private final UserRepository userRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final Clock clock;

    /**
     * Stream 이벤트 한 건을 하나의 DB 트랜잭션으로 반영한다.
     *
     * <p>{@code requestId}가 이미 있으면 이전 처리가 커밋된 것이므로 아무 일도 하지 않는다.
     * 선조회 뒤 동시에 들어오는 중복은 DB UNIQUE 제약이 최종적으로 막는다. 그 충돌은 이
     * 트랜잭션을 롤백시켜 ACK되지 않게 하고, 다음 전달에서 이 선조회가 성공 처리한다.
     *
     * <p>물품 행 잠금은 Redis의 입찰 판정을 대신하지 않는다. 서로 다른 Stream 이벤트의 DB
     * 커밋과 판매자 마감 앞당기기가 같은 행을 동시에 덮지 않도록 비동기 반영 구간만
     * 직렬화한다. API 응답은 Lua 완료 직후 반환되므로 이 잠금을 기다리지 않는다.
     */
    @Transactional
    public void persist(BidStreamEvent event) {

        switch (event) {
            case BidStreamEvent.BidAccepted accepted -> persistAccepted(accepted);
            case BidStreamEvent.ItemClosing closing -> persistClosing(closing);
            case BidStreamEvent.ItemCloseAdvanced advanced -> persistAdvanced(advanced);
        }
    }

    private void persistAccepted(BidStreamEvent.BidAccepted event) {

        if (bidRepository.findByRequestId(event.requestId()).isPresent()) {
            return;
        }

        User bidder = userRepository.findById(event.bidderUserId())
                .orElseThrow(() -> new ApplicationException(CommonErrorType.RESOURCE_NOT_FOUND));
        AuctionItem item = auctionItemRepository.findByIdForUpdate(event.itemId())
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND));

        if (!item.getAuctionRoom().getAuctionRoomId().equals(event.roomId())) {
            throw new IllegalArgumentException("Stream의 roomId가 물품의 경매방과 다르다");
        }

        LocalDateTime acceptedAt = toLocalDateTime(event.acceptedAtMillis());
        LocalDateTime endAt = toLocalDateTime(event.endAtMillis());

        bidRepository.saveAndFlush(Bid.builder()
                .requestId(event.requestId())
                .auctionItem(item)
                .bidder(bidder)
                .amount(event.amount())
                .acceptedAt(acceptedAt)
                .build());

        boolean endAtAdvanced = item.applyPersistedBid(
                bidder, event.amount(), acceptedAt, endAt, event.totalExtensionSeconds());

        domainEventPublisher.publish(BidPlaced.of(
                event.roomId(), event.itemId(), item.getProduct().getName(),
                bidder.getNickname(), event.amount(), acceptedAt));

        if (event.extendedSeconds() > 0 && endAtAdvanced) {
            domainEventPublisher.publish(SoftCloseExtended.of(
                    event.roomId(), event.itemId(), item.getProduct().getName(),
                    event.extendedSeconds(), endAt, acceptedAt));
        }
    }

    private void persistClosing(BidStreamEvent.ItemClosing event) {
        AuctionItem item = lockedItem(event.itemId(), event.roomId());

        if (item.getStatus() != AuctionItemStatus.IN_PROGRESS) {
            return;
        }

        User leader = event.leaderUserId() == null ? null : userRepository.findById(event.leaderUserId())
                .orElseThrow(() -> new ApplicationException(CommonErrorType.RESOURCE_NOT_FOUND));
        LocalDateTime endAt = toLocalDateTime(event.endAtMillis());
        LocalDateTime closedAt = toLocalDateTime(event.closedAtMillis());

        item.closeFromRedis(
                leader, event.currentPrice(), endAt, event.totalExtensionSeconds());

        if (leader == null) {
            domainEventPublisher.publish(ItemPassed.of(
                    event.roomId(), event.itemId(), item.getProduct().getName(), closedAt));
            return;
        }
        domainEventPublisher.publish(ItemEnded.of(
                event.roomId(), event.itemId(), item.getProduct().getName(),
                event.currentPrice(), leader.getNickname(), closedAt));
    }

    private void persistAdvanced(BidStreamEvent.ItemCloseAdvanced event) {
        AuctionItem item = lockedItem(event.itemId(), event.roomId());
        if (item.getStatus() != AuctionItemStatus.IN_PROGRESS) {
            return;
        }
        LocalDateTime endAt = toLocalDateTime(event.endAtMillis());
        LocalDateTime advancedAt = toLocalDateTime(event.advancedAtMillis());

        if (!item.applyCloseAdvanced(endAt, advancedAt)) {
            return;
        }

        domainEventPublisher.publish(ItemCloseAdvanced.of(
                event.roomId(), event.itemId(), item.getProduct().getName(),
                event.remainingSeconds(), endAt, advancedAt));
    }

    private AuctionItem lockedItem(long itemId, long roomId) {
        AuctionItem item = auctionItemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND));
        if (!item.getAuctionRoom().getAuctionRoomId().equals(roomId)) {
            throw new IllegalArgumentException("Stream의 roomId가 물품의 경매방과 다르다");
        }
        return item;
    }

    private LocalDateTime toLocalDateTime(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), clock.getZone());
    }
}
