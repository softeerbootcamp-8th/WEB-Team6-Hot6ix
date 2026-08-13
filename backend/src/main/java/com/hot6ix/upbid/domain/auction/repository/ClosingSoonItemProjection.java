package com.hot6ix.upbid.domain.auction.repository;

import com.hot6ix.upbid.domain.auction.entity.AuctionItemStatus;
import java.time.LocalDateTime;

/**
 * 마감 임박 알림을 판정하는 데 필요한 값만 모은 한 줄. 알림 시각이
 * {@code endAt - softCloseTriggerSeconds}라서 물품의 마감 시각과 경매방의 트리거 설정이 함께 필요한데,
 * 엔티티로 읽으면 경매방과 상품이 LAZY라 쿼리가 셋으로 늘어난다.
 *
 * @param status     읽은 시점의 상태. <b>선점 UPDATE 의 조건으로 되돌려 보내</b> 읽고 나서
 *                   마감되지 않았는지 확인한다
 * @param endAt      읽은 시점의 마감 시각. 알림 시각의 근거이자 <b>선점 UPDATE 의 조건</b>이다.
 *                   읽고 나서 연장이나 앞당김이 커밋되면 이 값이 달라져 선점이 실패한다
 * @param notifiedAt 마지막으로 알린 시각. 선점에 실패했을 때 <b>이미 알린 것인지 값이 바뀐
 *                   것인지</b>를 가르는 데 쓴다
 */
public record ClosingSoonItemProjection(
        Long auctionRoomId,
        String productName,
        AuctionItemStatus status,
        LocalDateTime endAt,
        Integer softCloseTriggerSeconds,
        LocalDateTime notifiedAt
) {
}
