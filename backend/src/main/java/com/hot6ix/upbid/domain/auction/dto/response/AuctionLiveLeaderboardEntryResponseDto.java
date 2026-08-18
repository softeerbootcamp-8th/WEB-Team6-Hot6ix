package com.hot6ix.upbid.domain.auction.dto.response;

/** 회원 ID 대신 방 단위 익명 식별값을 쓰는 Live Snapshot 리더보드 한 줄. */
public record AuctionLiveLeaderboardEntryResponseDto(
        int rank,
        String bidderKey,
        String nickname,
        Long amount,
        boolean isMe
) {
}
