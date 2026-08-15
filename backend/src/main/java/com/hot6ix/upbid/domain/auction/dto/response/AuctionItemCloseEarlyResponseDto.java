package com.hot6ix.upbid.domain.auction.dto.response;

import com.hot6ix.upbid.domain.auction.entity.AuctionItem;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 마감 앞당기기(POST /api/v1/auction-items/{id}/close-early) 응답.
 *
 * <p>이 API가 실제로 바꾼 값만 담는다. 물품 상세를 통째로 돌려주지 않는 것은 진행 중인 물품이라
 * 리더보드를 다시 조회해야 하는데, 화면이 쓰지도 않을 값 때문에 쿼리를 늘릴 이유가 없기 때문이다.
 * 앞당김은 실시간 이벤트로도 나가므로 화면의 다른 값은 그쪽에서 맞춰진다.
 *
 * @param endAt            앞당겨진 뒤의 마감 시각
 * @param remainingSeconds 그 시각까지 남은 초. 요청한 값이며, 요청이 없었으면 경매방의 Soft Close
 *                         트리거 값이다
 */
public record AuctionItemCloseEarlyResponseDto(
        Long auctionItemId,
        LocalDateTime endAt,
        int remainingSeconds
) {
    /**
     * @param now 앞당길 때 쓴 기준 시각. 남은 초를 그 값으로 재야 화면에 알리는 시간과 실제
     *            마감 시각이 어긋나지 않는다
     */
    public static AuctionItemCloseEarlyResponseDto of(AuctionItem auctionItem, LocalDateTime now) {
        return new AuctionItemCloseEarlyResponseDto(
                auctionItem.getAuctionItemId(),
                auctionItem.getEndAt(),
                (int) Duration.between(now, auctionItem.getEndAt()).toSeconds());
    }
}
