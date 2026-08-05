package com.hot6ix.upbid.domain.auction.repository;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;

/**
 * 결과 조회가 물품 테이블에서 읽어오는 행. 응답 DTO를 바로 만들지 않는 이유는 응답에 들어가는
 * 요청자 순위가 {@code deal_candidates}에 있어서다 — 원천이 둘이라 서비스에서 합친다.
 *
 * @param leaderNickname 최고 입찰자 닉네임. 입찰이 없었으면 {@code null}이다.
 *                       <b>낙찰자와 같지 않다</b> — 진행 중인 물품에도 값이 있으므로
 *                       {@code SOLD}인지 확인하고 써야 한다
 * @param currentPrice   마지막으로 불린 가격. 입찰이 없었으면 시작가 그대로다
 */
public record AuctionItemResultProjection(
        Long auctionItemId,
        String productName,
        String imageUrl,
        AuctionItemStatus status,
        Long currentPrice,
        String leaderNickname
) {
}
