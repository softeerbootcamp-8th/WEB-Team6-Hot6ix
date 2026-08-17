package com.hot6ix.upbid.domain.auction.store;

/** Redis 입찰 판정과 실시간 이벤트에 필요한 참여자 표시 스냅샷. */
public record AuctionRedisParticipant(long userId, String nickname) {

    public AuctionRedisParticipant {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("참여자 nickname은 비어 있을 수 없다");
        }
    }
}
