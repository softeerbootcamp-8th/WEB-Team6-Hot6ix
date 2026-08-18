package com.hot6ix.upbid.domain.auction.service;

import com.hot6ix.upbid.domain.auction.repository.AuctionParticipantRepository;
import com.hot6ix.upbid.domain.auction.store.AuctionRedisStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** DB에는 동의했지만 Redis 참여자 정보가 누락된 사용자를 입찰 재판정 전에 복구한다. */
@Service
@RequiredArgsConstructor
public class AuctionParticipantRedisReconciler {

    private final AuctionParticipantRepository auctionParticipantRepository;
    private final AuctionRedisStore auctionRedisStore;

    public boolean restoreIfAgreed(Long itemId, Long userId) {

        var participant = auctionParticipantRepository
                .findAgreedParticipantForItem(itemId, userId)
                .orElse(null);
        if (participant == null) {
            return false;
        }

        auctionRedisStore.addParticipant(participant.getRoomId(), userId, participant.getNickname());
        return true;
    }
}
