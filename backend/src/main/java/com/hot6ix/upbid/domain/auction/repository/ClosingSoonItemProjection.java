package com.hot6ix.upbid.domain.auction.repository;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import java.time.LocalDateTime;

/**
 * 마감 임박 알림을 판정하는 데 필요한 값만 모은 한 줄. 알림 시각이
 * {@code endAt - softCloseTriggerSeconds}라서 물품의 마감 시각과 경매방의 트리거 설정이 함께 필요한데,
 * 엔티티로 읽으면 경매방과 상품이 LAZY라 쿼리가 셋으로 늘어난다.
 */
public record ClosingSoonItemProjection(
        Long auctionRoomId,
        String productName,
        AuctionItemStatus status,
        LocalDateTime endAt,
        Integer softCloseTriggerSeconds
) {
}
