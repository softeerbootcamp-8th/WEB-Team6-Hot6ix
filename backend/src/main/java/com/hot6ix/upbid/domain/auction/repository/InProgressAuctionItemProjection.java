package com.hot6ix.upbid.domain.auction.repository;

import java.time.LocalDateTime;

/**
 * 진행 중인 물품 한 줄. 기동 시 마감 예약을 다시 걸 때 쓰며, 그 일에 필요한 두 값만 담는다.
 *
 * <p>엔티티를 읽지 않는 것은 실제 마감이 {@code AuctionItemCloseService}에서 행 락을 걸고
 * 다시 읽기 때문이다. 여기서 상태나 현재가를 들고 있어봐야 마감이 도는 시점에는 이미 낡은
 * 값이라, 아예 안 갖는 편이 낫다.
 */
public record InProgressAuctionItemProjection(
        Long auctionItemId,
        LocalDateTime endAt
) {
}
