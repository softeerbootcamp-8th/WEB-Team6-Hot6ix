package com.hot6ix.upbid.domain.deal.repository;

/**
 * 낙찰됐는데 후보가 없는 물품 한 줄. 마감 이벤트가 리스너까지 닿지 못해 후보 생성이 빠진
 * 물품을 다시 만들 때 쓴다.
 *
 * @param auctionRoomId {@code award}가 낙찰 권한 이벤트를 발행할 때 필요하다. 물품에서 방을
 *                      다시 조회하지 않으려고 함께 담는다
 */
public record AwardRecoveryTargetProjection(
        Long auctionItemId,
        Long auctionRoomId
) {
}
