package com.hot6ix.upbid.domain.deal.repository;

/**
 * 요청자가 어떤 물품에서 몇 위였는지. 경매방 결과 화면이 물품마다 "내 최종 순위"를 보여주는데,
 * 물품 하나씩 후보를 조회하면 물품 수만큼 쿼리가 나가므로 방 단위로 한 번에 읽는다.
 *
 * @param bidAmount 그 물품에서 요청자가 부른 최고가. 후보는 입찰자별 최고가 한 행이다
 */
public record MyCandidateRankProjection(
        Long auctionItemId,
        Integer candidateRank,
        Long bidAmount
) {
}
