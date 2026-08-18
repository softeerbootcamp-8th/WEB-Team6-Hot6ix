package com.hot6ix.upbid.domain.auction.service;

import com.hot6ix.upbid.domain.auction.dto.response.AuctionLiveStateResponseDto;
import com.hot6ix.upbid.domain.auction.realtime.BidderKeyEncoder;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisInitializer;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisLiveState;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisStore;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuctionLiveStateService {

    private final AuctionRoomShareService auctionRoomShareService;
    private final AuctionLiveStateItemReader auctionLiveStateItemReader;
    private final AuctionRedisInitializer auctionRedisInitializer;
    private final AuctionRedisStore auctionRedisStore;
    private final BidderKeyEncoder bidderKeyEncoder;
    private final Clock clock;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<AuctionLiveStateResponseDto> getLiveStates(String shareCode, Long viewerUserId) {
        Long roomId = auctionRoomShareService.resolveRoomId(shareCode);

        return auctionLiveStateItemReader
                .findInProgressItemIds(roomId)
                .stream()
                .map(itemId -> readLiveState(roomId, itemId))
                .map(state -> AuctionLiveStateResponseDto.from(
                        state, viewerUserId, bidderKeyEncoder, clock.getZone()))
                .toList();
    }

    private AuctionRedisLiveState readLiveState(Long roomId, Long itemId) {
        auctionRedisInitializer.initialize(itemId);
        AuctionRedisLiveState state = auctionRedisStore.findLiveState(itemId)
                .orElseThrow(() -> new IllegalStateException(
                        "진행 중 물품의 Redis Live State가 없다: itemId=" + itemId));
        if (state.roomId() != roomId) {
            throw new IllegalStateException(
                    "Redis Live State의 경매방이 일치하지 않는다: itemId=" + itemId);
        }
        return state;
    }
}
