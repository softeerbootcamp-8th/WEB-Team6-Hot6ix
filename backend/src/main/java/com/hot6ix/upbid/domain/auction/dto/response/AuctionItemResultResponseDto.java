package com.hot6ix.upbid.domain.auction.dto.response;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;

/**
 * 경매 결과에 실리는 물품 한 줄.
 *
 * <p>낙찰자와 낙찰가는 경매 결과라 모두에게 공개하고, 연락처는 내려가지 않는다 — 거래 상대의
 * 연락처는 낙찰 후보 조회에서만 판매자에게 보인다.
 *
 * @param status      마감된 물품이면 {@code SOLD}/{@code FAILED}다. 방이 닫히기 전 조회하면
 *                    아직 시작 전이거나 진행 중인 물품의 {@code READY}/{@code IN_PROGRESS}도
 *                    그대로 내린다
 * @param finalPrice  낙찰가. 유찰이면 {@code null} — 유찰 물품의 {@code currentPrice}는 시작가와
 *                    같지만 그건 아무도 부르지 않았다는 결과일 뿐이라 가격으로 내리지 않는다
 * @param winnerNickname 낙찰자 닉네임. 유찰이면 {@code null}
 * @param myRank      요청자의 최종 순위. 후보에 없거나 비로그인이면 {@code null}
 * @param myAmount    요청자가 부른 최고가. 후보에 없거나 비로그인이면 {@code null}
 */
public record AuctionItemResultResponseDto(
        Long auctionItemId,
        String productName,
        String imageUrl,
        AuctionItemStatus status,
        Long finalPrice,
        String winnerNickname,
        Integer myRank,
        Long myAmount
) {

    /** 내 순위만 바꾼 복사본. 캐시에서 읽은 공용 값 위에 요청자의 순위를 끼워 넣을 때 쓴다. */
    public AuctionItemResultResponseDto withMyRank(Integer myRank, Long myAmount) {
        return new AuctionItemResultResponseDto(
                auctionItemId, productName, imageUrl, status, finalPrice, winnerNickname, myRank, myAmount);
    }
}
