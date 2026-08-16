package com.hot6ix.upbid.domain.auction.dto.response;

import com.hot6ix.upbid.domain.bid.repository.TopBidderProjection;

/**
 * 리더보드 한 줄. 입찰자 한 명이 한 줄이고 금액은 그 사람의 최고가다.
 *
 * <p>입찰 시각은 담지 않는다. 화면이 순위·닉네임·금액만 그린다
 * ({@code leaderboard-rows.tsx}).
 *
 * @param rank 물품 안에서의 순위. 1부터 시작하고 구멍이 없다
 * @param isMe 보는 사람 본인의 줄인지. 게스트에게는 항상 {@code false}
 */
public record LeaderboardEntryResponseDto(
        int rank,
        String nickname,
        Long amount,
        boolean isMe
) {
    /**
     * 본인 여부를 <b>서버에서 판정해</b> 담는다.
     *
     * <p>입찰자의 {@code userId}를 그대로 내려보내면 화면이 직접 비교할 수 있지만, 그러면
     * 방을 여는 누구나 다른 회원의 id를 얻는다. 닉네임 비교도 안 된다 — 닉네임에 unique
     * 제약이 없어서(카카오 이름을 그대로 쓴다) 동명이인이면 남의 줄이 본인 것으로 강조된다.
     *
     * @param viewerUserId 조회한 사람. 로그인하지 않았으면 {@code null}
     */
    public static LeaderboardEntryResponseDto from(TopBidderProjection row, Long viewerUserId) {
        return new LeaderboardEntryResponseDto(
                row.getRankNo(),
                row.getNickname(),
                row.getAmount(),
                viewerUserId != null && viewerUserId.equals(row.getBidderUserId()));
    }
}
