package com.hot6ix.upbid.domain.auction.dto.response;

import com.hot6ix.upbid.domain.bid.repository.TopBidderProjection;

/**
 * 리더보드 한 줄. 입찰자 한 명이 한 줄이고 금액은 그 사람의 최고가다.
 *
 * <p>입찰 시각은 담지 않는다. 화면이 순위·닉네임·금액만 그린다
 * ({@code leaderboard-rows.tsx}).
 *
 * @param rank 물품 안에서의 순위. 1부터 시작하고 구멍이 없다
 */
public record LeaderboardEntryResponseDto(
        int rank,
        String nickname,
        Long amount
) {
    public static LeaderboardEntryResponseDto from(TopBidderProjection row) {
        return new LeaderboardEntryResponseDto(row.getRankNo(), row.getNickname(), row.getAmount());
    }
}
