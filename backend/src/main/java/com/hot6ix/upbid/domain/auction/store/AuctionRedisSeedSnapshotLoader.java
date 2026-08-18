package com.hot6ix.upbid.domain.auction.store;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import com.hot6ix.upbid.domain.auction.exception.AuctionErrorType;
import com.hot6ix.upbid.domain.auction.repository.AuctionItemRepository;
import com.hot6ix.upbid.domain.auction.repository.AuctionParticipantRepository;
import com.hot6ix.upbid.domain.bid.repository.BidRepository;
import com.hot6ix.upbid.domain.user.entity.User;
import com.hot6ix.upbid.global.exception.ApplicationException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Redis I/O와 분리된 짧은 트랜잭션에서 Redis Seed용 MySQL 스냅샷을 만든다. */
@Service
@RequiredArgsConstructor
public class AuctionRedisSeedSnapshotLoader {

    private final AuctionItemRepository auctionItemRepository;
    private final AuctionParticipantRepository auctionParticipantRepository;
    private final BidRepository bidRepository;

    /** 현재 진행 중인 물품이면 readiness 확인에 필요한 방 ID를 반환한다. */
    @Transactional(readOnly = true)
    public Optional<Long> findInProgressRoomId(long itemId) {

        AuctionItem item = findItem(itemId);
        if (item.getStatus() != AuctionItemStatus.IN_PROGRESS) {
            return Optional.empty();
        }
        return Optional.of(item.getAuctionRoom().getAuctionRoomId());
    }

    /** readiness 확인 뒤에도 진행 중인 물품만 불변 Redis Seed로 변환한다. */
    @Transactional(readOnly = true)
    public Optional<AuctionRedisSeed> loadInProgressSeed(long itemId) {

        AuctionItem item = findItem(itemId);
        if (item.getStatus() != AuctionItemStatus.IN_PROGRESS) {
            return Optional.empty();
        }

        long roomId = item.getAuctionRoom().getAuctionRoomId();
        long sellerUserId = auctionItemRepository.findSellerUserId(itemId)
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND));
        List<AuctionRedisParticipant> participants = auctionParticipantRepository
                .findAgreedParticipants(roomId)
                .stream()
                .map(participant -> new AuctionRedisParticipant(
                        participant.getUserId(), participant.getNickname()))
                .toList();
        List<AuctionRedisLeaderboardEntry> leaderboard = bidRepository
                .findTopBidders(List.of(itemId), 3)
                .stream()
                .map(row -> new AuctionRedisLeaderboardEntry(
                        row.getBidderUserId(), row.getNickname(), row.getAmount()))
                .toList();
        User leader = item.getLeaderUser();

        return Optional.of(new AuctionRedisSeed(
                item.getAuctionItemId(),
                roomId,
                sellerUserId,
                item.getStatus(),
                item.getStartingPrice(),
                item.getCurrentPrice(),
                leader == null ? null : leader.getUserId(),
                item.getBidIncrement(),
                toMillis(item.getEndAt()),
                item.getAuctionRoom().getSoftCloseTriggerSeconds(),
                item.getAuctionRoom().getSoftCloseExtendSeconds(),
                item.getTotalExtensionSeconds(),
                AuctionItem.MAX_TOTAL_EXTENSION_SECONDS,
                item.getProduct().getName(),
                participants,
                leaderboard));
    }

    private AuctionItem findItem(long itemId) {
        return auctionItemRepository.findById(itemId)
                .orElseThrow(() -> new ApplicationException(AuctionErrorType.AUCTION_ITEM_NOT_FOUND));
    }

    private static long toMillis(LocalDateTime value) {
        return value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
